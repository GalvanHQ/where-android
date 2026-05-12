import { onCall, HttpsError } from "firebase-functions/v2/https";
import * as admin from "firebase-admin";
import { pairId, members } from "../lib/pairId";
import {
  friendshipDoc,
  friendsDoc,
  inboxDoc,
  outboxDoc,
  summaryDoc,
} from "../lib/paths";
import { FriendshipDoc, FriendEntry, RequestEntry } from "../lib/types";

if (!admin.apps.length) admin.initializeApp();
const db = admin.firestore();

/** Page size for legacy doc iteration. */
const PAGE_SIZE = 100;

/** Maximum writes per batch. */
const BATCH_SIZE = 400;

export const backfillFriendships = onCall(async (request) => {
  // Admin-only check
  if (!request.auth?.token?.admin) {
    throw new HttpsError("permission-denied", "Admin access required");
  }

  const cursor: string | undefined = request.data?.cursor;
  let query = db
    .collection("friendships")
    .orderBy(admin.firestore.FieldPath.documentId())
    .limit(PAGE_SIZE);

  if (cursor) {
    query = query.startAfter(cursor);
  }

  const snapshot = await query.get();

  if (snapshot.empty) {
    return { status: "complete", processed: 0, nextCursor: null };
  }

  let processed = 0;
  const writes: Array<{
    ref: FirebaseFirestore.DocumentReference;
    data: Record<string, unknown>;
    isSet: boolean;
  }> = [];

  for (const doc of snapshot.docs) {
    const data = doc.data();
    const docId = doc.id;

    // Skip docs that are already in the new pairId format
    const requesterId: string = data.requesterId || "";
    const receiverId: string = data.receiverId || "";

    if (!requesterId || !receiverId || requesterId === receiverId) {
      continue; // Invalid legacy doc, skip
    }

    const pair = pairId(requesterId, receiverId);
    const mems = members(requesterId, receiverId);

    // Skip if this is already a new-format doc (docId matches pairId)
    if (docId === pair) {
      continue;
    }

    // Check if new pairId doc already exists (idempotent)
    const newDocRef = db.doc(friendshipDoc(pair));
    const existingNew = await newDocRef.get();
    if (existingNew.exists) {
      processed++;
      continue; // Already migrated
    }

    const status: string = data.status || "PENDING";
    const createdAt: number = data.createdAt || Date.now();
    const updatedAt: number = data.updatedAt || Date.now();

    if (status === "ACCEPTED") {
      // Write new friendship doc
      const friendshipData: FriendshipDoc = {
        pairId: pair,
        members: mems,
        requesterId,
        status: "ACCEPTED",
        createdAt,
        updatedAt,
        acceptedAt: updatedAt,
      };
      writes.push({ ref: newDocRef, data: friendshipData as unknown as Record<string, unknown>, isSet: true });

      // Fetch user display fields for FriendEntry mirrors
      const requesterSnap = await db.doc(`users/${requesterId}`).get();
      const receiverSnap = await db.doc(`users/${receiverId}`).get();
      const requesterData = requesterSnap.data() || {};
      const receiverData = receiverSnap.data() || {};

      // Write FriendEntry for requester (about receiver)
      const requesterFriendEntry: FriendEntry = {
        friendUid: receiverId,
        displayName: receiverData.displayName || "",
        username: receiverData.username || "",
        photoUrl: receiverData.photoUrl || null,
        isOnline: receiverData.isOnline || false,
        since: updatedAt,
        pairId: pair,
      };
      writes.push({
        ref: db.doc(friendsDoc(requesterId, receiverId)),
        data: requesterFriendEntry as unknown as Record<string, unknown>,
        isSet: true,
      });

      // Write FriendEntry for receiver (about requester)
      const receiverFriendEntry: FriendEntry = {
        friendUid: requesterId,
        displayName: requesterData.displayName || "",
        username: requesterData.username || "",
        photoUrl: requesterData.photoUrl || null,
        isOnline: requesterData.isOnline || false,
        since: updatedAt,
        pairId: pair,
      };
      writes.push({
        ref: db.doc(friendsDoc(receiverId, requesterId)),
        data: receiverFriendEntry as unknown as Record<string, unknown>,
        isSet: true,
      });
    } else if (status === "PENDING") {
      // Write new friendship doc
      const friendshipData: FriendshipDoc = {
        pairId: pair,
        members: mems,
        requesterId,
        status: "PENDING",
        createdAt,
        updatedAt,
      };
      writes.push({ ref: newDocRef, data: friendshipData as unknown as Record<string, unknown>, isSet: true });

      // Fetch user display fields for RequestEntry
      const requesterSnap = await db.doc(`users/${requesterId}`).get();
      const receiverSnap = await db.doc(`users/${receiverId}`).get();
      const requesterData = requesterSnap.data() || {};
      const receiverData = receiverSnap.data() || {};

      // Write inbox entry for receiver
      const inboxEntry: RequestEntry = {
        uid: requesterId,
        displayName: requesterData.displayName || "",
        username: requesterData.username || "",
        photoUrl: requesterData.photoUrl || null,
        sentAt: createdAt,
        pairId: pair,
      };
      writes.push({
        ref: db.doc(inboxDoc(receiverId)),
        data: { entries: { [requesterId]: inboxEntry } },
        isSet: true,
      });

      // Write outbox entry for requester
      const outboxEntry: RequestEntry = {
        uid: receiverId,
        displayName: receiverData.displayName || "",
        username: receiverData.username || "",
        photoUrl: receiverData.photoUrl || null,
        sentAt: createdAt,
        pairId: pair,
      };
      writes.push({
        ref: db.doc(outboxDoc(requesterId)),
        data: { entries: { [receiverId]: outboxEntry } },
        isSet: true,
      });
    }
    // BLOCKED legacy docs: write the new friendship doc only
    else if (status === "BLOCKED") {
      const friendshipData: FriendshipDoc = {
        pairId: pair,
        members: mems,
        requesterId,
        status: "BLOCKED",
        createdAt,
        updatedAt,
      };
      writes.push({ ref: newDocRef, data: friendshipData as unknown as Record<string, unknown>, isSet: true });
    }

    processed++;
  }

  // Execute writes in batches
  if (writes.length > 0) {
    const chunks: Array<typeof writes> = [];
    for (let i = 0; i < writes.length; i += BATCH_SIZE) {
      chunks.push(writes.slice(i, i + BATCH_SIZE));
    }

    for (const chunk of chunks) {
      const batch = db.batch();
      for (const write of chunk) {
        batch.set(write.ref, write.data, { merge: true });
      }
      await batch.commit();
    }
  }

  // Recompute summaries for all affected users
  await recomputeAffectedSummaries(snapshot.docs);

  const lastDoc = snapshot.docs[snapshot.docs.length - 1];
  const nextCursor = snapshot.docs.length === PAGE_SIZE ? lastDoc.id : null;

  return { status: "in_progress", processed, nextCursor };
});

/**
 * Recompute social summaries for users affected by the migrated docs.
 * Counts from scratch to avoid increment drift.
 */
async function recomputeAffectedSummaries(
  docs: FirebaseFirestore.QueryDocumentSnapshot[]
): Promise<void> {
  const affectedUids = new Set<string>();

  for (const doc of docs) {
    const data = doc.data();
    if (data.requesterId) affectedUids.add(data.requesterId);
    if (data.receiverId) affectedUids.add(data.receiverId);
  }

  const batch = db.batch();
  let batchCount = 0;

  for (const uid of affectedUids) {
    // Count friends
    const friendsSnap = await db.collection(`users/${uid}/friends`).count().get();
    const friendsCount = friendsSnap.data().count;

    // Count inbox entries
    const inboxSnap = await db.doc(`users/${uid}/inbox/friendRequests`).get();
    const inboxData = inboxSnap.data() as { entries?: Record<string, unknown> } | undefined;
    const pendingIncomingCount = inboxData?.entries ? Object.keys(inboxData.entries).length : 0;

    // Count outbox entries
    const outboxSnap = await db.doc(`users/${uid}/outbox/friendRequests`).get();
    const outboxData = outboxSnap.data() as { entries?: Record<string, unknown> } | undefined;
    const pendingOutgoingCount = outboxData?.entries ? Object.keys(outboxData.entries).length : 0;

    // Count blocks
    const blocksSnap = await db.collection(`users/${uid}/blocks`).count().get();
    const blockedCount = blocksSnap.data().count;

    batch.set(db.doc(summaryDoc(uid)), {
      friendsCount,
      pendingIncomingCount,
      pendingOutgoingCount,
      blockedCount,
      updatedAt: Date.now(),
    });

    batchCount++;
    if (batchCount >= BATCH_SIZE) {
      await batch.commit();
      batchCount = 0;
    }
  }

  if (batchCount > 0) {
    await batch.commit();
  }
}
