import { onDocumentWritten } from "firebase-functions/v2/firestore";
import { resolveDisplayName, sendToToken, fetchFcmToken, persistInboxEntry } from "../lib/notify";

/**
 * Fires when an entry is added to `users/{uid}/inbox/friendRequests`. The
 * `users/{recipientUid}` is the inbox owner, and the new entry's key is the
 * sender's uid. We notify the *recipient* — they're the one who needs to
 * see "X wants to be your friend".
 *
 * The friendship Cloud Functions write the inbox doc transactionally, so a
 * single send/cancel pair only emits one notification.
 *
 * Why a Firestore trigger and not an inline FCM call from
 * sendFriendRequest? Decoupling lets the friendship-write logic stay focused
 * on the data model and lets push delivery degrade independently — a
 * messaging outage doesn't block the friend request itself.
 */
export const onFriendRequestSent = onDocumentWritten(
  "users/{recipientUid}/inbox/friendRequests",
  async (event) => {
    const recipientUid = event.params.recipientUid;
    const before = event.data?.before?.data() as { entries?: Record<string, unknown> } | undefined;
    const after = event.data?.after?.data() as
      | { entries?: Record<string, { uid: string; sentAt: number; displayName?: string }> }
      | undefined;
    if (!after?.entries) return;

    const beforeKeys = new Set(Object.keys(before?.entries ?? {}));
    const newKeys = Object.keys(after.entries).filter((k) => !beforeKeys.has(k));
    if (newKeys.length === 0) return;

    const token = await fetchFcmToken(recipientUid);

    for (const requesterUid of newKeys) {
      const entry = after.entries[requesterUid];
      const senderName = entry.displayName || (await resolveDisplayName(requesterUid));
      const payload = {
        type: "friend_request",
        title: `${senderName} wants to be your friend`,
        body: "Tap to review the request",
        extra: {
          requesterId: requesterUid,
          requesterName: senderName,
          userId: requesterUid,
        },
      };
      await persistInboxEntry(recipientUid, payload);
      if (token) await sendToToken(token, recipientUid, payload);
    }
  }
);
