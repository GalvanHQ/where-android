# Implementation Plan: Profile Screen Fixes

## Overview

This plan addresses three issues with the Profile Screen: (1) replacing the one-shot Firestore `get()` with a real-time snapshot listener for live profile updates, (2) wiring quick action navigation callbacks, and (3) polishing the UI. Tasks are ordered so data layer changes come first, then presentation layer changes build on top.

## Tasks

- [x] 1. Implement real-time Firestore snapshot listener in AuthRepositoryImpl
  - [x] 1.1 Rewrite `currentUser` flow to use `addSnapshotListener`
    - Add import for `com.google.firebase.firestore.ListenerRegistration`
    - Replace the one-shot `.get()` call inside the `AuthStateListener` with `addSnapshotListener`
    - Store the `ListenerRegistration` in a local variable (`snapshotRegistration`)
    - On auth state change to authenticated: remove previous snapshot listener, attach new one on `users/{uid}`
    - On snapshot success: merge Firestore `User` with `isEmailVerified` from Firebase Auth, emit via `trySend`
    - On snapshot error: fall back to Firebase Auth data (`displayName`, `email`, `photoUrl`, `isEmailVerified`), log warning via Timber
    - On auth state change to signed-out: remove snapshot listener, emit `null`
    - In `awaitClose`: remove both `snapshotRegistration` and `authListener`
    - _Requirements: 1.1, 1.3, 1.4, 1.5, 1.6_

  - [ ]* 1.2 Write unit tests for real-time currentUser flow
    - Mock `FirebaseAuth` and `FirebaseFirestore` using MockK
    - Verify snapshot listener is attached when user is authenticated
    - Verify updated User is emitted when snapshot fires with new data
    - Verify fallback to Auth data on snapshot error
    - Verify listener is removed on sign-out and null is emitted
    - Verify both listeners cleaned up on flow cancellation (awaitClose)
    - _Requirements: 1.1, 1.4, 1.5, 1.6_

- [x] 2. Checkpoint - Verify data layer changes
  - Ensure all tests pass, ask the user if questions arise.

- [x] 3. Add navigation callback parameters to ProfileScreen
  - [x] 3.1 Add new parameters and wire quick action onClick handlers
    - Add three new parameters to `ProfileScreen` composable: `onNavigateToMessages: () -> Unit`, `onNavigateToGroups: () -> Unit`, `onNavigateToLocationSharing: () -> Unit`
    - Replace the empty `{ /* handled by bottom nav */ }` lambdas in the three `QuickActionRow` calls with the corresponding callbacks
    - Messages QuickActionRow → `onClick = onNavigateToMessages`
    - Groups QuickActionRow → `onClick = onNavigateToGroups`
    - Location Sharing QuickActionRow → `onClick = onNavigateToLocationSharing`
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_

- [x] 4. Wire navigation callbacks in MainScaffold
  - [x] 4.1 Provide navigation callbacks at the ProfileScreen call site
    - In `MainScaffold.kt`, update the `composable(BottomTab.Profile.route)` block to pass the three new callbacks to `ProfileScreen`
    - `onNavigateToMessages`: navigate `bottomNavController` to `BottomTab.Chats.route` with `popUpTo(BottomTab.Map.route) { saveState = true }`, `launchSingleTop = true`, `restoreState = true`
    - `onNavigateToGroups`: navigate `bottomNavController` to `BottomTab.People.route` with same nav options
    - `onNavigateToLocationSharing`: navigate `bottomNavController` to `BottomTab.Map.route` with same nav options
    - _Requirements: 3.1, 3.2, 3.3, 3.4_

  - [ ]* 4.2 Write Compose UI tests for quick action navigation
    - Use `createComposeRule` to render `ProfileScreen` with mock callbacks
    - Verify tapping "Messages" row invokes `onNavigateToMessages`
    - Verify tapping "Groups" row invokes `onNavigateToGroups`
    - Verify tapping "Location Sharing" row invokes `onNavigateToLocationSharing`
    - _Requirements: 3.1, 3.2, 3.3, 3.5_

- [x] 5. Checkpoint - Verify navigation wiring
  - Ensure all tests pass, ask the user if questions arise.

- [x] 6. Polish Profile Screen UI
  - [x] 6.1 Review and refine ProfileScreen layout
    - Verify animated gradient ring uses Material 3 primary/tertiary colors consistently
    - Ensure stats row icons use `primaryContainer` background with proper corner radius
    - Verify action buttons use correct Material 3 color roles (primary for Edit Profile, surfaceVariant for Settings)
    - Ensure quick actions card uses `surfaceContainerLow` with proper `tonalElevation`
    - Verify typography hierarchy: `headlineSmall` for name, `bodyMedium` for username/bio, `titleMedium` for stat counts, `bodySmall` for stat labels
    - Confirm spacing uses `Dimens` constants consistently (no hardcoded dp values except where intentional)
    - Ensure the fade-in entrance animation duration (500ms) and ring animation (1500ms) feel smooth
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7_

- [x] 7. Final checkpoint - Ensure all changes compile and tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster delivery
- Task 1 (data layer) and Task 3 (ProfileScreen parameters) can be done in parallel
- Task 4 depends on Task 3 (needs the new parameters to exist)
- Task 6 modifies ProfileScreen and should be done after Task 3 to avoid merge conflicts
- No property-based testing applies — all changes involve Firebase integration, UI rendering, and navigation callbacks
