import { onSchedule } from "firebase-functions/v2/scheduler";
import * as admin from "firebase-admin";
import { INBOX_RETENTION_MS } from "../lib/notify";

if (!admin.apps.length) admin.initializeApp();
const db = admin.firestore();

/**
 * Daily Cloud Scheduler job that removes inbox entries older than the
 * 30-day retention window.
 *
 * The single-doc inbox model can't use Firestore's native TTL feature
 * (which deletes whole documents). The FIFO cap in `persistInboxEntry`
 * already prevents unbounded growth, but a 30-day window is a separate
 * UX promise — at low notification volumes a user could still be looking
 * at 6-month-old entries without this job.
 *
 * Scope: scans every `users/*\/inbox/notifications` document. At 10K
 * users that's 10K reads + at most 10K writes per day, which is well
 * under free-tier limits. The scan is paginated to keep individual
 * Function invocations under the 9-minute schedule limit.
 *
 * Cost-vs-correctness tradeoff: we don't write the doc back unless any
 * entries actually got pruned. Idle inboxes stay untouched.
 */
export const scheduledPruneNotifications = onSchedule(
  {
    schedule: "every 24 hours",
    timeZone: "UTC",
  },
  async () => {
    const cutoff = Date.now() - INBOX_RETENTION_MS;
    let totalPruned = 0;
    let totalScanned = 0;

    // Collection-group query so we don't have to enumerate every user
    // doc just to find their inbox. Only matches the single `notifications`
    // doc inside each user's `inbox` subcollection.
    const PAGE_SIZE = 200;
    let lastDoc: FirebaseFirestore.QueryDocumentSnapshot | null = null;
    let done = false;

    while (!done) {
      let q = db
        .collectionGroup("inbox")
        .where(admin.firestore.FieldPath.documentId(), ">=", "notifications")
        .where(admin.firestore.FieldPath.documentId(), "<=", "notifications")
        .orderBy(admin.firestore.FieldPath.documentId())
        .limit(PAGE_SIZE);
      if (lastDoc) q = q.startAfter(lastDoc);

      const snap = await q.get();
      if (snap.empty) {
        done = true;
        break;
      }
      totalScanned += snap.size;

      const writes: Promise<unknown>[] = [];
      for (const doc of snap.docs) {
        const data = doc.data();
        const entries = (data.entries || {}) as Record<string, { timestamp?: number }>;
        const fresh: Record<string, unknown> = {};
        let prunedThisDoc = 0;
        for (const [id, entry] of Object.entries(entries)) {
          if ((entry.timestamp ?? 0) >= cutoff) {
            fresh[id] = entry;
          } else {
            prunedThisDoc++;
          }
        }
        if (prunedThisDoc > 0) {
          totalPruned += prunedThisDoc;
          writes.push(
            doc.ref.set(
              {
                entries: fresh,
                updatedAt: Date.now(),
              },
              { merge: false }
            )
          );
        }
      }
      if (writes.length > 0) await Promise.all(writes);

      if (snap.size < PAGE_SIZE) {
        done = true;
      } else {
        lastDoc = snap.docs[snap.docs.length - 1];
      }
    }

    console.log(
      `[scheduledPruneNotifications] scanned=${totalScanned} pruned=${totalPruned}`
    );
  }
);
