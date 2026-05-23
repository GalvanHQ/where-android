import { onDocumentWritten } from "firebase-functions/v2/firestore";
import { resolveDisplayName, sendToToken, fetchFcmToken, persistInboxEntry } from "../lib/notify";

/**
 * Fires when a friendship document transitions to ACCEPTED.
 *
 * The original requester is the one who needs the notification ("X accepted
 * your friend request"). The acceptor obviously already knows. The
 * acceptFriendRequest function sets `requesterId` on the doc, so we use that
 * to route the message.
 */
export const onFriendRequestAccepted = onDocumentWritten(
  "friendships/{pairId}",
  async (event) => {
    const before = event.data?.before?.data() as { status?: string } | undefined;
    const after = event.data?.after?.data() as
      | { status?: string; requesterId?: string; members?: string[] }
      | undefined;
    if (!after) return;

    const wasNotAccepted = before?.status !== "ACCEPTED";
    const nowAccepted = after.status === "ACCEPTED";
    if (!(wasNotAccepted && nowAccepted)) return;

    const requesterId = after.requesterId;
    const members = after.members ?? [];
    if (!requesterId || members.length !== 2) return;

    const acceptorUid = members.find((m) => m !== requesterId);
    if (!acceptorUid) return;

    const token = await fetchFcmToken(requesterId);
    const acceptorName = await resolveDisplayName(acceptorUid);
    const payload = {
      type: "friend_accepted",
      title: `${acceptorName} accepted your friend request`,
      body: "You're now friends on Where",
      extra: {
        userId: acceptorUid,
        userName: acceptorName,
      },
    };

    // Always mirror to the inbox first — read state syncs across devices
    // even when the recipient device has a stale FCM token.
    await persistInboxEntry(requesterId, payload);

    if (token) {
      await sendToToken(token, requesterId, payload);
    }
  }
);
