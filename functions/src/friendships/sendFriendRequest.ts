import { onCall, HttpsError } from "firebase-functions/v2/https";
import * as admin from "firebase-admin";
import { pairId, members } from "../lib/pairId";
import { friendshipDoc, inboxDoc, outboxDoc, summaryDoc } from "../lib/paths";
import { FriendshipDoc, RequestEntry, SocialSummary } from "../lib/types";
import { assertAuth, assertDifferentUids, assertNotBlocked } from "../lib/guards";

if (!admin.apps.length) admin.initializeApp();
const db = admin.firestore();

export const sendFriendRequest = onCall(async (request) => {
  const callerUid = assertAuth(request);
  const receiverId: string = request.data?.receiverId;
  if (!receiverId || typeof receiverId !== "string") {
    throw new HttpsError("invalid-argument", "receiverId is required");
  }
  assertDifferentUids(callerUid, receiverId);

  const pair = pairId(callerUid, receiverId);
  const mems = members(callerUid, receiverId);

  const result = await db.runTransaction(async (tx) => {
    // ── ALL READS FIRST ──────────────────────────────────────────────
    
    // Check blocks
    await assertNotBlocked(tx, db, callerUid, receiverId);

    // Rate limit doc
    const ratePath = `users/${callerUid}/summary/rate`;
    const rateSnap = await tx.get(db.doc(ratePath));

    // Existing friendship doc
    const friendshipRef = db.doc(friendshipDoc(pair));
    const existingSnap = await tx.get(friendshipRef);

    // Sender and receiver profiles
    const senderSnap = await tx.get(db.doc(`users/${callerUid}`));
    const receiverSnap = await tx.get(db.doc(`users/${receiverId}`));

    // Both summaries
    const receiverSummaryRef = db.doc(summaryDoc(receiverId));
    const receiverSummarySnap = await tx.get(receiverSummaryRef);

    const senderSummaryRef = db.doc(summaryDoc(callerUid));
    const senderSummarySnap = await tx.get(senderSummaryRef);

    // ── VALIDATION (uses read data) ──────────────────────────────────

    // Rate limit check
    const rateData = rateSnap.data() as { sends?: { ts: number }[] } | undefined;
    const now = Date.now();
    const windowMs = 60 * 60 * 1000; // 1 hour
    const recentSends = (rateData?.sends ?? []).filter(
      (s) => now - s.ts < windowMs
    );
    if (recentSends.length >= 20) {
      throw new HttpsError("resource-exhausted", "Too many friend requests. Try again later.");
    }

    // Check existing friendship
    if (existingSnap.exists) {
      const existing = existingSnap.data() as FriendshipDoc;
      if (
        existing.status === "PENDING" ||
        existing.status === "ACCEPTED" ||
        existing.status === "BLOCKED"
      ) {
        return { status: "already_exists" };
      }
    }

    // ── ALL WRITES AFTER ─────────────────────────────────────────────

    const senderData = senderSnap.data() || {};
    const receiverData = receiverSnap.data() || {};

    // Write friendship doc
    const friendshipData: FriendshipDoc = {
      pairId: pair,
      members: mems,
      requesterId: callerUid,
      status: "PENDING",
      createdAt: now,
      updatedAt: now,
    };
    tx.set(friendshipRef, friendshipData);

    // Write inbox entry for receiver
    const inboxEntry: RequestEntry = {
      uid: callerUid,
      displayName: senderData.displayName || "",
      username: senderData.username || "",
      photoUrl: senderData.photoUrl || null,
      sentAt: now,
      pairId: pair,
    };
    const inboxRef = db.doc(inboxDoc(receiverId));
    tx.set(
      inboxRef,
      { entries: { [callerUid]: inboxEntry } },
      { merge: true }
    );

    // Write outbox entry for sender
    const outboxEntry: RequestEntry = {
      uid: receiverId,
      displayName: receiverData.displayName || "",
      username: receiverData.username || "",
      photoUrl: receiverData.photoUrl || null,
      sentAt: now,
      pairId: pair,
    };
    const outboxRef = db.doc(outboxDoc(callerUid));
    tx.set(
      outboxRef,
      { entries: { [receiverId]: outboxEntry } },
      { merge: true }
    );

    // Update receiver summary
    const receiverSummary = (receiverSummarySnap.data() as SocialSummary) || {
      friendsCount: 0,
      pendingIncomingCount: 0,
      pendingOutgoingCount: 0,
      blockedCount: 0,
      updatedAt: 0,
    };
    tx.set(receiverSummaryRef, {
      ...receiverSummary,
      pendingIncomingCount: receiverSummary.pendingIncomingCount + 1,
      updatedAt: now,
    });

    // Update sender summary
    const senderSummary = (senderSummarySnap.data() as SocialSummary) || {
      friendsCount: 0,
      pendingIncomingCount: 0,
      pendingOutgoingCount: 0,
      blockedCount: 0,
      updatedAt: 0,
    };
    tx.set(senderSummaryRef, {
      ...senderSummary,
      pendingOutgoingCount: senderSummary.pendingOutgoingCount + 1,
      updatedAt: now,
    });

    // Update rate limit counter
    tx.set(db.doc(ratePath), {
      sends: [...recentSends, { ts: now }],
    });

    return { status: "sent" };
  });

  return result;
});
