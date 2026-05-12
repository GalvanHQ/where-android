# Backfill Migration Runbook

## Overview

The `backfillFriendships` Cloud Function migrates legacy `friendships/{uuid}` documents into the new denormalized model:
- `friendships/{pairId}` (canonical pair doc with deterministic ID)
- `users/{uid}/friends/{friendUid}` (denormalized friend entries)
- `users/{uid}/inbox/friendRequests` (aggregation doc)
- `users/{uid}/outbox/friendRequests` (aggregation doc)
- `users/{uid}/summary/social` (counter doc)

## Prerequisites

1. Deploy Cloud Functions: `firebase deploy --only functions`
2. Ensure the admin custom claim is set on the service account or the calling user.
3. Take a Firestore export BEFORE running: `gcloud firestore export gs://YOUR_BUCKET/pre-backfill-$(date +%Y%m%d)`

## Invocation

```bash
# First run (no cursor)
firebase functions:call backfillFriendships --data '{}'

# Subsequent runs (paginated — use the nextCursor from the previous response)
firebase functions:call backfillFriendships --data '{"cursor": "LAST_DOC_ID"}'
```

## Expected Output

```json
{ "status": "in_progress", "processed": 100, "nextCursor": "abc123_def456" }
```

When all docs are processed:
```json
{ "status": "complete", "processed": 42, "nextCursor": null }
```

## Automation (cron-safe)

Run in a loop until `nextCursor` is null:

```bash
CURSOR=""
while true; do
  RESULT=$(firebase functions:call backfillFriendships --data "{\"cursor\": \"$CURSOR\"}" 2>&1)
  echo "$RESULT"
  CURSOR=$(echo "$RESULT" | jq -r '.nextCursor // empty')
  if [ -z "$CURSOR" ]; then
    echo "Backfill complete."
    break
  fi
  sleep 2  # Rate limit between pages
done
```

## Idempotency

The function is fully idempotent:
- If a `friendships/{pairId}` doc already exists for a legacy pair, the legacy doc is skipped.
- Running the function multiple times over the same data produces identical final state.
- Summaries are recomputed from scratch (not incremented), so repeated runs don't inflate counters.

## Parity Verification

After backfill completes, verify parity:

```bash
# Count legacy ACCEPTED friendships
firebase firestore:query friendships --where "status==ACCEPTED" --count

# Count new friends subcollection docs (should be 2x the above)
# Use the Firebase console or a custom script to count users/*/friends/* docs
```

If counts don't match, re-run the backfill — it will pick up any missed docs.

## Gating by User ID Range

For large datasets, you can gate by user ID prefix:
```bash
firebase functions:call backfillFriendships --data '{"cursor": "a"}'
# This starts from docs whose ID is lexicographically after "a"
```

## Rollback

If issues are found after backfill:
1. Set Remote Config `useNewFriendshipModel = false` → clients revert to legacy reads.
2. The new denormalized docs remain but are unused.
3. Do NOT delete the new docs until parity is confirmed and the flag is permanently `true`.

## Cleanup (Phase 3)

After the flag has been `true` for all users for one release cycle:
1. Run `cleanupLegacyFriendships` (dry-run first, then real).
2. Deploy strict Firestore rules (Phase 3 cutover).
3. Remove the `useNewFriendshipModel` flag default gate from the Android client.
