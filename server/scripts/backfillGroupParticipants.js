#!/usr/bin/env node

/**
 * One-shot backfill: rewrite every group conversation's `participantIds`
 * to match its source group's current `memberIds`.
 *
 * Why this exists
 * ---------------
 * The Cloud Function `onGroupDocumentUpdated` was extended on 2026-05-24
 * to keep `conversation.participantIds` in sync with `groups/{id}.memberIds`
 * whenever the latter changes. That fix is forward-only — group changes
 * that happened before the deploy are not replayed. As a result,
 * conversations carry historical participants who left or were removed,
 * and the chat server (which authorizes by `participantIds`) still
 * accepts message frames from them.
 *
 * This script walks every `conversations` doc with `type == 'group'`,
 * fetches the corresponding `groups/{groupId}` doc, and rewrites
 * `participantIds = memberIds`. It also strips per-uid map entries
 * (`unreadCounts`, `mutedUntil`, `nicknames`) and array entries
 * (`mutedBy`, `pinnedBy`) for any uid that is no longer a group member,
 * mirroring exactly what the live Cloud Function does on each membership
 * change.
 *
 * Usage
 * -----
 *   cd server
 *   node scripts/backfillGroupParticipants.js [--dry-run]
 *
 *   --dry-run   Print every change without writing anything.
 *
 * Requires `server/serviceAccountKey.json` to be present (already used
 * by the Cloud Run server's local-dev path).
 *
 * Idempotent — safe to run multiple times. If `participantIds` already
 * matches, no write is issued.
 */

const path = require('path');
const admin = require('firebase-admin');

const dryRun = process.argv.includes('--dry-run');

const serviceAccountPath = path.resolve(__dirname, '..', 'serviceAccountKey.json');
const serviceAccount = require(serviceAccountPath);

admin.initializeApp({
    credential: admin.credential.cert(serviceAccount),
});

const db = admin.firestore();

async function backfill() {
    console.log(`Starting backfill (${dryRun ? 'DRY RUN' : 'LIVE'})...`);

    const snapshot = await db
        .collection('conversations')
        .where('type', '==', 'group')
        .get();

    if (snapshot.empty) {
        console.log('No group conversations found.');
        return;
    }

    console.log(`Scanning ${snapshot.size} group conversations.`);

    let processed = 0;
    let updated = 0;
    let skipped = 0;
    let missingGroup = 0;

    for (const convDoc of snapshot.docs) {
        processed++;
        const convId = convDoc.id;
        const convData = convDoc.data();
        const groupId = convData.groupId;

        if (!groupId) {
            console.warn(`[${convId}] no groupId, skipping`);
            skipped++;
            continue;
        }

        const groupSnap = await db.collection('groups').doc(groupId).get();
        if (!groupSnap.exists) {
            console.warn(`[${convId}] group ${groupId} doesn't exist, skipping`);
            missingGroup++;
            continue;
        }

        const groupData = groupSnap.data();
        const memberIds = (groupData.memberIds || []).filter((m) => typeof m === 'string' && m);

        const currentParticipants = (convData.participantIds || []).filter((p) => typeof p === 'string' && p);

        const memberSet = new Set(memberIds);
        const removed = currentParticipants.filter((p) => !memberSet.has(p));
        const added = memberIds.filter((m) => !currentParticipants.includes(m));

        if (removed.length === 0 && added.length === 0) {
            // Already in sync.
            continue;
        }

        const update = {
            participantIds: memberIds,
        };

        // Strip per-uid map entries for everyone who left.
        if (removed.length > 0) {
            for (const uid of removed) {
                update[`unreadCounts.${uid}`] = admin.firestore.FieldValue.delete();
                update[`mutedUntil.${uid}`] = admin.firestore.FieldValue.delete();
                update[`nicknames.${uid}`] = admin.firestore.FieldValue.delete();
            }
            update['mutedBy'] = admin.firestore.FieldValue.arrayRemove(...removed);
            update['pinnedBy'] = admin.firestore.FieldValue.arrayRemove(...removed);
        }

        console.log(
            `[${convId}] groupId=${groupId} ` +
            `before=${currentParticipants.length} ` +
            `after=${memberIds.length} ` +
            `removed=${removed.length} added=${added.length}`
        );

        if (!dryRun) {
            try {
                await convDoc.ref.update(update);
                updated++;
            } catch (err) {
                console.error(`[${convId}] update failed:`, err.message);
            }
        } else {
            updated++;
        }
    }

    console.log('\n──────────────────────────────────────────────');
    console.log(`Processed:     ${processed}`);
    console.log(`Updated:       ${updated}${dryRun ? ' (dry run)' : ''}`);
    console.log(`Skipped:       ${skipped}`);
    console.log(`Missing group: ${missingGroup}`);
    console.log('──────────────────────────────────────────────');
}

backfill()
    .then(() => process.exit(0))
    .catch((err) => {
        console.error('Backfill failed:', err);
        process.exit(1);
    });
