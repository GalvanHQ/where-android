import { onCall, HttpsError } from "firebase-functions/v2/https";
import * as admin from "firebase-admin";
import { pairId } from "../lib/pairId";
import { friendshipDoc, friendsDoc, summaryDoc } from "../lib/paths";
import type { FriendshipDoc, SocialSummary } from "../lib/types";
import { assertAuth, assertDifferentUids } from "../lib/guards";

if (!admin.apps.length) admin.initializeApp();
const db = admin.firestore();

export const removeFriend = onCall(async (request) => {
  const callerUid = assertAuth(request);
  const friendId: string = request.data?.friendId;
  if (!friendId || typeof friendId !== "string") {
    throw new HttpsError("invalid-argument", "friendId is required");
  }
  assertDifferentUids(callerUid, friendId);

  const pair = pairId(callerUid, friendId);

  await db.runTransaction(async (tx) => {
    const friendshipRef = db.doc(friendshipDoc(pair));
    const snap = await tx.get(friendshipRef);

    // Idempotent: if doc doesn't exist or status != ACCEPTED, return success
    if (!snap.exists) {
      return;
    }

    const data = snap.data() as FriendshipDoc;
    if (data.status !== "ACCEPTED") {
      return;
    }

    // Delete friendship doc
    tx.delete(friendshipRef);

    // Delete both FriendEntry docs
    tx.delete(db.doc(friendsDoc(callerUid, friendId)));
    tx.delete(db.doc(friendsDoc(friendId, callerUid)));

    // Decrement both friendsCount (floored at 0)
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
      friendsCount: Math.max(0, callerSummary.friendsCount - 1),
      updatedAt: Date.now(),
    });

    const friendSummaryRef = db.doc(summaryDoc(friendId));
    const friendSummarySnap = await tx.get(friendSummaryRef);
    const friendSummary = (friendSummarySnap.data() as SocialSummary) || {
      friendsCount: 0,
      pendingIncomingCount: 0,
      pendingOutgoingCount: 0,
      blockedCount: 0,
      updatedAt: 0,
    };
    tx.set(friendSummaryRef, {
      ...friendSummary,
      friendsCount: Math.max(0, friendSummary.friendsCount - 1),
      updatedAt: Date.now(),
    });
  });

  return { status: "removed" };
});
