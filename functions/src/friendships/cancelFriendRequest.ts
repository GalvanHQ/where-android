import { onCall, HttpsError } from "firebase-functions/v2/https";
import * as admin from "firebase-admin";
import { pairId } from "../lib/pairId";
import { friendshipDoc, inboxDoc, outboxDoc, summaryDoc } from "../lib/paths";
import { FriendshipDoc, SocialSummary } from "../lib/types";
import { assertAuth, assertDifferentUids } from "../lib/guards";

if (!admin.apps.length) admin.initializeApp();
const db = admin.firestore();

export const cancelFriendRequest = onCall(async (request) => {
  const callerUid = assertAuth(request);
  const receiverId: string = request.data?.receiverId;
  if (!receiverId || typeof receiverId !== "string") {
    throw new HttpsError("invalid-argument", "receiverId is required");
  }
  assertDifferentUids(callerUid, receiverId);

  const pair = pairId(callerUid, receiverId);

  await db.runTransaction(async (tx) => {
    // ── ALL READS FIRST ──────────────────────────────────────────────
    const friendshipRef = db.doc(friendshipDoc(pair));
    const snap = await tx.get(friendshipRef);

    const receiverSummaryRef = db.doc(summaryDoc(receiverId));
    const receiverSummarySnap = await tx.get(receiverSummaryRef);

    const senderSummaryRef = db.doc(summaryDoc(callerUid));
    const senderSummarySnap = await tx.get(senderSummaryRef);

    // ── VALIDATION ───────────────────────────────────────────────────
    // Idempotent: if doc doesn't exist, return success
    if (!snap.exists) {
      return;
    }

    const data = snap.data() as FriendshipDoc;

    // Reject if not PENDING or caller is not the requester
    if (data.status !== "PENDING" || data.requesterId !== callerUid) {
      throw new HttpsError(
        "failed-precondition",
        "Can only cancel your own pending request"
      );
    }

    const receiverSummary = (receiverSummarySnap.data() as SocialSummary) || {
      friendsCount: 0,
      pendingIncomingCount: 0,
      pendingOutgoingCount: 0,
      blockedCount: 0,
      updatedAt: 0,
    };

    const senderSummary = (senderSummarySnap.data() as SocialSummary) || {
      friendsCount: 0,
      pendingIncomingCount: 0,
      pendingOutgoingCount: 0,
      blockedCount: 0,
      updatedAt: 0,
    };

    // ── ALL WRITES AFTER ─────────────────────────────────────────────
    // Delete friendship doc
    tx.delete(friendshipRef);

    // Remove inbox entry from receiver
    const inboxRef = db.doc(inboxDoc(receiverId));
    tx.set(
      inboxRef,
      { entries: { [callerUid]: admin.firestore.FieldValue.delete() } },
      { merge: true }
    );

    // Remove outbox entry from sender
    const outboxRef = db.doc(outboxDoc(callerUid));
    tx.set(
      outboxRef,
      { entries: { [receiverId]: admin.firestore.FieldValue.delete() } },
      { merge: true }
    );

    // Decrement receiver's pendingIncomingCount (floored at 0)
    tx.set(receiverSummaryRef, {
      ...receiverSummary,
      pendingIncomingCount: Math.max(0, receiverSummary.pendingIncomingCount - 1),
      updatedAt: Date.now(),
    });

    // Decrement sender's pendingOutgoingCount (floored at 0)
    tx.set(senderSummaryRef, {
      ...senderSummary,
      pendingOutgoingCount: Math.max(0, senderSummary.pendingOutgoingCount - 1),
      updatedAt: Date.now(),
    });
  });

  return { status: "cancelled" };
});
