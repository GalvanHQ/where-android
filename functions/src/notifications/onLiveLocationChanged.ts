import { onDocumentUpdated } from "firebase-functions/v2/firestore";
import * as admin from "firebase-admin";
import { resolveDisplayName, sendToUsers } from "../lib/notify";

if (!admin.apps.length) admin.initializeApp();
const db = admin.firestore();

/**
 * Fires on the consolidated `activeLocations/{uid}` document — the same
 * document the foreground service writes to during a live-sharing session.
 *
 * ⚠️ Cost-sensitive trigger
 * The active-locations doc is written on every GPS tick (every 10–30s
 * during a session). Each write invokes this function. To keep cost
 * proportional to *user-meaningful* events instead of GPS frequency:
 *
 *   1. Use `onDocumentUpdated` (skips the per-frame creates that happen
 *      when a session restarts after a process kill).
 *   2. Bail out at the very top when the active flag didn't transition.
 *      No reads, no Promise allocations, no fan-out — we want this path
 *      to be effectively free for the 99% case.
 *
 * The "active flag" is the conjunction of `isSharingActive === true` and
 * `targetIds.length > 0`. Either condition flipping is a real event:
 *  • Sharing started — false → true
 *  • Sharing stopped — true → false
 *
 * Per-frame location updates are deliberately NOT pushed (the realtime
 * map already shows them, and a notification per GPS tick would be both
 * spammy and battery-hostile).
 *
 * Recipients:
 *  • Direct shares — the friend uid embedded as `direct:{uid}`.
 *  • Group shares — every other group member.
 *  • Multi-target — the union of the above, minus the sharer.
 *
 * The `visibleTo` array is already maintained by the repo (every member
 * who can see the share); we use it as the recipient list directly so we
 * don't have to re-resolve group memberships here.
 */
export const onLiveLocationChanged = onDocumentUpdated(
  "activeLocations/{uid}",
  async (event) => {
    const before = event.data?.before.data() as
      | { targetIds?: string[]; isSharingActive?: boolean; visibleTo?: string[]; targetType?: string }
      | undefined;
    const after = event.data?.after.data() as
      | {
          targetIds?: string[];
          isSharingActive?: boolean;
          visibleTo?: string[];
          targetType?: string;
        }
      | undefined;

    const beforeActive = !!(before?.isSharingActive && (before.targetIds?.length ?? 0) > 0);
    const afterActive = !!(after?.isSharingActive && (after?.targetIds?.length ?? 0) > 0);

    // Hot-path early return — most invocations are mid-session GPS ticks.
    if (beforeActive === afterActive) return;

    const uid = event.params.uid;
    const visibleTo = afterActive ? after?.visibleTo ?? [] : before?.visibleTo ?? [];
    const recipients = visibleTo.filter((u) => u !== uid);
    if (recipients.length === 0) return;

    const userName = await resolveDisplayName(uid);
    const targetType = (after?.targetType ?? before?.targetType ?? "group");

    // Group + direct chats both map to a chat conversation we can deep-link
    // to — figure out the best one to surface in the notification.
    const targetIds = (afterActive ? after?.targetIds : before?.targetIds) ?? [];
    const groupId = targetIds.find((t) => !t.startsWith("direct:"));
    const directFriend = targetIds
      .find((t) => t.startsWith("direct:"))
      ?.replace("direct:", "");
    const conversationId = groupId
      ? await findGroupConversationId(groupId)
      : directFriend
        ? await findDirectConversationId(uid, directFriend)
        : null;

    const extra: Record<string, string> = {
      userId: uid,
      userName,
      targetType,
    };
    if (groupId) extra.groupId = groupId;
    if (conversationId) extra.conversationId = conversationId;

    if (afterActive) {
      await sendToUsers(recipients, {
        type: "live_location_started",
        title: `${userName} started sharing location`,
        body: "Tap to view on the map",
        extra,
      });
    } else {
      await sendToUsers(recipients, {
        type: "live_location_stopped",
        title: `${userName} stopped sharing location`,
        body: "They are no longer sharing",
        extra,
      });
    }
  }
);

/** Looks up the conversation linked to a group, if any. */
async function findGroupConversationId(groupId: string): Promise<string | null> {
  try {
    const snap = await db
      .collection("conversations")
      .where("groupId", "==", groupId)
      .limit(1)
      .get();
    return snap.empty ? null : snap.docs[0].id;
  } catch {
    return null;
  }
}

/** Looks up the direct conversation between two uids, if any. */
async function findDirectConversationId(a: string, b: string): Promise<string | null> {
  try {
    const snap = await db
      .collection("conversations")
      .where("type", "==", "direct")
      .where("participantIds", "array-contains", a)
      .get();
    for (const doc of snap.docs) {
      const ids = (doc.data().participantIds as string[]) ?? [];
      if (ids.length === 2 && ids.includes(b)) return doc.id;
    }
    return null;
  } catch {
    return null;
  }
}
