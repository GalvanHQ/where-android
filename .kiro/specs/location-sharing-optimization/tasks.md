# Implementation Plan: Location Sharing Optimization

## Overview

This plan transforms the WHERE app's location sharing system from a dual-write Firestore architecture to a Socket.IO-primary, cache-first system. Implementation proceeds in layers: data models and Room migration first, then repository logic (single write, throttle, Socket.IO emission), server relay, ViewModel updates, Firestore rules, and finally UX feedback. Kotlin is used for Android client code; JavaScript for the Socket.IO server.

## Tasks

- [x] 1. Update Room entity and database migration
  - [x] 1.1 Add new fields to SharedLocationEntity and create Room migration
    - Add `photoUrl: String?` (default null), `targetType: String` (default ""), `targetId: String` (default ""), `visibleTo: String?` (default null) to `SharedLocationEntity`
    - Create `Migration` object that adds columns via ALTER TABLE with correct defaults
    - Increment database version in the Room database class
    - _Requirements: 10.1, 10.4_

  - [x] 1.2 Add LocationDao query for targetId filtering
    - Add `observeByTargetId(targetId: String): Flow<List<SharedLocationEntity>>` query
    - Query filters by `targetId` and `isSharingActive = 1`, ordered by `timestamp DESC`
    - _Requirements: 10.3_

  - [x] 1.3 Implement toEntity/toDomain mapping with targetType inference
    - Update mapping functions to include all new fields (photoUrl, targetType, targetId, visibleTo)
    - Implement targetType inference: "direct" when groupId starts with "direct:", "group" otherwise
    - Store visibleTo as JSON-serialized string
    - _Requirements: 10.2, 10.5_

  - [ ] 1.4 Write property test for entity mapping round-trip
    - **Property 17: Entity Mapping Completeness**
    - Generate random SharedLocation objects and verify all fields survive toEntity → toDomain round-trip
    - **Validates: Requirements 10.2**

  - [ ] 1.5 Write property test for targetType inference
    - **Property 18: TargetType Inference from GroupId Prefix**
    - Generate groupIds with and without "direct:" prefix, verify correct targetType assignment
    - **Validates: Requirements 10.5**

  - [ ] 1.6 Write property test for DAO targetId filtering and ordering
    - **Property 19: DAO TargetId Filtering and Ordering**
    - Generate location sets with various targetIds, verify query returns only matching records ordered by timestamp descending
    - **Validates: Requirements 10.3**

- [x] 2. Implement LocationUpdateFrame data model and validation
  - [x] 2.1 Create LocationUpdateFrame data class
    - Define `LocationUpdateFrame` with fields: userId (String), latitude (Double), longitude (Double), accuracy (Float), speed (Float), bearing (Float), timestamp (Long)
    - Add validation function checking ranges: latitude -90..90, longitude -180..180, bearing 0..360, accuracy >= 0, speed >= 0
    - _Requirements: 4.6_

  - [ ] 2.2 Write property test for LocationUpdateFrame validation
    - **Property 6: Location_Update_Frame Validation**
    - Generate valid and invalid payloads, verify validation accepts/rejects correctly based on field presence and range constraints
    - **Validates: Requirements 4.6, 4.7**

- [x] 3. Remove legacy dual-write and implement single write target
  - [x] 3.1 Refactor LocationRepository to write only to activeLocations/{uid}
    - Remove all writes to `groups/{groupId}/locations/{uid}` and `directLocationShares/{shareId}/locations/{uid}`
    - Modify `startLocationSharing`, `updateLocation`, and `stopLocationSharing` to write only to `activeLocations/{uid}`
    - Include `visibleTo` array: for direct shares `[uid, friendId]`, for group shares all group member UIDs
    - Return `Resource.Error` on write failure instead of silently discarding
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6_

  - [x] 3.2 Denormalize displayName and photoUrl into activeLocations writes
    - Include current user's `displayName` (truncated to 50 chars) and `photoUrl` (max 2048 chars) in every write to activeLocations
    - Source values from authenticated user's profile
    - On profile update, include updated values in next `updateLocation` write
    - _Requirements: 2.1, 2.5_

  - [ ] 3.3 Write property test for visibleTo array correctness
    - **Property 2: VisibleTo Array Correctness**
    - Generate random member lists for group and direct shares, verify visibleTo contains exactly the correct set of UIDs
    - **Validates: Requirements 1.5**

  - [ ] 3.4 Write property test for denormalized profile inclusion
    - **Property 3: Denormalized Profile Inclusion**
    - Generate profiles with various displayName lengths and photoUrl values, verify truncation and inclusion
    - **Validates: Requirements 2.1**

- [x] 4. Implement speed-dependent write throttle
  - [x] 4.1 Implement adaptive Firestore write throttle in LocationRepository
    - Throttle writes to 10-second intervals when speed > 1 m/s (moving)
    - Throttle writes to 30-second intervals when speed ≤ 1 m/s (stationary)
    - On speed state transition (crossing 1 m/s threshold in either direction), send immediate write and reset timer
    - On throttled write failure, retain sample and attempt on next throttle cycle
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.7_

  - [ ] 4.2 Write property test for speed-dependent Firestore write throttle
    - **Property 9: Speed-Dependent Firestore Write Throttle**
    - Generate GPS fix sequences with timestamps and speeds, verify writes occur at correct minimum intervals
    - **Validates: Requirements 8.1, 8.2**

  - [ ] 4.3 Write property test for immediate write on speed state transition
    - **Property 10: Immediate Write on Speed State Transition**
    - Generate GPS fix sequences where speed crosses 1 m/s threshold, verify immediate write occurs and timer resets
    - **Validates: Requirements 8.3, 8.4**

- [ ] 5. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 6. Implement Socket.IO location emission on client
  - [x] 6.1 Add Socket.IO location_update emission to LocationRepository
    - Emit `location_update` event with full LocationUpdateFrame on each GPS fix
    - Emit at GPS fix interval (5s) when moving (speed > 1 m/s), independent of Firestore throttle
    - Emit at maximum 30-second intervals when stationary (speed ≤ 1 m/s)
    - On Socket.IO disconnect, continue Firestore writes without queuing emissions
    - On reconnect, resume emitting on next GPS fix without replaying missed updates
    - On emission failure, log and continue without retry
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6_

  - [ ] 6.2 Write property test for speed-dependent Socket.IO emission rate
    - **Property 8: Speed-Dependent Socket.IO Emission Rate**
    - Generate GPS fix sequences, verify emissions occur at 5s intervals when moving and 30s intervals when stationary
    - **Validates: Requirements 5.2, 5.3, 8.5, 8.6**

- [x] 7. Implement Socket.IO location relay on server
  - [x] 7.1 Add location room join logic on connection
    - When client connects with `locationRoom` query parameter, join client to that room
    - When client connects without `locationRoom`, allow connection but do not join any room
    - Existing Firebase ID token authentication middleware handles auth verification
    - Reject connections without valid/expired tokens
    - _Requirements: 4.2, 4.3, 4.4, 4.5_

  - [x] 7.2 Implement location_update event handler with validation and broadcast
    - Validate incoming frame has all required fields with values in specified ranges
    - Broadcast valid frames to all other clients in the same location room (exclude sender)
    - Discard invalid frames without broadcast
    - Do NOT persist location updates to Firestore from server
    - _Requirements: 4.1, 4.6, 4.7, 4.8_

  - [x] 7.3 Implement disconnect and location_user_offline broadcast
    - On client disconnect or explicit room leave, broadcast `location_user_offline` with userId to remaining room members
    - _Requirements: 4.9_

  - [ ] 7.4 Write property test for Socket.IO relay broadcast
    - **Property 7: Socket.IO Relay Broadcast**
    - Generate valid frames and room configurations, verify broadcast goes to all room members except sender and not to other rooms
    - **Validates: Requirements 4.1**

- [x] 8. Implement cache-first architecture and Firestore fallback
  - [x] 8.1 Implement persist-before-emit pattern in LocationRepository
    - When location update arrives from Socket.IO or Firestore, persist to Room cache BEFORE emitting to UI flow
    - Upsert semantics: one record per userId with latest data (REPLACE conflict strategy)
    - _Requirements: 6.2, 6.3_

  - [x] 8.2 Implement Socket.IO primary / Firestore fallback listener management
    - While Socket.IO connected and delivering updates, do NOT maintain Firestore snapshot listener
    - After Socket.IO disconnected > 10 seconds, activate Firestore snapshot listener on activeLocations filtered by visibleTo
    - On Socket.IO reconnect, remove Firestore fallback listener within 5 seconds
    - Persist fallback listener data to Room cache using same flow as Socket.IO updates
    - Ensure no duplicate entries per userId during transition
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5_

  - [ ] 8.3 Write property test for one record per user in cache
    - **Property 12: One Record Per User in Cache**
    - Generate sequences of location updates for same userId, verify Room cache contains exactly one record with latest data
    - **Validates: Requirements 6.3**

  - [ ] 8.4 Write property test for no duplicate entries during fallback transition
    - **Property 14: No Duplicate Entries During Fallback Transition**
    - Generate transition scenarios, verify emitted location list contains at most one entry per userId
    - **Validates: Requirements 7.5**

- [x] 9. Implement in-memory sharing status cache
  - [x] 9.1 Add activeSharingSessions in-memory map and status check logic
    - On `startLocationSharing`, insert groupId + expiry into `activeSharingSessions` map before returning
    - On `stopLocationSharing`, remove groupId from map before returning
    - On `checkSharingStatus`, check in-memory map first (0 reads); if miss, read single doc from activeLocations (1 read)
    - Never read from legacy subcollection paths for status checks
    - On read failure, return false and log error without throwing
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5, 9.6_

  - [ ] 9.2 Write property test for sharing status lookup chain
    - **Property 15: Sharing Status Lookup Chain**
    - Generate sharing session states, verify lookup checks in-memory first, then single doc read, never legacy paths
    - **Validates: Requirements 9.1, 9.2, 9.3**

  - [ ] 9.3 Write property test for session map lifecycle
    - **Property 16: Session Map Lifecycle**
    - Generate start/stop sequences, verify map contains entry after start and not after stop
    - **Validates: Requirements 9.4, 9.5**

- [ ] 10. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 11. Update GlobalMapViewModel for cache-first display and UX
  - [x] 11.1 Observe Room cache for instant location display
    - Observe `observeCachedLocations()` flow for immediate marker rendering (< 100ms target)
    - Use denormalized `displayName`/`photoUrl` from location documents
    - Fallback chain: denormalized field → in-memory user cache → userId/placeholder
    - Display empty map without error state when cache is empty
    - _Requirements: 6.1, 6.4, 6.6, 2.2, 2.3, 2.4_

  - [x] 11.2 Implement stale location indicator and offline state
    - Mark locations with timestamp > 5 minutes old as stale with visual indicator
    - Show persistent offline indicator when no network connectivity
    - _Requirements: 6.4, 6.5_

  - [ ] 11.3 Write property test for stale location indicator
    - **Property 13: Stale Location Indicator**
    - Generate timestamps relative to current time, verify stale indicator is exposed when timestamp > 5 minutes old
    - **Validates: Requirements 6.5**

- [x] 12. Update MapViewModel to use consolidated listener
  - [x] 12.1 Replace legacy group listener with consolidated active locations observer
    - Inject `ObserveActiveLocationsUseCase` instead of `ObserveGroupLocationsUseCase`
    - Filter consolidated stream client-side by matching `SharedLocation.targetId` to current groupId
    - Only include locations where `isSharingActive` is true and `sharingExpiresAt` has not elapsed
    - Read displayName/photoUrl from SharedLocation fields; fallback to userId if empty
    - Cancel all coroutine jobs on `onCleared`
    - Set UI error state on stream error without crashing
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_

  - [ ] 12.2 Write property test for client-side group filtering
    - **Property 5: Client-Side Group Filtering**
    - Generate random location lists and group filters, verify filtered output contains only matching active, non-expired locations
    - **Validates: Requirements 3.2**

- [x] 13. Implement location sharing UX feedback
  - [x] 13.1 Add countdown timer and sharing state to GlobalMapViewModel
    - Expose remaining duration as countdown string: "Xh Ym" when ≥ 60 min, "Xm" when < 60 min, updated every 60 seconds
    - Expose null countdown for sessions with no expiration (continuous sharing)
    - Expose boolean sharing-active indicator
    - On session expiry, update state to inactive, clear session data, stop tracking service within 5 seconds
    - On ViewModel init, restore sharing state from persisted session data within 2 seconds
    - If persisted session already expired, set inactive and clear data instead of restoring
    - _Requirements: 12.1, 12.2, 12.3, 12.4, 12.5, 12.6_

  - [ ] 13.2 Write property test for countdown format string
    - **Property 20: Countdown Format String**
    - Generate random durations, verify format is "Xh Ym" when ≥ 60 minutes and "Xm" when < 60 minutes
    - **Validates: Requirements 12.1**

- [x] 14. Update Firestore security rules
  - [x] 14.1 Add activeLocations security rules and mark legacy rules as deprecated
    - Add read rule: authenticated AND (uid == userId OR uid in visibleTo)
    - Add write rule: authenticated AND uid == userId
    - Add "DEPRECATED: remove after migration" comment to legacy location subcollection rule blocks
    - Do NOT modify rules for groups, conversations, directLocationShares (parent), users, friendships, or messages
    - _Requirements: 11.1, 11.2, 11.3, 11.4, 11.5_

- [ ] 15. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties using Kotest property testing (`io.kotest:kotest-property`)
- Unit tests validate specific examples and edge cases
- Kotlin is used for all Android client code; JavaScript for the Socket.IO server (task 7)
- The design uses a persist-before-emit pattern — Room is always written before UI flows emit

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "2.1"] },
    { "id": 1, "tasks": ["1.2", "1.3", "1.4", "1.5", "2.2"] },
    { "id": 2, "tasks": ["1.6", "3.1"] },
    { "id": 3, "tasks": ["3.2", "3.3", "3.4", "4.1"] },
    { "id": 4, "tasks": ["4.2", "4.3", "6.1"] },
    { "id": 5, "tasks": ["6.2", "7.1"] },
    { "id": 6, "tasks": ["7.2", "7.3"] },
    { "id": 7, "tasks": ["7.4", "8.1"] },
    { "id": 8, "tasks": ["8.2", "8.3", "9.1"] },
    { "id": 9, "tasks": ["8.4", "9.2", "9.3"] },
    { "id": 10, "tasks": ["11.1", "12.1"] },
    { "id": 11, "tasks": ["11.2", "11.3", "12.2", "13.1"] },
    { "id": 12, "tasks": ["13.2", "14.1"] }
  ]
}
```
