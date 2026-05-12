# WHERE Cloud Functions

Firebase Cloud Functions for the WHERE app's friendship/social graph subsystem.

## Architecture

All friendship mutations are routed through HTTPS callable Cloud Functions rather than direct client-side Firestore writes. This ensures:
- Transactional consistency across 5+ denormalized documents per operation
- Server-side validation (block checks, rate limits, state machine enforcement)
- Strict Firestore security rules (clients can only read, never write friendship data)

## Functions

| Function | Type | Description |
|----------|------|-------------|
| `sendFriendRequest` | Callable | Send a friend request (rate-limited: 20/hour) |
| `cancelFriendRequest` | Callable | Cancel a pending outgoing request |
| `acceptFriendRequest` | Callable | Accept an incoming request → creates friendship |
| `declineFriendRequest` | Callable | Decline an incoming request (silent) |
| `removeFriend` | Callable | Unfriend (removes both sides atomically) |
| `blockUser` | Callable | Block a user (removes all relationship state) |
| `unblockUser` | Callable | Unblock a user |
| `onUserProfileUpdated` | Firestore trigger | Fan-out display field changes to denormalized docs |
| `backfillFriendships` | Callable (admin) | Migrate legacy data to new model |

## Setup

```bash
cd functions
npm install
npm run build
npm run lint
```

## Testing

```bash
# Unit tests
npm test

# Emulator integration tests (requires Firebase Emulator Suite)
npm run test:emulator
```

## Deployment

```bash
firebase deploy --only functions
```

## Profile Fan-Out SLO

The `onUserProfileUpdated` trigger propagates display field changes (displayName, username, photoUrl) to all denormalized friend entries and inbox/outbox mirrors.

**Target SLO:** p95 latency ≤ 5 seconds from source profile write to all fan-out writes committed.

**Retry policy:**
- Firebase Functions v2 automatically retries on transient failures.
- The function is idempotent — reapplying the same diff produces no duplicates or counter changes.
- Chunked batches of ≤ 400 writes run in parallel across chunks.

**Monitoring:**
- Set up a Cloud Monitoring alert on `cloudfunctions.googleapis.com/function/execution_times` for `onUserProfileUpdated` with threshold > 5000ms at p95.
- Dashboard: Firebase Console → Functions → onUserProfileUpdated → Execution time.

**Scaling considerations:**
- A user with 500 friends triggers ~500 writes on profile update. At Firestore's sustained write rate this completes in < 2s.
- For users with > 1000 friends (unlikely in this app's domain), the function may exceed the 5s SLO. Mitigation: split into a task queue pattern if needed in the future.

## Staged Rollout Plan

The migration from the legacy `friendships/{uuid}` model to the new denormalized model follows a 4-phase rollout:

### Phase 0 — Preparation (no user impact)
- Deploy permissive Firestore rules (new paths readable/writable by owner + admin)
- Deploy all Cloud Functions
- Run `backfillFriendships` to populate the new model from legacy data
- Verify parity (see `scripts/run-backfill.md`)

### Phase 1 — Dual-Write (1 week)
- Ship Android client with `useNewFriendshipModel = false` (reads from legacy)
- Client writes go to BOTH legacy collection AND new Cloud Functions
- Monitor error rates in Cloud Functions logs

### Phase 2 — Cutover Reads (staged)
- Flip `useNewFriendshipModel` via Remote Config:
  - **10%** of users → monitor for 48 hours
  - **50%** of users → monitor for 48 hours
  - **100%** of users → monitor for 1 week
- Client now reads from new model, writes exclusively through Cloud Functions
- Legacy collection becomes read-only (no new writes from clients)

### Phase 3 — Cleanup (after 1 week at 100%)
- Deploy strict Firestore rules (deny client writes on `friendships/*`)
- Run `cleanupLegacyFriendships` (dry-run first, then real)
- Remove dual-write code paths from Android client
- Remove the `useNewFriendshipModel` flag default gate (flag stays for emergency rollback)

### Rollback at any phase
- Set `useNewFriendshipModel = false` → clients immediately revert to legacy reads
- New denormalized docs remain but are unused
- After Phase 3 cleanup, rollback requires a Firestore restore from the pre-cleanup export

### Success Criteria
- Zero increase in crash rate during Phase 2
- Firestore read count drops ≥ 60% for the People flow (measured via Firebase Performance)
- No user reports of missing friends or phantom requests
- `friendsCount` parity between old and new model for all users (verified by backfill)
