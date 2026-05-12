import { onCall, HttpsError } from "firebase-functions/v2/https";
import * as admin from "firebase-admin";
import { pairId } from "../lib/pairId";
import {
  friendshipDoc,
  friendsDoc,
  inboxDoc,
  outboxDoc,
  summaryDoc,
  blockDoc,
} from "../lib/paths";
import { FriendshipDoc, SocialSummary } from "../lib/types";
import { assertAuth, assertDifferentUids } from "../lib/guards";

if (!admin.apps.length) admin.initializeApp();
const db = admin.firestore();

export const blockUser = onCall(async (request) => {
  const callerUid = assertAuth(request);
  const userId: string = request.data?.userId;
  if (!userId || typeof userId !== "string") {
    throw new HttpsError("invalid-argument", "userId is required");
  }
  assertDifferentUids(callerUid, userId);

  const pair = pairId(callerUid, userId);
  const now = Date.now();

  await db.runTransaction(async (tx) => {
    // Read current friendship state
    const friendshipRef = db.doc(friendshipDoc(pair));
    const friendshipSnap = await tx.get(friendshipRef);
    const existingData = friendshipSnap.exists
      ? (friendshipSnap.data() as FriendshipDoc)
      : null;

    // Read both summaries
    const callerSummaryRef = db.doc(summaryDoc(callerUid));
    const callerSummarySnap = await tx.get(callerSummaryRef);
    const callerSummary: SocialSummary = (callerSummarySnap.data() as SocialSummary) || {
      friendsCount: 0,
      pendingIncomingCount: 0,
      pendingOutgoingCount: 0,
      blockedCount: 0,
      updatedAt: 0,
    };

    const otherSummaryRef = db.doc(summaryDoc(userId));
    const otherSummarySnap = await tx.get(otherSummaryRef);
    const otherSummary: SocialSummary = (otherSummarySnap.data() as SocialSummary) || {
      friendsCount: 0,
      pendingIncomingCount: 0,
      pendingOutgoingCount: 0,
      blockedCount: 0,
      updatedAt: 0,
    };

    // Compute counter adjustments based on previous state
    let callerFriendsDelta = 0;
    let callerPendingInDelta = 0;
    let callerPendingOutDelta = 0;
    let otherFriendsDelta = 0;
    let otherPendingInDelta = 0;
    let otherPendingOutDelta = 0;

    if (existingData) {
      if (existingData.status === "ACCEPTED") {
        callerFriendsDelta = -1;
        otherFriendsDelta = -1;
      } else if (existingData.status === "PENDING") {
        if (existingData.requesterId === callerUid) {
          // Caller sent the request → caller has outgoing, other has incoming
          callerPendingOutDelta = -1;
          otherPendingInDelta = -1;
        } else {
          // Other sent the request → other has outgoing, caller has incoming
          callerPendingInDelta = -1;
          otherPendingOutDelta = -1;
        }
      }
      // If already BLOCKED, no counter changes needed for friends/pending
    }

    // Write block entry
    tx.set(db.doc(blockDoc(callerUid, userId)), {
      blockedUid: userId,
      blockedAt: now,
    });

    // Set friendship doc to BLOCKED with requesterId = caller (blocker)
    tx.set(friendshipRef, {
      pairId: pair,
      members: callerUid < userId ? [callerUid, userId] : [userId, callerUid],
      requesterId: callerUid,
      status: "BLOCKED",
      createdAt: existingData?.createdAt || now,
      updatedAt: now,
    });

    // Delete both FriendEntry docs (if they exist)
    tx.delete(db.doc(friendsDoc(callerUid, userId)));
    tx.delete(db.doc(friendsDoc(userId, callerUid)));

    // Remove inbox/outbox mirrors
    const callerInboxRef = db.doc(inboxDoc(callerUid));
    tx.set(
      callerInboxRef,
      { entries: { [userId]: admin.firestore.FieldValue.delete() } },
      { merge: true }
    );
    const callerOutboxRef = db.doc(outboxDoc(callerUid));
    tx.set(
      callerOutboxRef,
      { entries: { [userId]: admin.firestore.FieldValue.delete() } },
      { merge: true }
    );
    const otherInboxRef = db.doc(inboxDoc(userId));
    tx.set(
      otherInboxRef,
      { entries: { [callerUid]: admin.firestore.FieldValue.delete() } },
      { merge: true }
    );
    const otherOutboxRef = db.doc(outboxDoc(userId));
    tx.set(
      otherOutboxRef,
      { entries: { [callerUid]: admin.firestore.FieldValue.delete() } },
      { merge: true }
    );

    // Update caller summary
    tx.set(callerSummaryRef, {
      friendsCount: Math.max(0, callerSummary.friendsCount + callerFriendsDelta),
      pendingIncomingCount: Math.max(0, callerSummary.pendingIncomingCount + callerPendingInDelta),
      pendingOutgoingCount: Math.max(0, callerSummary.pendingOutgoingCount + callerPendingOutDelta),
      blockedCount: callerSummary.blockedCount + 1,
      updatedAt: now,
    });

    // Update other summary
    tx.set(otherSummaryRef, {
      friendsCount: Math.max(0, otherSummary.friendsCount + otherFriendsDelta),
      pendingIncomingCount: Math.max(0, otherSummary.pendingIncomingCount + otherPendingInDelta),
      pendingOutgoingCount: Math.max(0, otherSummary.pendingOutgoingCount + otherPendingOutDelta),
      blockedCount: otherSummary.blockedCount,
      updatedAt: now,
    });
  });

  return { status: "blocked" };
});
