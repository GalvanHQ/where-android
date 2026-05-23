// Cloud Functions entry point — re-exports all callable handlers.
export { sendFriendRequest } from "./friendships/sendFriendRequest";
export { cancelFriendRequest } from "./friendships/cancelFriendRequest";
export { acceptFriendRequest } from "./friendships/acceptFriendRequest";
export { declineFriendRequest } from "./friendships/declineFriendRequest";
export { removeFriend } from "./friendships/removeFriend";
export { blockUser } from "./friendships/blockUser";
export { unblockUser } from "./friendships/unblockUser";
export { onUserProfileUpdated } from "./profile/onUserProfileUpdated";
export { backfillFriendships } from "./migration/backfillFriendships";
export { cleanupLegacyFriendships } from "./migration/cleanupLegacyFriendships";

// ── Notification triggers ────────────────────────────────────────────────
// All triggers send data-only FCM messages. The Android client dispatches
// them via FcmMessagingService.onMessageReceived → NotificationHelper.
//
// Note: only ONE trigger is registered per Firestore path so we don't
// double-invoke on shared writes (e.g. group membership + meetup live on
// the same document).
export { onFriendRequestSent } from "./notifications/onFriendRequestSent";
export { onFriendRequestAccepted } from "./notifications/onFriendRequestAccepted";
export { onGroupDocumentUpdated } from "./notifications/onGroupDocumentUpdated";
export { onLiveLocationChanged } from "./notifications/onLiveLocationChanged";

// Scheduled pruning of the single-doc inbox to enforce the 30-day
// retention window. The FIFO cap in `persistInboxEntry` keeps doc size
// bounded; this job enforces the time-window separately.
export { scheduledPruneNotifications } from "./notifications/scheduledPruneNotifications";
