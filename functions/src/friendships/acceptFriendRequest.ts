import { onCall, HttpsError } from "firebase-functions/v2/https";
import * as admin from "firebase-admin";
import { pairId } from "../lib/pairId";
import {
  friendshipDoc,
  friendsDoc,
  inboxDoc,
  outboxDoc,
  summaryDoc,
} from "../lib/paths";
import { FriendshipDoc, FriendEntry, SocialSummary } from "../lib/types";
import { assertAuth, assertDifferentUids } from "../lib/guards";

if (!admin.apps.length) admin.initializeApp();
const db = admin.firestore();

export const acceptFriendRequest = onCall(async (request) => {
  const callerUid = assertAuth(request);
  const requesterId: string = request.data?.requesterId;
  if (!requesterId || typeof requesterId !== "string") {
    throw new HttpsError("invalid-argument", "requesterId is required");
  }
  assertDifferentUids(callerUid, requesterId);

  const pair = pairId(callerUid, requesterId);
  const now = Date.now();

  await db.runTransaction(async (tx) => {
    const friendshipRef = db.doc(friendshipDoc(pair));
    const snap = await tx.get(friendshipRef);

    // If doc doesn't exist, throw not-found
    if (!snap.exists) {
      throw new HttpsError("not-found", "Friend request not found");
    }

    const data = snap.data() as FriendshipDoc;

    // Reject if not PENDING or if caller is the requester (can't accept own request)
    if (data.status !== "PENDING" || data.requesterId === callerUid) {
      throw new HttpsError(
        "failed-precondition",
        "Can only accept a pending request sent to you"
      );
    }

    // Fetch display fields for both users
    const callerSnap = await tx.get(db.doc(`users/${callerUid}`));
    const requesterSnap = await tx.get(db.doc(`users/${requesterId}`));
    const callerData = callerSnap.data() || {};
    const requesterData = requesterSnap.data() || {};

    // Update friendship doc to ACCEPTED
    tx.update(friendshipRef, {
      status: "ACCEPTED",
      acceptedAt: now,
      updatedAt: now,
    });

    // Write FriendEntry for caller (about the requester)
    const callerFriendEntry: FriendEntry = {
      friendUid: requesterId,
      displayName: requesterData.displayName || "",
      username: requesterData.username || "",
      photoUrl: requesterData.photoUrl || null,
      isOnline: requesterData.isOnline || false,
      since: now,
      pairId: pair,
    };
    tx.set(db.doc(friendsDoc(callerUid, requesterId)), callerFriendEntry);

    // Write FriendEntry for requester (about the caller)
    const requesterFriendEntry: FriendEntry = {
      friendUid: callerUid,
      displayName: callerData.displayName || "",
      username: callerData.username || "",
      photoUrl: callerData.photoUrl || null,
      isOnline: callerData.isOnline || false,
      since: now,
      pairId: pair,
    };
    tx.set(db.doc(friendsDoc(requesterId, callerUid)), requesterFriendEntry);

    // Remove inbox entry from caller
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

    // Update caller's summary: +1 friendsCount, -1 pendingIncomingCount
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
      friendsCount: callerSummary.friendsCount + 1,
      pendingIncomingCount: Math.max(0, callerSummary.pendingIncomingCount - 1),
      updatedAt: now,
    });

    // Update requester's summary: +1 friendsCount, -1 pendingOutgoingCount
    const requesterSummaryRef = db.doc(summaryDoc(requesterId));
    const requesterSummarySnap = await tx.get(requesterSummaryRef);
    const requesterSummary = (requesterSummarySnap.data() as SocialSummary) || {
      friendsCount: 0,
      pendingIncomingCount: 0,
      pendingOutgoingCount: 0,
      blockedCount: 0,
      updatedAt: 0,
    };
    tx.set(requesterSummaryRef, {
      ...requesterSummary,
      friendsCount: requesterSummary.friendsCount + 1,
      pendingOutgoingCount: Math.max(0, requesterSummary.pendingOutgoingCount - 1),
      updatedAt: now,
    });
  });

  return { status: "accepted" };
});
