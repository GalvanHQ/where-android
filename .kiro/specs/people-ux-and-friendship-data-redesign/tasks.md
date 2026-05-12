# Implementation Plan

This plan executes the **People UX & Friendship Data Redesign** end-to-end, in the dependency order called out in the spec: domain → data → DI → rules (Phase 0) → Cloud Functions → repository rewiring → ViewModels → shared UI → per-screen UX → migration → rules (Phase 3 cutover) → cleanup & verification.

Conventions
- Leaf tasks are scoped so a single `spec-task-execution` pass can complete them (≈ 1–3 files touched).
- Each task ends with `_Requirements:` and (where applicable) `_Design:` references.
- Tasks tagged **(PBT)** are property-based tests run via `io.kotest:kotest-property` or, for rules/emulator, via `@firebase/rules-unit-testing` and the Firebase Emulator. Their status is tracked via the PBT status tool.
- Verification parent tasks (build/lint/tests) gate progression between phases.

Source roots assumed
- Android module: `app/` (Kotlin, Compose, Hilt).
- Cloud Functions module: `functions/` (TypeScript, Firebase Functions v2). Created in task 3.1 — does **not** currently exist; the existing `server/` is an unrelated Express service and is left untouched.

---

## Phase 1 — Domain, Data, and DI Foundations

- [x] 1. Domain models and helpers for the new friendship schema
  - [x] 1.1 Create `FriendshipIds` helper
    - Add `app/src/main/java/com/ovi/where/domain/model/FriendshipIds.kt` with `pairId(a: String, b: String): String` and `members(a: String, b: String): List<String>`.
    - Require `a != b`; reject equal uids with `require`.
    - _Requirements: 1.1, 1.2, 1.3_
    - _Design: §4.1_
  - [x] 1.2 Evolve `Friendship` model
    - Update `app/src/main/java/com/ovi/where/domain/model/Friendship.kt`:
      - Replace `id: String` with `pairId: String`, add `members: List<String>`, add `acceptedAt: Long?`, keep `requesterId`, `status`, `createdAt`, `updatedAt`.
      - Add `NONE` to `FriendshipStatus` enum (still serialize only `PENDING | ACCEPTED | BLOCKED` to Firestore — `NONE` represents absence).
      - Document invariants (`members.size == 2 && members == members.sorted() && pairId == "${members[0]}_${members[1]}" && requesterId in members`).
    - _Requirements: 1.4, 11.1_
    - _Design: §4.1_
  - [x] 1.3 Add `FriendEntry` model
    - New `app/src/main/java/com/ovi/where/domain/model/FriendEntry.kt` with fields from design §4.2 (`friendUid`, `displayName`, `username`, `photoUrl?`, `isOnline`, `since`, `pairId`).
    - No-arg defaults for Firestore deserialization.
    - _Requirements: 8.2, 8.5_
    - _Design: §4.2_
  - [x] 1.4 Add `RequestEntry` and `RequestInbox` models
    - New `app/src/main/java/com/ovi/where/domain/model/RequestEntry.kt`.
    - New `app/src/main/java/com/ovi/where/domain/model/RequestInbox.kt` with `entries: Map<String, RequestEntry> = emptyMap()`.
    - _Requirements: 9.2, 9.3, 9.6_
    - _Design: §4.3_
  - [x] 1.5 Add `SocialSummary` model
    - New `app/src/main/java/com/ovi/where/domain/model/SocialSummary.kt` with `friendsCount`, `pendingIncomingCount`, `pendingOutgoingCount`, `blockedCount`, `updatedAt`.
    - _Requirements: 10.1, 10.2, 10.3, 10.4_
    - _Design: §4.4_
  - [x] 1.6 Add `BlockEntry` model
    - New `app/src/main/java/com/ovi/where/domain/model/BlockEntry.kt` with `blockedUid`, `blockedAt`, `reason?`.
    - _Requirements: 7.1, 7.3, 7.6_
    - _Design: §4.5_
  - [x] 1.7 **(PBT)** Kotest property tests for `FriendshipIds`
    - New `app/src/test/java/com/ovi/where/domain/model/FriendshipIdsPropertyTest.kt`.
    - Property: `∀ a, b ∈ Arb.stringPattern("[a-zA-Z0-9]{1,28}"), a != b ⟹ pairId(a, b) == pairId(b, a)`.
    - Property: `∀ a, b, members(a, b) == members(a, b).sorted() && size == 2`.
    - Property: `∀ a, b, pairId(a, b) == "${members(a, b)[0]}_${members(a, b)[1]}"`.
    - Negative: `pairId(a, a)` throws `IllegalArgumentException`.
    - Use `iterations = 200`, tag `Feature: people-ux-and-friendship-data-redesign, Property 1: FriendshipIds are symmetric and sorted`.
    - _Requirements: 1.2, 1.3, 1.5_
    - _Design: §8 Property 1, §9_

- [x] 2. `FriendshipRepository` interface overhaul
  - [x] 2.1 Replace `FriendshipRepository` interface
    - Edit `app/src/main/java/com/ovi/where/domain/repository/FriendshipRepository.kt` to match design §5.1 signatures exactly (new `cancelFriendRequest`, `blockUser`, `unblockUser`, `observeIncomingRequests`, `observeOutgoingRequests`, `observeSocialSummary`, `observeBlockedUsers`; drop the `friendshipId` variants of accept/decline; keep `observeAllFriendLocations`).
    - _Requirements: 2.1, 3.1, 4.1, 5.1, 6.1, 7.1, 7.3, 8.1, 9.1, 9.3, 10.1_
    - _Design: §5.1_
  - [x] 2.2 Retire legacy callers of the old interface
    - Compile-fix every use site (`FriendUseCases.kt`, `FriendshipRepositoryImpl.kt`, `SearchUsersViewModel`, etc.) so the module compiles against the new signatures — stub new methods with `TODO()` in the impl at this point.
    - _Requirements: 23.2_
    - _Design: §5.1_

- [x] 3. Hilt dependency injection for `FirebaseFunctions`
  - [x] 3.1 Add `FirebaseFunctions` provider
    - Edit `app/src/main/java/com/ovi/where/di/AppModule.kt` (or add `FirebaseModule.kt`): `@Provides @Singleton fun provideFirebaseFunctions(): FirebaseFunctions = Firebase.functions`.
    - Parametrize region via a constant if the deployed region is non-default; default to `us-central1`.
    - Add `com.google.firebase:firebase-functions-ktx` to `app/build.gradle.kts` / `gradle/libs.versions.toml`.
    - _Requirements: 23.1_
    - _Design: §14_
  - [x] 3.2 Inject `FirebaseFunctions` into `FriendshipRepositoryImpl`
    - Update constructor; register the binding in `RepositoryModule.kt` if not already auto-wired.
    - _Requirements: 23.2_
    - _Design: §5.2_
  - [x] 3.3 Expose Hilt test module override scaffolding
    - Add `app/src/test/java/com/ovi/where/di/TestFirebaseModule.kt` demonstrating a `@TestInstallIn` replacement for `FirebaseFunctions` returning a fake.
    - _Requirements: 23.3_
    - _Design: §9_

- [x] 4. `FriendshipRepositoryImpl` — listener wiring (reads only)
  - [x] 4.1 Implement `observeFriends`
    - `users/{uid}/friends` ordered by `displayName`; emit empty list when unauthenticated; close on error.
    - _Requirements: 8.1, 8.2, 8.3, 8.5_
    - _Design: §5.2_
  - [x] 4.2 Implement `observeIncomingRequests`
    - Single-doc listener on `users/{uid}/inbox/friendRequests`; emit `entries.values.sortedByDescending { sentAt }`; empty list when doc missing.
    - _Requirements: 9.1, 9.2, 9.4_
    - _Design: §5.2_
  - [x] 4.3 Implement `observeOutgoingRequests`
    - Single-doc listener on `users/{uid}/outbox/friendRequests`.
    - _Requirements: 9.3, 9.4_
    - _Design: §5.2_
  - [x] 4.4 Implement `observeSocialSummary`
    - Single-doc listener on `users/{uid}/summary/social`; emit zero-valued `SocialSummary()` when absent; no negative values.
    - _Requirements: 10.1, 10.2, 10.3_
    - _Design: §5.2_
  - [x] 4.5 Implement `observeBlockedUsers`
    - Subcollection listener on `users/{uid}/blocks`.
    - _Requirements: 7.6_
    - _Design: §4.5_
  - [x] 4.6 Implement `getFriendshipStatus(otherUserId)`
    - Single deterministic `get` on `friendships/{pairId}`.
    - _Requirements: 1.6_
    - _Design: §5.2_
  - [x] 4.7 Spill handling for inbox/outbox (`friendRequests_2`)
    - Extend `observeIncomingRequests` / `observeOutgoingRequests` to merge `friendRequests` + `friendRequests_2` when the secondary doc exists.
    - _Requirements: 9.5_
    - _Design: §4.3_

- [x] 5. `FriendshipRepositoryImpl` — callable write wrappers
  - [x] 5.1 `sendFriendRequest(receiverId)` → callable "sendFriendRequest"
  - [x] 5.2 `cancelFriendRequest(receiverId)` → callable "cancelFriendRequest"
  - [x] 5.3 `acceptFriendRequest(requesterId)` → callable "acceptFriendRequest"
  - [x] 5.4 `declineFriendRequest(requesterId)` → callable "declineFriendRequest"
  - [x] 5.5 `removeFriend(friendId)` → callable "removeFriend"
  - [x] 5.6 `blockUser(userId)` → callable "blockUser"
  - [x] 5.7 `unblockUser(userId)` → callable "unblockUser"
    - Each wrapper catches `FirebaseFunctionsException`, maps codes (`UNAVAILABLE`, `DEADLINE_EXCEEDED`, `PERMISSION_DENIED`, `FAILED_PRECONDITION`, `NOT_FOUND`, `INVALID_ARGUMENT`, `RESOURCE_EXHAUSTED`) to `Resource.Error` with a stable message key; zero client-side Firestore reads.
    - _Requirements: 2.1, 2.7, 3.1, 4.1, 5.1, 6.1, 7.1, 7.3, 18.5, 21.2, 21.3_
    - _Design: §5.2, §7_

- [ ] 6. Repository fakes and unit tests
  - [x] 6.1 `FakeFirebaseFirestore`/`FakeFriendshipRepository` for ViewModel + repo tests
    - New `app/src/test/java/com/ovi/where/data/repository/fake/FakeFriendshipRepository.kt`.
    - Supports scripted flows for friends / inbox / outbox / summary / blocks and recording callable invocations.
    - _Requirements: 8.1, 9.1, 10.1_
  - [ ] 6.2 Unit tests for `FriendshipRepositoryImpl` listener behaviour
    - Assert exactly one listener per observe; assert unauthenticated state emits empty list without opening listener; assert ordering.
    - _Requirements: 8.1, 8.3, 9.1, 9.2, 9.3, 10.1_
  - [ ] 6.3 Unit tests for callable wrappers
    - Assert each wrapper calls the correct HTTPS callable name with the expected payload shape; assert error-code mapping.
    - _Requirements: 2.1, 3.1, 4.1, 5.1, 6.1, 7.1, 7.3, 2.7_
  - [ ] 6.4 **(PBT)** Read-count budget property (client side)
    - Property: `∀ F ≥ 0. opening PeopleViewModel incurs ≤ F+1 reads; FriendRequestsViewModel ≤ 2; UserProfileViewModel ≤ 2; SearchUsersViewModel per debounced query ≤ 40 + 0 friendship reads; every Friendship_Callable invocation ≤ 0 reads`.
    - Uses an instrumented `FakeFirebaseFirestore` that counts `.get()` and initial snapshot-listener deliveries.
    - Tag: `Feature: people-ux-and-friendship-data-redesign, Property 10: People-flow read budget upper bounds`.
    - _Requirements: 1.6, 18.1, 18.2, 18.3, 18.4, 18.5_
    - _Design: §8 Property 10, §15_

- [ ] 7. Phase-1 verification
  - [ ] 7.1 `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest -i`
    - Fix any compile/test failures before proceeding.
    - _Requirements: 1.*–10.*, 23.*_

---

## Phase 2 — Firestore Rules (Phase 0 permissive fallback)

- [ ] 8. Deploy-safe permissive rules update
  - [x] 8.1 Add new-path rules to `firestore.rules` — permissive mode
    - Edit top-level `firestore.rules`: add rule blocks for `users/{uid}/friends/{friendUid}`, `users/{uid}/inbox/{docId}`, `users/{uid}/outbox/{docId}`, `users/{uid}/summary/{docId}`, `users/{uid}/blocks/{blockedUid}`.
    - For `friends`, `inbox`, `outbox`, `summary`: `allow read: if request.auth.uid == uid; allow write: if request.auth.uid == uid || isAdmin();` (admin + owner during Phase 0–2 so the client can still write during dual-write; strict cutover in Phase 8).
    - For `blocks`: `allow read, write: if request.auth.uid == uid;`.
    - Leave the existing `friendships/{friendshipId}` block in place (legacy model still live).
    - Add `isAdmin()` helper: `return request.auth.token.admin == true;`.
    - _Requirements: 12.1, 12.2, 12.3, 12.4, 12.7, 22.3_
    - _Design: §12.2, §13 Phase 0_
  - [ ] 8.2 Add `firestore.indexes.json` entries
    - Composite index on `users/{uid}/friends` ordered by `displayName` asc (single-field auto, but verify collection-group patterns if introduced later).
    - _Requirements: 8.1_
  - [ ] 8.3 `firebase deploy --only firestore:rules` smoke test (manual, document in commit message)
    - Dry run against emulator first; verify existing screens still load.
    - _Requirements: 12.7, 22.1_

---

## Phase 3 — Cloud Functions (callable mutations + triggers)

- [x] 9. Scaffold `functions/` module
  - [x] 9.1 Create `functions/` package
    - New `functions/package.json` (TypeScript, `firebase-functions@^5`, `firebase-admin@^12`), `tsconfig.json`, `.eslintrc.js`, `.gitignore` (`lib/`, `node_modules/`).
    - `npm scripts`: `build`, `lint`, `test` (using `jest` + `ts-jest`), `serve` (`firebase emulators:start --only functions,firestore`).
    - Add `engines.node: "20"`.
    - _Requirements: 23.1_
    - _Design: §14_
  - [x] 9.2 Register functions with top-level Firebase
    - Update `firebase.json`: add `functions: { source: "functions", predeploy: ["npm --prefix \"$RESOURCE_DIR\" run lint", "npm --prefix \"$RESOURCE_DIR\" run build"] }`.
    - _Requirements: 23.1_
  - [x] 9.3 Shared `functions/src/lib/pairId.ts`
    - Mirror of `FriendshipIds.pairId` / `members` so client and server compute identical ids.
    - _Requirements: 1.1, 1.2, 1.3_
    - _Design: §4.1_
  - [x] 9.4 Shared `functions/src/lib/paths.ts` + `types.ts`
    - Document-path builders (`friendshipDoc(pairId)`, `friendsDoc(uid, friendUid)`, `inboxDoc(uid)`, `outboxDoc(uid)`, `summaryDoc(uid)`, `blockDoc(uid, blockedUid)`) and TS shapes matching Kotlin models.
    - _Requirements: 1.1, 8.2, 9.6, 10.4_
    - _Design: §4.*_
  - [x] 9.5 Shared `functions/src/lib/guards.ts`
    - `assertAuth(context)`, `assertDifferentUids(a,b)`, `assertNotBlocked(tx, a, b)`, `rateLimit(tx, uid, bucket, limit, windowMs)`.
    - _Requirements: 2.3, 2.4, 2.6, 7.5_
    - _Design: §11_

- [ ] 10. `sendFriendRequest` callable
  - [x] 10.1 Implement `functions/src/friendships/sendFriendRequest.ts`
    - Exact transaction per design §5.2 and Requirement 2.2.
    - Reject `receiverId == callerUid` with `invalid-argument`.
    - Reject if either `blocks/*` exists with `permission-denied`.
    - Reject if > 20 successful sends in last rolling hour with `resource-exhausted` (counter at `users/{uid}/summary/rate`).
    - Idempotent when the pair doc exists with any non-`NONE` status (return `{ status: "already" }`).
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6_
    - _Design: §5.2_
  - [ ] 10.2 Jest + firebase-functions-test unit tests
    - Example-based tests for each reject path; happy path verifies post-state of all five documents.
    - _Requirements: 2.2, 2.3, 2.4, 2.6_
  - [ ] 10.3 Emulator integration test: happy path + idempotency race
    - Two concurrent invocations → exactly one pending doc, one mirrored pair of inbox/outbox entries, counters incremented by 1 on each side.
    - _Requirements: 2.2, 2.5_

- [ ] 11. `cancelFriendRequest` callable
  - [x] 11.1 Implement `functions/src/friendships/cancelFriendRequest.ts`
    - Transaction per Requirement 3.2. Reject if `status != PENDING || requesterId != callerUid` with `failed-precondition`. Return success if doc absent (idempotent).
    - Decrement counters floored at zero.
    - _Requirements: 3.1, 3.2, 3.3, 3.4_
  - [ ] 11.2 Jest + emulator tests
    - _Requirements: 3.2, 3.3, 3.4_

- [ ] 12. `acceptFriendRequest` callable
  - [x] 12.1 Implement `functions/src/friendships/acceptFriendRequest.ts`
    - Transaction per Requirement 4.2; reject non-PENDING or self-initiated with `failed-precondition`; reject missing doc with `not-found`; writes both `FriendEntry` denormalizations with display fields fetched from `users/{uid}`.
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5_
  - [ ] 12.2 Jest + emulator tests
    - _Requirements: 4.2, 4.3, 4.4, 4.5_

- [ ] 13. `declineFriendRequest` callable
  - [x] 13.1 Implement `functions/src/friendships/declineFriendRequest.ts`
    - Transaction per Requirement 5.2; idempotent when doc absent; reject non-PENDING with `failed-precondition`.
    - _Requirements: 5.1, 5.2, 5.3, 5.4_
  - [ ] 13.2 Jest + emulator tests
    - _Requirements: 5.2, 5.3, 5.4_

- [ ] 14. `removeFriend` callable
  - [x] 14.1 Implement `functions/src/friendships/removeFriend.ts`
    - Transaction per Requirement 6.2; idempotent when status != ACCEPTED; decrement both `friendsCount` floored at zero.
    - _Requirements: 6.1, 6.2, 6.3_
  - [ ] 14.2 Jest + emulator tests; assert `observeFriends` emission no longer contains removed friend (via emulator listener)
    - _Requirements: 6.2, 6.3, 6.4_

- [ ] 15. `blockUser` callable
  - [x] 15.1 Implement `functions/src/friendships/blockUser.ts`
    - Transaction per Requirement 7.2: write `blocks/{userId}`, set pair doc to `BLOCKED` with `requesterId = callerUid`, delete both friend entries, remove inbox/outbox mirrors, update all four counters correctly, increment `blockedCount`.
    - _Requirements: 7.1, 7.2_
  - [ ] 15.2 Jest + emulator tests
    - _Requirements: 7.2_

- [ ] 16. `unblockUser` callable
  - [x] 16.1 Implement `functions/src/friendships/unblockUser.ts`
    - Transaction per Requirement 7.4: delete `blocks/{userId}`, delete pair doc only when status == BLOCKED && requesterId == callerUid, decrement `blockedCount` floored at zero.
    - _Requirements: 7.3, 7.4_
  - [ ] 16.2 Jest + emulator tests
    - _Requirements: 7.4_

- [ ] 17. `onUserProfileUpdated` Firestore trigger (fan-out)
  - [x] 17.1 Implement `functions/src/profile/onUserProfileUpdated.ts`
    - Trigger: `onDocumentUpdated("users/{uid}")`. Compare before/after on `displayName`, `username`, `photoUrl`. If any changed, enqueue batched writes to every `users/{friend}/friends/{uid}` and every `users/{other}/inbox.entries[{uid}]` / `users/{other}/outbox.entries[{uid}]` mirror for pending requests.
    - Uses chunked batches of ≤ 400 writes; parallel across chunks.
    - Idempotent — reapplying the same diff leaves no duplicates or counter changes.
    - _Requirements: 13.1, 13.2, 13.4_
    - _Design: §4.2, §10_
  - [ ] 17.2 Emulator integration test for fan-out
    - Seed a user with 50 friends + 10 pending outgoing + 5 pending incoming; update `displayName`; assert all 65 mirror docs reflect the new value.
    - _Requirements: 13.1, 13.2_
  - [x] 17.3 Document p95 ≤ 5 s SLO + retry policy
    - Add `functions/README.md` note referencing Requirement 13.3 (monitored via Cloud Monitoring alert, not CI-enforced).
    - _Requirements: 13.3_

- [ ] 18. `backfillFriendships` callable (migration)
  - [x] 18.1 Implement `functions/src/migration/backfillFriendships.ts`
    - Admin-only callable. Iterates legacy `friendships/*` collection with `docId != pairId` shape; for each ACCEPTED doc, writes both `FriendEntry` mirrors, ensures `Friendship_Doc` at the new `pairId` path with `members`, and updates `friendsCount` on both summaries (recomputed from scratch — never incrementing the existing value).
    - Processes PENDING legacy docs similarly into inbox/outbox.
    - Idempotent: detects already-migrated pair via `friendships/{pairId}` existence + checksum.
    - Pagination via cursor; cron-safe.
    - _Requirements: 22.3, 22.4, 22.5, 22.6_
    - _Design: §13 Phase 1_
  - [ ] 18.2 Jest + emulator idempotency test
    - Run twice over the same seeded legacy dataset; assert identical final state.
    - Run with injected partial failure; assert resumable without duplicates.
    - _Requirements: 22.4, 22.6_

- [ ] 19. Cloud Functions property & integration tests
  - [ ] 19.1 **(PBT)** Friendship state-machine model-based test (emulator)
    - Reference model in TS: `type State = "NONE" | "PENDING_BY_A" | "PENDING_BY_B" | "ACCEPTED" | "BLOCKED_BY_A" | "BLOCKED_BY_B"`; deterministic transitions per design §3.3.
    - Generator: `fast-check.commands` driving random sequences of `{send, cancel, accept, decline, removeFriend, blockUser, unblockUser}` applied by either of the two actors.
    - After each command, assert:
      1. Emulator state of `friendships/{pairId}.status` equals the model.
      2. Both `users/{uid}/friends/{other}` existence matches model-`ACCEPTED`.
      3. Inbox/outbox mirrors match the pending direction.
      4. Counters match subcollection cardinalities.
      5. Illegal transitions are rejected with `failed-precondition`.
    - `numRuns: 100`, `shrink: true`.
    - Tag: `Feature: people-ux-and-friendship-data-redesign, Property 2: Friendship state-machine model consistency`.
    - _Requirements: 1.4, 2.2, 3.2, 3.3, 4.2, 4.3, 4.5, 5.2, 5.3, 6.2, 7.2, 7.4, 9.6, 10.4, 11.2, 11.3, 11.5_
    - _Design: §8 Property 2, §9_
  - [ ] 19.2 **(PBT)** Block-precedence forbids send (emulator)
    - `∀ (a, b) distinct, and ∀ blockConfig ∈ {A blocks B, B blocks A, both}, sendFriendRequest in either direction → permission-denied`.
    - `numRuns: 50`.
    - Tag: `Property 3: Block precedence forbids send`.
    - _Requirements: 2.4, 7.5_
    - _Design: §8 Property 3_
  - [ ] 19.3 **(PBT)** Send idempotence from non-NONE state (emulator)
    - Seed any non-NONE state; second send is success and mutates nothing.
    - Tag: `Property 4: Send is idempotent from non-NONE starting states`.
    - _Requirements: 2.5_
    - _Design: §8 Property 4_
  - [ ] 19.4 **(PBT)** Cancel/decline/remove idempotence from no-op state (emulator)
    - Seed absent doc or wrong-state doc → these callables return success without mutating any derived doc.
    - Tag: `Property 5: Cancel / decline / remove are idempotent from their no-op state`.
    - _Requirements: 3.4, 5.4, 6.3_
    - _Design: §8 Property 5_
  - [ ] 19.5 **(PBT)** Inbox/outbox mirror invariant (emulator)
    - After any `send` or `cancel` sequence: `∀ (a,b) with PENDING from a→b, inbox[b].entries[a] exists iff outbox[a].entries[b] exists with matching sentAt + pairId`.
    - Tag: `Property 2 (mirror sub-invariant)`.
    - _Requirements: 9.6_
  - [ ] 19.6 Inbox spill threshold example test (emulator)
    - Seed 500 entries → next send writes to `friendRequests_2`; client `observeIncomingRequests` merges both.
    - _Requirements: 9.5_
  - [ ] 19.7 Rate-limit boundary test (emulator)
    - 20 sends in window → all succeed; 21st → `resource-exhausted`.
    - _Requirements: 2.6_
  - [ ] 19.8 Profile fan-out integration test
    - Covered in 17.2; cross-reference here for traceability.
    - _Requirements: 13.1, 13.2_

- [ ] 20. Firestore Rules property tests (Phase 0 rules still permissive; strict tests gate Phase 8)
  - [ ] 20.1 **(PBT)** Per-path ownership (rules-unit-testing)
    - `@firebase/rules-unit-testing` with `fast-check` over `(owner, reader, admin?)` triples.
    - For each path (`friends`, `inbox`, `outbox`, `summary`, `blocks`, `friendships`), assert read/write allow/deny matches design §12.
    - During Phase 0, additionally assert owner-writes on `friends|inbox|outbox|summary` still succeed (permissive mode).
    - Tag: `Property 8: Firestore security rules enforce per-path ownership`.
    - _Requirements: 12.1, 12.2, 12.3, 12.4, 12.5, 12.6_
    - _Design: §8 Property 8, §12.3_
  - [ ] 20.2 Regression tests for existing rule blocks
    - Verify `groups`, `conversations`, `directLocationShares`, `activeLocations` behave as before the edit.
    - _Requirements: 12.7_

- [ ] 21. Phase-3 verification
  - [ ] 21.1 `npm --prefix functions run lint && npm --prefix functions run build && npm --prefix functions test`
  - [ ] 21.2 `firebase emulators:exec --only firestore,functions "npm --prefix functions run test:emulator"`

---

## Phase 4 — Use Cases, Repository Rewiring, and ViewModels

- [x] 22. Use cases
  - [x] 22.1 Consolidate friend use cases in `FriendUseCases.kt`
    - Add/replace: `ObserveFriendsUseCase`, `ObserveIncomingRequestsUseCase`, `ObserveOutgoingRequestsUseCase`, `ObserveSocialSummaryUseCase`, `ObserveBlockedUsersUseCase`, `SendFriendRequestUseCase`, `CancelFriendRequestUseCase`, `AcceptFriendRequestUseCase`, `DeclineFriendRequestUseCase`, `RemoveFriendUseCase`, `BlockUserUseCase`, `UnblockUserUseCase`, `GetFriendshipStatusUseCase`.
    - Drop `AcceptFriendRequestByUserIdUseCase` and `DeclineFriendRequestByUserIdUseCase` (collapsed).
    - _Requirements: 2.1, 3.1, 4.1, 5.1, 6.1, 7.1, 7.3, 8.1, 9.1, 9.3, 10.1_
    - _Design: §5.1, §5.4_
  - [x] 22.2 `SearchUsersUseCase` with set-join
    - New `app/src/main/java/com/ovi/where/domain/usecase/user/SearchUsersUseCase.kt`.
    - Depends on `UserRepository.searchUsers` plus the two observed flows (`friendIds: Set<String>`, `outgoingIds: Set<String>`) injected as `Flow<Set<String>>`.
    - Maps each result to `SearchUserUiModel` with derived `FriendshipStatus` — issues **zero** friendship reads.
    - _Requirements: 16.4, 16.5, 16.6, 16.7, 18.4_
    - _Design: §5.4, §6.3_
  - [x] 22.3 Update `UseCaseModule.kt`
    - Wire the new use cases into Hilt.
    - _Requirements: 23.2_

- [ ] 23. `PeopleViewModel`
  - [x] 23.1 Rewrite with `combine(observeFriends, observeSocialSummary)`
    - State: `PeopleUiState(friends, pendingRequestCount, activeNow, isLoading, error)`.
    - Ordering: friends by `displayName` (case-insensitive).
    - Expose `onRetry()` that re-subscribes the flows.
    - _Requirements: 14.1, 14.3, 14.4, 14.7, 18.1_
    - _Design: §5.4, §6.1_
  - [x] 23.2 Handle long-press actions & message navigation
    - Expose `onUnfriend(uid)`, `onBlock(uid)`, `onShareWith(uid)`, `onMessage(uid)` (delegates to existing `openOrCreateDm`).
    - _Requirements: 14.5, 14.6_
  - [ ] 23.3 Unit tests + listener error mapping
    - Assert `PERMISSION_DENIED` listener error surfaces as error state with retry.
    - _Requirements: 14.7, 21.6_

- [ ] 24. `FriendRequestsViewModel`
  - [x] 24.1 Rewrite with `combine(observeIncomingRequests, observeOutgoingRequests)` and `SavedStateHandle`-backed tab state
    - State: `FriendRequestsUiState(incoming, outgoing, selectedTab, isLoading, error, snackbar: OneShotEvent)`.
    - _Requirements: 15.1, 15.8, 18.2_
    - _Design: §5.4, §6.2_
  - [x] 24.2 Optimistic accept / decline / cancel with undo
    - Immediately remove from UI state; call callable; on failure, revert and emit error snackbar event.
    - `onUndo()` restores the row and calls the inverse callable (`acceptFriendRequest` inverse = `removeFriend`; `declineFriendRequest` / `cancelFriendRequest` inverse = `sendFriendRequest`).
    - _Requirements: 15.2, 15.3, 15.4, 15.5, 15.6_
  - [ ] 24.3 Unit tests
    - Drive Turbine-backed `StateFlow`; inject scripted callable outcomes; assert correct optimistic transitions, error reverts, and snackbar events.
    - _Requirements: 15.2, 15.3, 15.4, 15.5, 15.6_
  - [ ] 24.4 **(PBT)** Undo round-trip property
    - `∀ sequence of {accept, decline, cancel} on random entries, followed by undo before snackbar dismiss → final UI state == pre-sequence UI state`.
    - Tag: `Feature: people-ux-and-friendship-data-redesign, Property: Undo round-trip`.
    - _Requirements: 15.5, 15.6_
    - _Design: §6.2_

- [ ] 25. `SearchUsersViewModel`
  - [x] 25.1 Rewrite with 300 ms debounce + IME-Search bypass + set-join
    - Use `MutableStateFlow<String>` → `debounce(300).distinctUntilChanged().filter { it.length >= 2 }.flatMapLatest { ... }`.
    - Merge friend/outgoing sets from injected flows.
    - Expose `onSearchImmediately()` that skips the debounce for IME Search.
    - _Requirements: 16.1, 16.2, 16.3, 16.4_
    - _Design: §5.4, §6.3_
  - [x] 25.2 Action handlers
    - `onAddFriend(uid)`, `onCancelRequest(uid)` (with confirmation bottom sheet signal).
    - _Requirements: 16.5, 16.6, 16.7_
  - [ ] 25.3 Unit tests with virtual time
    - Use `TestScope` + `runTest` to verify debounce collapses rapid keystrokes to one call.
    - Assert zero friendship reads (using a counting fake repo).
    - _Requirements: 16.1, 16.4_
  - [ ] 25.4 **(PBT)** Debounce + set-derivation property
    - Property A: `∀ sequence of keystrokes within 300 ms window, repository.searchUsers is called ≤ 1 time`.
    - Property B: `∀ (users, friendSet, outgoingSet), for each u: action == FRIENDS iff u.id ∈ friendSet; PENDING iff u.id ∈ outgoingSet \ friendSet; ADD otherwise`.
    - Tag: `Feature: people-ux-and-friendship-data-redesign, Property 9: Search status derivation from already-subscribed sets`.
    - _Requirements: 16.1, 16.4, 16.5, 16.6, 16.7_
    - _Design: §8 Property 9_

- [ ] 26. `UserProfileViewModel`
  - [x] 26.1 Add error + not-found + block/cancel/unblock actions
    - State includes `ProfileFriendshipAction` sealed class (add `Blocked`, `BlockedByThem` branches).
    - Actions: `onSendRequest`, `onCancelRequest`, `onAccept`, `onDecline`, `onMessage`, `onUnfriend`, `onBlock`, `onUnblock`, `onRetry`.
    - Combines `getUser` + `getFriendshipStatus` + `observeBlockedUsers`.
    - _Requirements: 17.2, 17.3, 17.4, 17.5, 17.6, 17.7, 17.8, 17.9, 17.11_
    - _Design: §5.4, §6.4_
  - [x] 26.2 Error-code to snackbar mapping
    - Map `UNAVAILABLE → "You're offline"`, `DEADLINE_EXCEEDED → "Couldn't complete action, try again"`, `failed-precondition (already exists) → silent refresh via getFriendshipStatus`, `not-found → "Request is no longer available" + refresh listeners`, listener `PERMISSION_DENIED → error state`.
    - _Requirements: 21.2, 21.3, 21.4, 21.5, 21.6_
    - _Design: §7_
  - [ ] 26.3 Unit tests for each action + error path
    - _Requirements: 17.4–17.9, 21.2–21.6_
  - [ ] 26.4 **(PBT)** Callable-error → event mapping property
    - `∀ code ∈ {UNAVAILABLE, DEADLINE_EXCEEDED, FAILED_PRECONDITION, NOT_FOUND, PERMISSION_DENIED}, the emitted one-shot event matches the table in §7`.
    - Tag: `Property 11: ViewModel maps callable errors to the specified one-shot events`.
    - _Requirements: 21.2, 21.3, 21.4, 21.5, 21.6_
    - _Design: §8 Property 11_

- [ ] 27. Phase-4 verification
  - [ ] 27.1 `./gradlew :app:testDebugUnitTest -i`

---

## Phase 5 — Shared UI Components (Compose)

- [x] 28. Design-system primitives reused across People screens
  - [x] 28.1 `StatusDot` (10 dp) with semantic color + text label
    - `app/src/main/java/com/ovi/where/presentation/people/components/StatusDot.kt`.
    - _Requirements: 19.4, 20.5_
  - [x] 28.2 `LiveRingAvatar` wrapper composable (reuses DESIGN.md §6.1 ring)
    - _Requirements: 20.4_
  - [x] 28.3 `FriendshipActionPill` (ADD/PENDING/FRIENDS variants)
    - _Requirements: 16.5, 16.6, 16.7_
  - [x] 28.4 `WhereUndoSnackbar` variants for accept/decline/cancel
    - _Requirements: 15.2, 15.3, 15.4, 15.5_

- [x] 29. People-list row & section primitives
  - [x] 29.1 `FriendRow` with avatar, name, online text label, message icon (48 dp), long-press detector
    - _Requirements: 14.4, 14.5, 14.6, 14.8, 19.1, 19.2, 19.3_
  - [x] 29.2 `FriendsSectionHeader(title, count, accent?)`
    - _Requirements: 14.4_
  - [x] 29.3 `RequestsInboxCard(count, onClick)` with DESIGN.md §Level-3 shadow
    - _Requirements: 14.3, 20.2_

- [x] 30. Skeleton / empty / error primitives
  - [x] 30.1 `PeopleSkeleton` (1 card + 6 rows)
    - _Requirements: 14.1_
  - [x] 30.2 `RequestsSkeleton` (4 rows)
    - _Requirements: 15.x loading rendering_
  - [x] 30.3 `SearchLoadingShimmer` (≥4 rows)
    - _Requirements: 16.8_
  - [x] 30.4 `ProfileSkeleton` (ghost avatar + 2 bars)
    - _Requirements: 17.1_
  - [x] 30.5 `PeopleEmptyState`, `RequestsEmptyState(tab)`, `SearchPreEmptyState`, `SearchNoResultsState(query)`, `ProfileErrorState`, `ProfileNotFoundState`
    - _Requirements: 14.2, 15.7, 16.2, 16.10, 17.2, 17.3_
  - [x] 30.6 `InfoCard(type = ERROR)` at top with retry (reuse existing `presentation/common/Components.kt` if present)
    - _Requirements: 14.7, 16.9, 21.6_

- [x] 31. Profile components
  - [x] 31.1 `ProfileHeader(profile, isSharing)` + `ProfileStats(mutualCount, isSharing)` + `MutualFriendsSection(friends)`
    - _Requirements: 17.10, 17.11_
  - [x] 31.2 `ProfileActions` with state-driven branches (AddFriend, RequestSent, RequestReceived, AlreadyFriends, Blocked, BlockedByThem)
    - _Requirements: 17.4, 17.5, 17.6, 17.7, 17.8, 17.9_

- [x] 32. SearchBar component
  - [x] 32.1 `SearchBar` using `WhereTextField` with leading search icon + trailing clear + `imeAction = ImeAction.Search`
    - _Requirements: 16.11_

- [ ] 33. Compose unit tests for shared components
  - [ ] 33.1 Semantics tests for `FriendRow` (merged node, `Role.Button`, contentDescription includes online text label, 48 dp target)
    - _Requirements: 19.1, 19.2, 19.3, 19.4_
  - [ ] 33.2 Dimension tests for `StatusDot` (10 dp)
    - _Requirements: 20.5_

---

## Phase 6 — Per-Screen UX Redesign

- [ ] 34. `PeopleScreen` redesign
  - [x] 34.1 Rebuild `PeopleScreen.kt` per design §6.1 layout
    - Use `LazyColumn` keyed by `userId` or `pairId`; render `RequestsInboxCard`, `FriendsSectionHeader("Active now" / "All friends")`, `FriendRow` rows, empty/loading/error states; wire long-press bottom sheet.
    - _Requirements: 14.1, 14.2, 14.3, 14.4, 14.5, 14.6, 14.7, 14.8_
    - _Design: §6.1_
  - [ ] 34.2 Motion: 50 ms stagger enter animation
    - _Requirements: 20.1_
  - [ ] 34.3 Shared-element avatar hook for navigation to `UserProfileScreen`
    - _Requirements: 20.3_
  - [ ] 34.4 Compose UI tests (semantic tree assertions for each state)
    - _Requirements: 14.1–14.8, 19.1, 19.2, 19.3, 19.4_
  - [ ] 34.5 Golden screenshot tests — light + dark × {loading, empty, content, error}
    - New `app/src/androidTest/java/com/ovi/where/presentation/people/PeopleScreenGoldenTest.kt`.
    - _Requirements: 14.1, 14.2, 14.3, 14.4, 14.7, 20.1, 20.2_

- [ ] 35. `FriendRequestsScreen` redesign
  - [x] 35.1 Rebuild with segmented tabs (Incoming / Sent), `SavedStateHandle`-driven selection
    - _Requirements: 15.1, 15.7, 15.8, 19.5_
    - _Design: §6.2_
  - [ ] 35.2 Incoming row (`IncomingRequestRow`) with Accept + Decline + timestamp
    - _Requirements: 15.2, 15.3_
  - [ ] 35.3 Outgoing row (`OutgoingRequestRow`) with Cancel + timestamp
    - _Requirements: 15.4_
  - [ ] 35.4 Undo snackbars wired to ViewModel
    - _Requirements: 15.2, 15.3, 15.4, 15.5, 15.6_
  - [ ] 35.5 Compose UI tests
    - _Requirements: 15.*_
  - [ ] 35.6 Golden screenshot tests — light + dark × {loading, empty-incoming, empty-outgoing, content-incoming, content-outgoing, error}
    - _Requirements: 15.1, 15.7_

- [ ] 36. `SearchUsersScreen` redesign
  - [x] 36.1 Rebuild with `SearchBar` + pre-search / loading / no-results / error states; `FriendshipActionPill` per row
    - _Requirements: 16.1, 16.2, 16.4, 16.5, 16.6, 16.7, 16.8, 16.9, 16.10, 16.11_
    - _Design: §6.3_
  - [ ] 36.2 IME Search bypass wiring
    - _Requirements: 16.3_
  - [ ] 36.3 Compose UI tests
    - _Requirements: 16.*_
  - [ ] 36.4 Golden screenshot tests — light + dark × {pre-search, loading, results, no-results, error}
    - _Requirements: 16.2, 16.8, 16.10_

- [ ] 37. `UserProfileScreen` redesign
  - [x] 37.1 Rebuild per design §6.4 (header + actions + stats + mutual friends + live-ring + error/not-found states)
    - Top bar renders `@username` once loaded; placeholder is empty on loading (no "Profile" flash).
    - _Requirements: 17.1, 17.2, 17.3, 17.4, 17.5, 17.6, 17.7, 17.8, 17.9, 17.10, 17.11, 19.6, 20.3, 20.4_
    - _Design: §6.4_
  - [ ] 37.2 Block-confirmation dialog
    - _Requirements: 19.6_
  - [ ] 37.3 Compose UI tests
    - _Requirements: 17.*, 19.*_
  - [ ] 37.4 Golden screenshot tests — light + dark × each `ProfileFriendshipAction` branch + loading + error + not-found
    - _Requirements: 17.1–17.11_

- [ ] 38. Accessibility scanner instrumented tests (all four screens)
  - [ ] 38.1 Add Compose `AccessibilityChecks.enable()` + Google Accessibility Test Framework integration
    - _Requirements: 19.7_
  - [ ] 38.2 One test per screen driving all states and asserting zero content-label / touch-target-size / contrast-ratio violations
    - _Requirements: 19.1–19.7_

- [ ] 39. Navigation updates
  - [ ] 39.1 `Screen.kt` — add/verify routes; add optional `source` nav arg on `UserProfile`
    - _Requirements: 20.3_
  - [ ] 39.2 `AppNavGraph.kt` — shared-element hooks for avatar transition (using Compose `Modifier.sharedElement` or the project's existing shared-element util)
    - _Requirements: 20.3_
  - [ ] 39.3 Tab-state persistence verification — `FriendRequestsScreen` tab survives back-forward navigation
    - _Requirements: 15.8_

- [ ] 40. Phase-6 verification
  - [ ] 40.1 `./gradlew :app:connectedDebugAndroidTest` (requires emulator)
  - [ ] 40.2 `./gradlew :app:testDebugUnitTest`
  - [ ] 40.3 `./gradlew :app:lintDebug`

---

## Phase 7 — Migration Tooling and Dual-Write

- [ ] 41. Remote Config feature flag
  - [x] 41.1 Add `useNewFriendshipModel` key
    - Create a `FeatureFlags` Hilt-provided class wrapping `FirebaseRemoteConfig` with default `false`, min-fetch-interval 30 min in release, 0 s in debug.
    - _Requirements: 22.1, 22.2_
    - _Design: §13_
  - [ ] 41.2 Unit test for flag-gated routing
    - _Requirements: 22.1, 22.2_

- [ ] 42. Dual-write plumbing in `FriendshipRepositoryImpl`
  - [ ] 42.1 When `useNewFriendshipModel == false` or during the dual-write Phase 1 window, every mutation:
    - (a) Calls the new Cloud Function callable.
    - (b) Also writes to the legacy `friendships/{uuid}` collection (existing behaviour).
    - On any failure in either branch, return `Resource.Error` but leave the other branch's effects in place (backfill reconciles).
    - _Requirements: 22.3_
    - _Design: §13 Phase 1_
  - [ ] 42.2 Read-path selector
    - When `useNewFriendshipModel == true`, the observe methods read the new subcollections; otherwise they retain the legacy dual-listener path.
    - _Requirements: 22.1, 22.2_
  - [ ] 42.3 Integration tests covering flag flip mid-session
    - Assert next observe subscription uses the new path within one collection cycle.
    - _Requirements: 22.2_

- [x] 43. Backfill invocation + cleanup scripts
  - [x] 43.1 Document `functions/scripts/run-backfill.md`
    - `firebase functions:call backfillFriendships` invocation, expected duration, how to gate by user-id range, how to verify parity.
    - _Requirements: 22.4, 22.5, 22.6_
  - [x] 43.2 `functions/src/migration/cleanupLegacyFriendships.ts`
    - Batch-deletes legacy `friendships/{uuid}` docs that have a verified new-model counterpart at `friendships/{pairId}`.
    - Dry-run mode prints intended deletions; requires explicit `{ confirm: "yes" }` argument to actually delete.
    - _Requirements: 22.6_
  - [x] 43.3 Firestore export before cleanup (manual runbook)
    - Add note in `functions/scripts/run-backfill.md` instructing an export via `gcloud firestore export` before running cleanup.
    - _Requirements: 22.6_

- [ ] 44. Migration PBT
  - [ ] 44.1 **(PBT)** Backfill idempotence + parity (emulator)
    - Generate random legacy datasets (`N` users, `M` accepted friendships, `K` pending).
    - Run `backfillFriendships` twice; assert equality of derived state.
    - Assert `users/{uid}/summary/social.friendsCount` equals accepted legacy count for every uid.
    - Tag: `Property 12: Migration backfill is idempotent and preserves friend-count parity`.
    - _Requirements: 22.4, 22.5_
    - _Design: §8 Property 12_

- [ ] 45. Phase-7 verification
  - [ ] 45.1 Emulator integration suite green
  - [ ] 45.2 Manual dual-write smoke test on debug build

---

## Phase 8 — Cutover (Strict Firestore Rules + Flag On)

- [x] 46. Flip Remote Config flag to `true` for staged cohorts
  - [x] 46.1 Staged rollout plan (10% → 50% → 100%) documented in `functions/README.md`
    - _Requirements: 22.1, 22.2_

- [ ] 47. Switch `firestore.rules` to strict Phase 3 ruleset
  - [x] 47.1 Rewrite `firestore.rules` to match design §12.2 exactly
    - `friends|inbox|outbox|summary`: `write: if isAdmin()` (no more owner-write).
    - `friendships/{pairId}`: `read: if authed && request.auth.uid in resource.data.members; create/update/delete: if isAdmin()`.
    - Keep `blocks/*` as read+write by owner.
    - Leave `groups`, `conversations`, `directLocationShares`, `activeLocations` blocks unchanged.
    - _Requirements: 12.1, 12.2, 12.3, 12.4, 12.5, 12.6, 12.7_
    - _Design: §12.2_
  - [ ] 47.2 Re-run rules PBT (task 20.1) against the strict ruleset
    - Now additionally assert `write` by owner on `friends|inbox|outbox|summary` is **denied**.
    - _Requirements: 12.1, 12.2, 12.3, 12.6_
    - _Design: §8 Property 8_
  - [ ] 47.3 Deploy rules to production
    - `firebase deploy --only firestore:rules`.
    - _Requirements: 12.*_

- [ ] 48. Verify client no longer requires client-side writes to the new paths
  - [ ] 48.1 Audit `FriendshipRepositoryImpl` — after the cutover, the only remaining client writes are to `users/{uid}/blocks/*` (self-owned) and any Phase 1 legacy `friendships/{uuid}` writes (which are removed in Phase 9).
    - _Requirements: 12.6, 18.5_

---

## Phase 9 — Cleanup and Final Verification

- [ ] 49. Remove legacy code paths
  - [ ] 49.1 Remove dual-write branches from `FriendshipRepositoryImpl`
    - Delete legacy `friendships/{uuid}` writers; keep only new-path listeners and callable wrappers.
    - _Requirements: 22.3_
  - [ ] 49.2 Remove legacy listeners and batch-user fetches
    - Delete old `observeFriends` dual-listener path and its `whereIn`-capped user fetch (the >10-truncation bug disappears with it).
    - _Requirements: 8.5_
  - [ ] 49.3 Delete obsolete use cases (`AcceptFriendRequestByUserIdUseCase`, etc.) and their tests
    - _Requirements: 22.3_

- [ ] 50. Run `cleanupLegacyFriendships` in production
  - [ ] 50.1 Verify Firestore export completed (task 43.3)
  - [ ] 50.2 Invoke the callable (dry-run first, then real)
    - _Requirements: 22.6_

- [ ] 51. Final verification & sign-off
  - [ ] 51.1 `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug`
  - [ ] 51.2 `./gradlew :app:connectedDebugAndroidTest` on an emulator (Compose UI + accessibility scanner + shared-element nav)
  - [ ] 51.3 `npm --prefix functions run lint && npm --prefix functions run build && npm --prefix functions test`
  - [ ] 51.4 Emulator-backed Firestore rules + Cloud Functions integration + PBT suite
  - [ ] 51.5 Read-count parity check — run a scripted walkthrough in the emulator (open People, open Requests, open Profile, run a Search) and confirm each action stays within the budgets in Requirement 18 / design §15
    - _Requirements: 18.1, 18.2, 18.3, 18.4, 18.5_
  - [ ] 51.6 Remove the Remote Config flag default gate for new-model reads (`useNewFriendshipModel` becomes unconditionally true; the flag itself can remain for emergency rollback during one release cycle, then be retired)
    - _Requirements: 22.1, 22.2_

---

## Appendix A — PBT Task Index

Property-based test tasks, mapped to the design §8 correctness properties:

| Task  | Design §8 Property | Requirements |
|-------|--------------------|--------------|
| 1.7   | Property 1         | 1.2, 1.3, 1.5 |
| 6.4   | Property 10        | 1.6, 18.1–18.5 |
| 19.1  | Property 2         | 1.4, 2.2, 3.2, 3.3, 4.2, 4.3, 4.5, 5.2, 5.3, 6.2, 7.2, 7.4, 9.6, 10.4, 11.2, 11.3, 11.5 |
| 19.2  | Property 3         | 2.4, 7.5 |
| 19.3  | Property 4         | 2.5 |
| 19.4  | Property 5         | 3.4, 5.4, 6.3 |
| 19.5  | Property 2 (sub-invariant) | 9.6 |
| 20.1  | Property 8         | 12.1–12.6 |
| 24.4  | (supporting)       | 15.5, 15.6 |
| 25.4  | Property 9         | 16.1, 16.4–16.7 |
| 26.4  | Property 11        | 21.2–21.6 |
| 44.1  | Property 12        | 22.4, 22.5 |
| 47.2  | Property 8 (strict) | 12.1, 12.2, 12.3, 12.6 |

Property 6 (`observeFriends emits the full subcollection`) and Property 7 (`inbox/outbox emissions sorted by sentAt desc`) are validated by the repository unit tests in 6.2 combined with the emulator state-machine property 19.1 (which exercises the same listener paths while asserting post-state). No dedicated separate PBT task is required because the state-machine runs already generate N > 10 friend configurations and arbitrary pending maps.

---

## Appendix B — Cross-Reference by Requirement

Every requirement ID in `requirements.md` is covered by at least one task:

- **1.1–1.6** → 1.1, 1.2, 1.7, 4.6, 9.3, 9.4
- **2.1–2.7** → 5.1, 6.3, 10.1–10.3, 19.1, 19.3, 19.7
- **3.1–3.4** → 5.2, 6.3, 11.1–11.2, 19.1, 19.4
- **4.1–4.5** → 5.3, 6.3, 12.1–12.2, 19.1
- **5.1–5.4** → 5.4, 6.3, 13.1–13.2, 19.1, 19.4
- **6.1–6.4** → 5.5, 6.3, 14.1–14.2, 19.1, 19.4
- **7.1–7.6** → 5.6, 5.7, 6.3, 15.1–15.2, 16.1–16.2, 19.1, 19.2, 20.1, 4.5
- **8.1–8.5** → 4.1, 6.2, 17.1, 19.1, 49.2
- **9.1–9.6** → 4.2, 4.3, 4.7, 6.2, 19.1, 19.5, 19.6
- **10.1–10.4** → 4.4, 6.2, 19.1
- **11.1–11.5** → 1.2, 19.1
- **12.1–12.7** → 8.1, 20.1, 20.2, 47.1, 47.2
- **13.1–13.4** → 17.1, 17.2, 17.3
- **14.1–14.8** → 29.*, 30.*, 34.*
- **15.1–15.8** → 24.*, 30.*, 35.*
- **16.1–16.11** → 25.*, 28.3, 30.3, 32.1, 36.*
- **17.1–17.11** → 26.*, 30.4, 31.*, 37.*
- **18.1–18.5** → 6.4, 23.1, 24.1, 25.3, 26.1, 51.5
- **19.1–19.7** → 33.*, 34.4, 35.5, 36.3, 37.3, 38.*
- **20.1–20.5** → 28.1, 28.2, 34.2, 34.3, 37.1, 39.2
- **21.1–21.6** → 23.3, 26.2, 26.4, 5.1–5.7
- **22.1–22.6** → 41.*, 42.*, 43.*, 44.1, 46.1, 50.*, 51.6
- **23.1–23.3** → 3.1, 3.2, 3.3
