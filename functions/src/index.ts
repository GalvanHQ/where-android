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
