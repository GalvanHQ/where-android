import { onCall, HttpsError } from "firebase-functions/v2/https";
import * as admin from "firebase-admin";
import { pairId } from "../lib/pairId";
import { friendshipDoc, blockDoc, summaryDoc } from "../lib/paths";
import { FriendshipDoc, SocialSummary } from "../lib/types";
import { assertAuth, assertDifferentUids } from "../lib/guards";

if (!admin.apps.length) admin.initializeApp();
const db = admin.firestore();

export const unblockUser = onCall(async (request) => {
  const callerUid = assertAuth(request);
  const userId: string = request.data?.userId;
  if (!userId || typeof userId !== "string") {
    throw new HttpsError("invalid-argument", "userId is required");
  }
  assertDifferentUids(callerUid, userId);

  const pair = pairId(callerUid, userId);

  await db.runTransaction(async (tx) => {
    // Read friendship doc
    const friendshipRef = db.doc(friendshipDoc(pair));
    const friendshipSnap = await tx.get(friendshipRef);

    // Delete friendship doc only if status == BLOCKED and requesterId == caller (blocker)
    if (friendshipSnap.exists) {
      const data = friendshipSnap.data() as FriendshipDoc;
      if (data.status === "BLOCKED" && data.requesterId === callerUid) {
        tx.delete(friendshipRef);
      }
    }

    // Delete block entry
    tx.delete(db.doc(blockDoc(callerUid, userId)));

    // Decrement caller's blockedCount (floored at 0)
    const callerSummaryRef = db.doc(summaryDoc(callerUid));
    const callerSummarySnap = await tx.get(callerSummaryRef);
    const callerSummary = (callerSummarySnap.data() as SocialSummary) || {
      friendsCount: 0,
      pendingIncomingCount: 0,
      pendingOutgoingCount: 0,
      blockedCount: 0,
      updatedAt: 0,
    };
    tx.set(callerSummaryRef, {
      ...callerSummary,
      blockedCount: Math.max(0, callerSummary.blockedCount - 1),
      updatedAt: Date.now(),
    });
  });

  return { status: "unblocked" };
});
