import { onCall, HttpsError } from "firebase-functions/v2/https";
import * as admin from "firebase-admin";
import { pairId } from "../lib/pairId";
import { friendshipDoc, inboxDoc, outboxDoc, summaryDoc } from "../lib/paths";
import { FriendshipDoc, SocialSummary } from "../lib/types";
import { assertAuth, assertDifferentUids } from "../lib/guards";

if (!admin.apps.length) admin.initializeApp();
const db = admin.firestore();

export const declineFriendRequest = onCall(async (request) => {
  const callerUid = assertAuth(request);
  const requesterId: string = request.data?.requesterId;
  if (!requesterId || typeof requesterId !== "string") {
    throw new HttpsError("invalid-argument", "requesterId is required");
  }
  assertDifferentUids(callerUid, requesterId);

  const pair = pairId(callerUid, requesterId);

  await db.runTransaction(async (tx) => {
    // ── ALL READS FIRST ──────────────────────────────────────────────
    const friendshipRef = db.doc(friendshipDoc(pair));
    const snap = await tx.get(friendshipRef);

    const callerSummaryRef = db.doc(summaryDoc(callerUid));
    const callerSummarySnap = await tx.get(callerSummaryRef);

    const requesterSummaryRef = db.doc(summaryDoc(requesterId));
    const requesterSummarySnap = await tx.get(requesterSummaryRef);

    // ── VALIDATION ───────────────────────────────────────────────────
    // Idempotent: if doc doesn't exist, return success
    if (!snap.exists) {
      return;
    }

    const data = snap.data() as FriendshipDoc;

    // Reject if not PENDING or if caller is the requester (requester should cancel, not decline)
    if (data.status !== "PENDING" || data.requesterId === callerUid) {
      throw new HttpsError(
        "failed-precondition",
        "Can only decline a pending request sent to you"
      );
    }

    const callerSummary = (callerSummarySnap.data() as SocialSummary) || {
      friendsCount: 0,
      pendingIncomingCount: 0,
      pendingOutgoingCount: 0,
      blockedCount: 0,
      updatedAt: 0,
    };

    const requesterSummary = (requesterSummarySnap.data() as SocialSummary) || {
      friendsCount: 0,
      pendingIncomingCount: 0,
      pendingOutgoingCount: 0,
      blockedCount: 0,
      updatedAt: 0,
    };

    // ── ALL WRITES AFTER ─────────────────────────────────────────────
    // Delete friendship doc
    tx.delete(friendshipRef);

    // Remove inbox entry from caller (the receiver/decliner)
    const callerInboxRef = db.doc(inboxDoc(callerUid));
    tx.set(
      callerInboxRef,
      { entries: { [requesterId]: admin.firestore.FieldValue.delete() } },
      { merge: true }
    );

    // Remove outbox entry from requester
    const requesterOutboxRef = db.doc(outboxDoc(requesterId));
    tx.set(
      requesterOutboxRef,
      { entries: { [callerUid]: admin.firestore.FieldValue.delete() } },
      { merge: true }
    );

    // Decrement caller's pendingIncomingCount (floored at 0)
    tx.set(callerSummaryRef, {
      ...callerSummary,
      pendingIncomingCount: Math.max(0, callerSummary.pendingIncomingCount - 1),
      updatedAt: Date.now(),
    });

    // Decrement requester's pendingOutgoingCount (floored at 0)
    tx.set(requesterSummaryRef, {
      ...requesterSummary,
      pendingOutgoingCount: Math.max(0, requesterSummary.pendingOutgoingCount - 1),
      updatedAt: Date.now(),
    });
  });

  return { status: "declined" };
});
