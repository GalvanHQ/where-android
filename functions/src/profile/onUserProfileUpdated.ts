import { onDocumentUpdated } from "firebase-functions/v2/firestore";
import * as admin from "firebase-admin";

if (!admin.apps.length) admin.initializeApp();
const db = admin.firestore();

/** Maximum writes per batch (Firestore limit is 500; we use 400 for safety). */
const BATCH_SIZE = 400;

export const onUserProfileUpdated = onDocumentUpdated("users/{uid}", async (event) => {
  const uid = event.params.uid;
  const before = event.data?.before?.data();
  const after = event.data?.after?.data();

  if (!before || !after) return;

  // Compare display fields
  const displayNameChanged = before.displayName !== after.displayName;
  const usernameChanged = before.username !== after.username;
  const photoUrlChanged = before.photoUrl !== after.photoUrl;

  if (!displayNameChanged && !usernameChanged && !photoUrlChanged) {
    return; // No relevant changes
  }

  const updatedFields: Record<string, unknown> = {};
  if (displayNameChanged) updatedFields.displayName = after.displayName || "";
  if (usernameChanged) updatedFields.username = after.username || "";
  if (photoUrlChanged) updatedFields.photoUrl = after.photoUrl || null;

  // Collect all write operations needed
  const writes: Array<{ ref: FirebaseFirestore.DocumentReference; data: Record<string, unknown>; merge: boolean }> = [];

  // 1. Update all FriendEntry docs: users/{friend}/friends/{uid}
  const friendsSnapshot = await db
    .collectionGroup("friends")
    .where("friendUid", "==", uid)
    .get();

  for (const doc of friendsSnapshot.docs) {
    writes.push({ ref: doc.ref, data: updatedFields, merge: true });
  }

  // 2. Update inbox entries referencing this uid
  // Inbox entries are keyed by uid in the entries map
  const inboxSnapshot = await db
    .collectionGroup("inbox")
    .where(`entries.${uid}.uid`, "==", uid)
    .get();

  for (const doc of inboxSnapshot.docs) {
    const entryUpdates: Record<string, unknown> = {};
    if (displayNameChanged) entryUpdates[`entries.${uid}.displayName`] = after.displayName || "";
    if (usernameChanged) entryUpdates[`entries.${uid}.username`] = after.username || "";
    if (photoUrlChanged) entryUpdates[`entries.${uid}.photoUrl`] = after.photoUrl || null;
    writes.push({ ref: doc.ref, data: entryUpdates, merge: true });
  }

  // 3. Update outbox entries referencing this uid
  const outboxSnapshot = await db
    .collectionGroup("outbox")
    .where(`entries.${uid}.uid`, "==", uid)
    .get();

  for (const doc of outboxSnapshot.docs) {
    const entryUpdates: Record<string, unknown> = {};
    if (displayNameChanged) entryUpdates[`entries.${uid}.displayName`] = after.displayName || "";
    if (usernameChanged) entryUpdates[`entries.${uid}.username`] = after.username || "";
    if (photoUrlChanged) entryUpdates[`entries.${uid}.photoUrl`] = after.photoUrl || null;
    writes.push({ ref: doc.ref, data: entryUpdates, merge: true });
  }

  if (writes.length === 0) return;

  // Execute in chunked batches of BATCH_SIZE
  const chunks: Array<typeof writes> = [];
  for (let i = 0; i < writes.length; i += BATCH_SIZE) {
    chunks.push(writes.slice(i, i + BATCH_SIZE));
  }

  await Promise.all(
    chunks.map(async (chunk) => {
      const batch = db.batch();
      for (const write of chunk) {
        if (write.merge) {
          batch.set(write.ref, write.data, { merge: true });
        } else {
          batch.update(write.ref, write.data);
        }
      }
      await batch.commit();
    })
  );
});
