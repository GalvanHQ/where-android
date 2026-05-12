import { onCall, HttpsError } from "firebase-functions/v2/https";
import * as admin from "firebase-admin";
import { pairId } from "../lib/pairId";

if (!admin.apps.length) admin.initializeApp();
const db = admin.firestore();

const PAGE_SIZE = 200;
const BATCH_SIZE = 400;

/**
 * Admin-only callable that batch-deletes legacy `friendships/{uuid}` docs
 * that have a verified new-model counterpart at `friendships/{pairId}`.
 *
 * Supports dry-run mode (default) — prints intended deletions without executing.
 * Pass `{ confirm: "yes" }` to actually delete.
 *
 * Paginated via cursor for large datasets.
 */
export const cleanupLegacyFriendships = onCall(async (request) => {
  // Admin-only check
  if (!request.auth?.token?.admin) {
    throw new HttpsError("permission-denied", "Admin access required");
  }

  const confirm: boolean = request.data?.confirm === "yes";
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
    return { status: "complete", deleted: 0, skipped: 0, nextCursor: null, dryRun: !confirm };
  }

  let deleted = 0;
  let skipped = 0;
  const toDelete: FirebaseFirestore.DocumentReference[] = [];

  for (const doc of snapshot.docs) {
    const data = doc.data();
    const docId = doc.id;
    const requesterId: string = data.requesterId || "";
    const receiverId: string = data.receiverId || "";

    // Skip if this is already a new-format doc (docId contains underscore = pairId format)
    if (!requesterId || !receiverId || requesterId === receiverId) {
      skipped++;
      continue;
    }

    const pair = pairId(requesterId, receiverId);

    // If docId IS the pairId, this is a new-format doc — don't delete
    if (docId === pair) {
      skipped++;
      continue;
    }

    // Check if the new pairId doc exists (verified counterpart)
    const newDoc = await db.doc(`friendships/${pair}`).get();
    if (!newDoc.exists) {
      // No new counterpart — skip (parity mismatch, don't delete)
      skipped++;
      continue;
    }

    // Safe to delete — legacy doc has a verified new counterpart
    toDelete.push(doc.ref);
  }

  // Execute deletions (or just report in dry-run mode)
  if (confirm && toDelete.length > 0) {
    const chunks: FirebaseFirestore.DocumentReference[][] = [];
    for (let i = 0; i < toDelete.length; i += BATCH_SIZE) {
      chunks.push(toDelete.slice(i, i + BATCH_SIZE));
    }

    for (const chunk of chunks) {
      const batch = db.batch();
      for (const ref of chunk) {
        batch.delete(ref);
      }
      await batch.commit();
    }
    deleted = toDelete.length;
  } else if (!confirm) {
    deleted = toDelete.length; // Would-be deletions in dry-run
  }

  const lastDoc = snapshot.docs[snapshot.docs.length - 1];
  const nextCursor = snapshot.docs.length === PAGE_SIZE ? lastDoc.id : null;

  return {
    status: nextCursor ? "in_progress" : "complete",
    deleted,
    skipped,
    nextCursor,
    dryRun: !confirm,
  };
});
