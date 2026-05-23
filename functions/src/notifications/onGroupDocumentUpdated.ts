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
