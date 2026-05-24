import { onDocumentUpdated } from "firebase-functions/v2/firestore";
import * as admin from "firebase-admin";
import { resolveDisplayName, sendToUsers } from "../lib/notify";

if (!admin.apps.length) admin.initializeApp();

/**
 * Single trigger that fans out membership-change AND meetup-change pushes
 * for a group document.
 *
 * Why one trigger? Both feature areas live on the same Firestore document
 * (membership = `memberIds` field, meetup = `meetupDestination` map),
 * which means *every* write invokes any trigger registered on that path.
 * Splitting into two functions doubled cold-start cost per group write
 * for no benefit — the diff logic is cheap and the side-effects are
 * disjoint. Consolidating cuts that in half.
 *
 * Notification routing:
 *
 *   memberIds diff →
 *     • new member → MEMBER_JOINED to remaining members (sans actor)
 *     • removed     → MEMBER_LEFT  to remaining members (sans actor)
 *
 *   meetupDestination diff →
 *     • !active → active        → MEETUP_DESTINATION_SET     to non-setter
 *     • active  → !active       → MEETUP_DESTINATION_CLEARED to non-clearer
 *     • participant.status → ARRIVED → MEETUP_MEMBER_ARRIVED to others
 *
 * Performance note: when both fields change in the same write (e.g. a
 * member is removed AND the meetup auto-clears as a side effect), we
 * issue both batches in parallel. Recipients deduplicate automatically
 * via the in-app inbox primary key.
 */
export const onGroupDocumentUpdated = onDocumentUpdated(
  "groups/{groupId}",
  async (event) => {
    const groupId = event.params.groupId;
    const before = event.data?.before.data() as GroupDoc | undefined;
    const after = event.data?.after.data() as GroupDoc | undefined;
    if (!after) return;

    const memberIds = (after.memberIds ?? []) as string[];
    if (memberIds.length === 0) return;

    await Promise.all([
      handleMembershipChange(before, after, groupId, memberIds),
      handleMeetupChange(before, after, groupId, memberIds),
    ]);
  }
);

// ── Membership ─────────────────────────────────────────────────────────

async function handleMembershipChange(
  before: GroupDoc | undefined,
  after: GroupDoc,
  groupId: string,
  memberIds: string[]
) {
  const beforeIds = new Set(before?.memberIds ?? []);
  const afterIds = new Set(memberIds);
  const groupName = after.name || "your group";

  const joined = [...afterIds].filter((id) => !beforeIds.has(id));
  const left = [...beforeIds].filter((id) => !afterIds.has(id));
  if (joined.length === 0 && left.length === 0) return;

  // ── Sync conversation.participantIds with the group's memberIds ────────
  // Without this the conversation doc stays out of sync after a remove /
  // leave: the user keeps appearing in the chat (Chats tab, FCM fan-out
  // recipients) and the WS server's auth check at connect time still
  // accepts them — so a removed member can keep sending messages until
  // they next reconnect. Authoritative server-side write through Admin
  // SDK so clients can't drift this.
  if (joined.length > 0 || left.length > 0) {
    await syncConversationParticipants(groupId, memberIds, joined, left);
  }

  for (const newMemberId of joined) {
    const recipients = memberIds.filter((id) => id !== newMemberId);
    if (recipients.length === 0) continue;
    const userName = await resolveDisplayName(newMemberId);
    await sendToUsers(recipients, {
      type: "member_joined",
      title: `${userName} joined ${groupName}`,
      body: "Tap to view the group",
      extra: { groupId, userId: newMemberId, userName },
    });
  }

  for (const oldMemberId of left) {
    const recipients = memberIds; // already excludes the leaver
    if (recipients.length === 0) continue;
    const userName = await resolveDisplayName(oldMemberId);
    await sendToUsers(recipients, {
      type: "member_left",
      title: `${userName} left ${groupName}`,
      body: "Tap to view the group",
      extra: { groupId, userId: oldMemberId, userName },
    });
  }
}

/**
 * Mirror the group's `memberIds` into every group-typed conversation
 * that references this group. We do it as a single set() on
 * `participantIds` rather than two arrayUnion / arrayRemove updates so
 * the doc converges to exactly the group's current membership without
 * race windows.
 *
 * Also strips the leavers from `unreadCounts`, `mutedBy`, `mutedUntil`,
 * `pinnedBy`, and `nicknames` keyed on the leaver's uid — the
 * conversation no longer concerns them, and leaving stale entries
 * costs storage + can leak names into header counters.
 *
 * Idempotent: if no conversation is found (shouldn't happen for a real
 * group), we no-op silently.
 */
async function syncConversationParticipants(
  groupId: string,
  memberIds: string[],
  _joined: string[],
  left: string[]
) {
  const db = admin.firestore();
  try {
    const snapshot = await db
      .collection("conversations")
      .where("type", "==", "group")
      .where("groupId", "==", groupId)
      .get();
    if (snapshot.empty) return;

    const batch = db.batch();
    for (const doc of snapshot.docs) {
      const update: Record<string, unknown> = {
        participantIds: memberIds,
      };
      if (left.length > 0) {
        for (const uid of left) {
          update[`unreadCounts.${uid}`] = admin.firestore.FieldValue.delete();
          update[`mutedUntil.${uid}`] = admin.firestore.FieldValue.delete();
          update[`nicknames.${uid}`] = admin.firestore.FieldValue.delete();
        }
        update["mutedBy"] = admin.firestore.FieldValue.arrayRemove(...left);
        update["pinnedBy"] = admin.firestore.FieldValue.arrayRemove(...left);
      }
      batch.update(doc.ref, update);
    }
    await batch.commit();
  } catch (err) {
    console.error("Failed to sync conversation.participantIds for group", groupId, err);
  }
}

// ── Meetup ─────────────────────────────────────────────────────────────

async function handleMeetupChange(
  before: GroupDoc | undefined,
  after: GroupDoc,
  groupId: string,
  memberIds: string[]
) {
  const beforeDest = before?.meetupDestination;
  const afterDest = after.meetupDestination;

  // Early-exit: no meetup field on either side → nothing to do.
  if (!beforeDest && !afterDest) return;

  // ── Destination became active ────────────────────────────────────────
  if (!beforeDest?.isActive && afterDest?.isActive) {
    const setterUid = afterDest.setBy;
    if (!setterUid) return;
    const recipients = memberIds.filter((m) => m !== setterUid);
    if (recipients.length === 0) return;
    const setterName = await resolveDisplayName(setterUid);
    await sendToUsers(recipients, {
      type: "meetup_destination_set",
      title: `Meetup set: ${afterDest.name || "destination"}`,
      body: `${setterName} set the meetup point`,
      extra: {
        groupId,
        userId: setterUid,
        userName: setterName,
        destinationName: afterDest.name || "",
      },
    });
    return;
  }

  // ── Destination cleared ──────────────────────────────────────────────
  if (beforeDest?.isActive && !afterDest?.isActive) {
    const clearerUid = beforeDest.setBy ?? "";
    const recipients = clearerUid
      ? memberIds.filter((m) => m !== clearerUid)
      : memberIds;
    if (recipients.length === 0) return;
    await sendToUsers(recipients, {
      type: "meetup_destination_cleared",
      title: "Meetup destination cleared",
      body: "Tap to view the group",
      extra: {
        groupId,
        destinationName: beforeDest.name || "",
      },
    });
    return;
  }

  // ── Member arrival ───────────────────────────────────────────────────
  if (afterDest?.isActive && beforeDest?.isActive) {
    const beforeParticipants = beforeDest.participants ?? {};
    const afterParticipants = afterDest.participants ?? {};

    for (const [uid, participant] of Object.entries(afterParticipants)) {
      const wasArrived = beforeParticipants[uid]?.status === "ARRIVED";
      const isArrived = participant.status === "ARRIVED";
      if (!wasArrived && isArrived) {
        const recipients = memberIds.filter((m) => m !== uid);
        if (recipients.length === 0) continue;
        const userName = await resolveDisplayName(uid);
        await sendToUsers(recipients, {
          type: "meetup_member_arrived",
          title: `${userName} arrived at the meetup`,
          body: "They reached the meetup point",
          extra: {
            groupId,
            userId: uid,
            userName,
            destinationName: afterDest.name || "",
          },
        });
      }
    }
  }
}

interface GroupDoc {
  memberIds?: string[];
  name?: string;
  meetupDestination?: {
    isActive?: boolean;
    name?: string;
    setBy?: string;
    setAt?: number;
    participants?: Record<string, { status?: string; updatedAt?: number; note?: string }>;
  };
}
