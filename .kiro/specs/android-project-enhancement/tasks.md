# Implementation Plan: Android Project Enhancement

## Overview

This plan implements the comprehensive enhancement of the "Where" Android app across 14 requirements: codebase audit tooling, Material 3 theme migration, design system documentation, UI/UX redesign, animations, navigation, feature completion, permission handling, offline-first support, network optimization, notification management, code cleanup, and production readiness. Tasks are ordered to build foundational infrastructure first (theme, navigation, data layer utilities), then layer features on top, and finish with cleanup and production hardening.

## Tasks

- [x] 1. Set up core infrastructure and data layer utilities
  - [x] 1.1 Implement ConnectivityObserver interface and NetworkConnectivityObserver
    - Create `data/network/ConnectivityObserver.kt` interface with `isConnected: StateFlow<Boolean>`
    - Create `data/network/NetworkConnectivityObserver.kt` using ConnectivityManager.NetworkCallback
    - Register Hilt binding as @Singleton
    - _Requirements: 10.2_

  - [x] 1.2 Implement Resource sealed class and NetworkBoundResource utility
    - Create `data/util/Resource.kt` with Success, Error, Loading subtypes
    - Create `data/util/NetworkBoundResource.kt` inline function with query, fetch, saveFetchResult, shouldFetch, onFetchFailed parameters
    - Implement state emission: Loading(cached) → Success(fresh) or Error(exception, cached)
    - _Requirements: 11.2, 11.3_

  - [x] 1.3 Create CacheMetadataEntity and DAO for ETag/staleness tracking
    - Create `data/local/entity/CacheMetadataEntity.kt` with key, lastFetchedAt, eTag fields
    - Create `data/local/dao/CacheMetadataDao.kt` with insert, query by key, and delete operations
    - Add entity to Room database schema
    - Implement shouldFetch logic: return true if currentTime - lastFetchedAt > 300_000ms
    - _Requirements: 11.4, 11.5_

  - [x] 1.4 Create OfflineOperationEntity, DAO, and OfflineQueueWorker
    - Create `data/local/entity/OfflineOperationEntity.kt` with id, type, payload, createdAt, status, retryCount fields
    - Create `data/local/dao/OfflineOperationDao.kt` with getPendingOperations (LIMIT 50, ORDER BY createdAt ASC), insert, getActiveCount, updateStatus
    - Create `data/offline/OfflineQueueWorker.kt` extending CoroutineWorker with exponential backoff (30s base, max 3 retries)
    - Enforce 200 operation capacity limit before enqueue
    - Add entity to Room database schema
    - _Requirements: 10.4, 10.5, 10.6, 10.7, 10.8_

  - [x]* 1.5 Write property tests for NetworkBoundResource state machine
    - **Property 8: NetworkBoundResource state machine**
    - Test all combinations of cache state (empty, stale, fresh) and network response (success, failure)
    - Verify correct Resource emission sequence: Loading(cached) first, then Success or Error without discarding cache
    - **Validates: Requirements 11.2, 11.3**

  - [x]* 1.6 Write property tests for ETag cache validation and staleness threshold
    - **Property 9: ETag cache validation**
    - Verify If-None-Match header inclusion and 304 handling
    - **Property 10: Cache staleness threshold**
    - Verify shouldFetch returns true iff currentTime - lastFetchedAt > 300_000ms
    - **Validates: Requirements 11.4, 11.5**

  - [x]* 1.7 Write property tests for OfflineQueue
    - **Property 5: Offline queue round-trip persistence**
    - Verify operations persist with PENDING status and replay with original payload
    - **Property 6: Offline queue replay ordering**
    - Verify ascending createdAt order with max batch size 50
    - **Property 7: Offline queue capacity enforcement**
    - Verify rejection when queue has 200+ pending/in-progress operations
    - **Validates: Requirements 10.4, 10.6, 10.8**

- [x] 2. Material 3 theme migration and design system
  - [x] 2.1 Update Color.kt with #5170FF-based tonal palette
    - Replace existing Pink/Green/Purple palette with #5170FF-derived tonal steps (10, 20, 30, 40, 80, 90, 99) for primary, secondary, tertiary, error, neutral, neutral-variant
    - Preserve GoogleBlue constant unchanged
    - Update AvatarColors list (8 entries, hues distributed, 40-tone level)
    - Update LocationActive and LocationInactive to align with new secondary and neutral-variant roles
    - _Requirements: 2.1, 2.2, 2.6, 2.8_

  - [x] 2.2 Update Theme.kt with new LightColorScheme and DarkColorScheme
    - Assign new color tokens to all Material 3 color slots in both schemes
    - Set status bar color to colorScheme.surface
    - Set navigation bar color to colorScheme.surface
    - Configure isAppearanceLightStatusBars/isAppearanceLightNavigationBars (true for light, false for dark)
    - Preserve existing Type.kt (Nunito) and WhereShapes without modification
    - _Requirements: 2.3, 2.4, 2.7_

  - [x] 2.3 Replace hardcoded color literals in Composables with theme tokens
    - Scan all Composable files for Color(0xFF...) usage outside Color.kt
    - Replace each with semantically matching MaterialTheme.colorScheme token
    - _Requirements: 2.5_

  - [x] 2.4 Create DESIGN.md documentation file at project root
    - Document color palette (light + dark) with hex values and token names for all 28+ Material 3 color roles
    - Document typography scale (15 styles) with font family, weight, size, line height, letter spacing
    - Document shape scale (extraSmall through extraLarge) with corner radius values
    - Document spacing scale from Dimens.kt with all named tokens and dp values
    - Document component catalog from Components.kt with parameters and usage examples
    - Document screen inventory (18 screens) with layout composable and shared components used
    - Document animation specifications (duration, easing, repeat mode, location)
    - Document accessibility requirements (48dp touch targets, contentDescription, contrast ratios)
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8_

- [x] 3. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 4. Navigation architecture and deep linking
  - [x] 4.1 Refactor Screen sealed class to use kotlinx.serialization type-safe routes
    - Update `presentation/navigation/Screen.kt` to use @Serializable annotations on all 19 destinations
    - Ensure all route arguments (conversationId, userId, groupId) are type-safe parameters
    - _Requirements: 7.1, 7.2_

  - [x] 4.2 Implement deep link routing with "where://" scheme
    - Register deep link URI patterns in AppNavGraph for chat/{id}, user_profile/{id}, group_details/{id}, group_map/{id}, friend_requests
    - Implement authentication gatekeeper check before deep link navigation
    - Handle unrecognized URIs by discarding silently (no crash)
    - _Requirements: 7.4, 7.5_

  - [x] 4.3 Update AppNavGraph with fade-through transitions and back stack restoration
    - Apply fade-through transitions (300-400ms, EaseInOut) to all navigation transitions
    - Implement launchSingleTop to prevent duplicate entries on back press
    - Ensure full back stack restoration on process death (routes + arguments)
    - _Requirements: 6.1, 7.3, 7.6_

  - [x]* 4.4 Write property tests for deep link routing
    - **Property 3: Deep link routing correctness**
    - Generate valid URIs matching registered patterns and verify correct Screen resolution with arguments
    - Generate invalid URIs and verify null return without exceptions
    - **Validates: Requirements 7.4, 7.5**

- [x] 5. Permission handling system
  - [x] 5.1 Implement PermissionManager composable and PermissionManagerState
    - Create `presentation/permission/PermissionManager.kt` with rememberPermissionManager() composable
    - Implement rationale dialog display before system permission request
    - Implement permanent denial detection with Settings deep-link dialog
    - Implement background location ordering (verify foreground location granted first)
    - Handle POST_NOTIFICATIONS on Android 13+
    - Handle dismissal and denial gracefully (cancel operation, return to previous screen)
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5, 9.6, 9.7_

  - [x]* 5.2 Write property test for background location permission ordering
    - **Property 4: Background location permission ordering**
    - Verify ACCESS_BACKGROUND_LOCATION is never requested without prior foreground location grant
    - **Validates: Requirements 9.4**

- [x] 6. Notification management system
  - [x] 6.1 Implement NotificationHelper singleton with channel creation
    - Create `core/notification/NotificationHelper.kt` as @Singleton with Hilt injection
    - Implement createChannels() for messages (high), social (default), location_updates (high), group_activity (default), general (default)
    - Implement POST_NOTIFICATIONS permission check before posting (Android 13+)
    - _Requirements: 12.1, 12.2, 12.3_

  - [x] 6.2 Implement notification deep-link PendingIntents and grouping
    - Implement buildDeepLinkPendingIntent mapping: NEW_MESSAGE→Chat, FRIEND_REQUEST→FriendRequests, FRIEND_ACCEPTED→UserProfile, MEMBER_JOINED/LEFT→GroupDetails, LOCATION_UPDATE→GroupMap
    - Implement NotificationCompat.Group by conversationId with summary notification for 2+ distinct conversations
    - _Requirements: 12.4, 12.5_

  - [x] 6.3 Implement notification preference enforcement and unknown type routing
    - Create NotificationPreferencesRepository reading per-channel enable/disable from DataStore
    - Check preference before posting; suppress if channel disabled
    - Route unrecognized FCM message types to general channel
    - _Requirements: 12.6, 12.7_

  - [x]* 6.4 Write property tests for notification routing, grouping, and preferences
    - **Property 12: Notification deep-link routing**
    - Verify correct Screen mapping for each NotificationType
    - **Property 13: Notification grouping invariant**
    - Verify same-conversationId grouping and summary notification threshold
    - **Property 14: Notification channel preference enforcement**
    - Verify suppression when channel disabled; verify unknown types route to general
    - **Validates: Requirements 12.4, 12.5, 12.6, 12.7**

- [x] 7. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 8. UI/UX redesign — layout structure and shared components
  - [x] 8.1 Create/update shared UI components (ErrorView, EmptyState, ShimmerPlaceholder, OfflineBanner)
    - Create or update ErrorView composable with error message and retry button
    - Create or update EmptyState composable with icon, message (max 120 chars), optional action button
    - Create shimmer placeholder composable with 900ms linear gradient sweep
    - Create OfflineBanner composable (max 48dp height, non-obscuring, auto-dismiss within 3s on reconnect)
    - _Requirements: 4.5, 4.6, 4.7, 6.5, 10.3_

  - [x] 8.2 Update Main screen bottom navigation bar
    - Implement 4 tabs: Map, Chats, People, Profile
    - Use filled icon for selected state, outlined icon for unselected state
    - Add text labels matching tab names
    - _Requirements: 4.8_

  - [x] 8.3 Apply consistent layout structure across all screens
    - Apply Dimens.spaceLarge (16dp) horizontal padding to all content areas (except full-bleed Map screens)
    - Use Scaffold with topBar/bottomBar and apply content padding correctly
    - Consume WindowInsets for status bar and navigation bar
    - Apply Dimens.spaceLarge (16dp) vertical spacing between top app bar and first content element
    - Replace Column+verticalScroll with LazyColumn/LazyGrid for lists exceeding 20 items
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.9_

  - [x] 8.4 Implement interactive components across screens
    - Add WhereTopAppBar with scrollBehavior on People, FriendRequests, SearchUsers, Chats, GroupDetails screens
    - Apply Material 3 Card (tonalElevation 1dp, RoundedCornerShape 12dp) to group/conversation/user list items
    - Add pullToRefresh on People, FriendRequests, Chats screens (10s timeout)
    - Add swipe-to-dismiss on conversation items and notifications (40% threshold, 5s undo snackbar)
    - Add search bar with 300ms debounce and 2-char minimum on SearchUsers screen
    - Add FilterChip for sort/filter on People and Chats screens
    - Add FAB for "Create Group" on Groups screen and "Start Conversation" on Chats screen
    - Use AlertDialog for destructive confirmations, ModalBottomSheet for contextual menus
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7, 5.8_

- [x] 9. Animations and transitions
  - [x] 9.1 Implement press animations and list item animations
    - Add scale-down (0.95) + fade (0.7 opacity) on press for buttons/icons, return within 150ms
    - Add animateItem modifier with fade + vertical-slide (200-300ms) for LazyColumn items
    - _Requirements: 6.2, 6.3_

  - [x] 9.2 Implement splash screen and reduced motion support
    - Implement Android 12+ SplashScreen API with max 1000ms display duration
    - Detect "Remove animations" / "Reduce motion" accessibility setting
    - Skip or reduce all animation durations to ≤50ms when accessibility setting is enabled
    - _Requirements: 6.4, 6.6_

- [x] 10. Complete settings sub-screens
  - [x] 10.1 Implement NotificationPreferencesScreen with DataStore persistence
    - Create screen with toggles for friend requests, location updates, group activity, chat messages
    - Create NotificationPrefsViewModel reading/writing DataStore preferences
    - Persist toggle state across app restarts
    - _Requirements: 8.1_

  - [x] 10.2 Implement AppearanceScreen with theme selection
    - Create screen with light/dark/system-default selection
    - Create AppearanceViewModel with DataStore persistence
    - Apply selected theme immediately without app restart
    - _Requirements: 8.2_

  - [x] 10.3 Implement DataStorageScreen with cache management
    - Create screen displaying current cache size (KB/MB/GB)
    - Implement "Clear Cache" button with confirmation dialog
    - Update displayed cache size within 3 seconds after clearing
    - _Requirements: 8.3, 8.4_

  - [x] 10.4 Implement SecurityScreen with password reset and account deletion
    - Create screen with "Change Password" triggering Firebase Auth password-reset email
    - Implement "Delete Account" with confirmation dialog, re-authentication, permanent deletion, and navigation to Onboarding
    - Handle network/auth failures with error messages preserving session
    - _Requirements: 8.5, 8.6, 8.7_

  - [x] 10.5 Implement PrivacyScreen, HelpScreen, and AboutScreen
    - PrivacyScreen: location sharing default (always/friends/never) and profile visibility (everyone/friends/hidden) with DataStore persistence
    - HelpScreen: 5+ FAQ entries in expandable list, "Contact Support" button opening email client
    - AboutScreen: version name/code, open-source licenses list, Terms/Privacy links opening in browser
    - _Requirements: 8.8, 8.9, 8.10_

- [x] 11. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 12. Codebase audit engine
  - [x] 12.1 Implement AuditCodebaseTask as custom Gradle task
    - Create custom Gradle task `auditCodebase` in app/build.gradle.kts
    - Implement regex-based scanning of all Kotlin source files (excluding generated, build output, test sources)
    - Produce AUDIT.md at project root
    - _Requirements: 1.1_

  - [x] 12.2 Implement audit scanners for all pattern categories
    - Screen state detection: identify Screen composables and classify loading/error/empty/content states as present/missing
    - TODO/FIXME/HACK/STUB markers and TODO() function bodies with file path and line number
    - Unused imports, variables, functions, and commented-out code blocks (3+ consecutive commented lines)
    - Timber.d and Log.d debug log statements with file path and line number
    - Permission declarations in AndroidManifest.xml mapped to code usage locations
    - Network call patterns: Retrofit interfaces, Firestore queries, Socket.IO events with file path and line number
    - Notification channels with importance levels
    - Parse error handling: log errors and continue scanning
    - _Requirements: 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 1.9_

  - [x]* 12.3 Write property tests for audit pattern scanner
    - **Property 1: Audit pattern scanner completeness**
    - Generate Kotlin source files with known pattern counts and verify exact detection with correct file path and line number
    - **Property 2: Audit screen state detection**
    - Generate Screen composables with known state handlers and verify correct classification
    - **Validates: Requirements 1.2, 1.3, 1.5, 1.7**

- [x] 13. Network optimization and Firestore listener management
  - [x] 13.1 Apply NetworkBoundResource pattern to all list-fetching repositories
    - Refactor conversation, message, and group member repositories to use NetworkBoundResource
    - Ensure network requests only from ViewModel init or explicit user actions (not recomposition)
    - Implement ETag header support (If-None-Match, 304 handling) where applicable
    - Handle fetch failures by serving stale cache with retry after ≤60 seconds
    - _Requirements: 11.1, 11.2, 11.3, 11.4, 11.6_

  - [x] 13.2 Implement Firestore listener concurrency limiter
    - Create a listener registry that tracks active snapshot listeners
    - Enforce maximum of 10 concurrent listeners
    - Queue or combine new requests into collection-level queries when limit reached
    - _Requirements: 11.7_

  - [x]* 13.3 Write property test for Firestore listener concurrency limit
    - **Property 11: Firestore listener concurrency limit**
    - Generate sequences of listener registration requests and verify active count never exceeds 10
    - **Validates: Requirements 11.7**

- [x] 14. Code cleanup
  - [x] 14.1 Remove debug logs and replace with appropriate log levels
    - Remove all Timber.d and Log.d statements from production code
    - Replace error-recovery/state-transition guards with Timber.i or Timber.w as appropriate
    - _Requirements: 13.1_

  - [x] 14.2 Remove unused code, imports, and resources
    - Remove unused variables, functions, imports, and parameters
    - Remove commented-out code blocks (retain only design-decision comments or issue tracker references)
    - Remove unused drawable, string, and layout resources
    - _Requirements: 13.2, 13.3, 13.4_

  - [x] 14.3 Replace magic numbers/strings and standardize naming
    - Replace magic numbers and string literals with named constants or resource references (excluding 0, 1, -1 as index bounds and short dp/sp literals)
    - Standardize naming: camelCase for functions/properties, PascalCase for classes/composables, SCREAMING_SNAKE_CASE for constants
    - _Requirements: 13.5, 13.6_

  - [x] 14.4 Verify compilation and test suite after cleanup
    - Ensure project compiles without errors
    - Run all existing unit and instrumentation tests with no new failures
    - _Requirements: 13.7_

- [x] 15. Production readiness
  - [x] 15.1 Configure R8, ProGuard rules, and build variants
    - Enable R8 code shrinking and obfuscation for release build type
    - Add ProGuard keep rules for Firebase, Room (@Entity, @Dao), Hilt (@HiltViewModel, @Module), and kotlinx.serialization (@Serializable)
    - Define debug variant (debuggable, .debug suffix, cleartext allowed, Timber debug tree) and release variant (minify, shrinkResources, not debuggable, release keystore)
    - _Requirements: 14.1, 14.2_

  - [x] 15.2 Secure secrets and network configuration
    - Move all API keys/secrets to local.properties or BuildConfig from environment variables
    - Ensure local.properties is in .gitignore
    - Create network_security_config.xml with cleartextTrafficPermitted="false" for release
    - Set android:allowBackup="false" in AndroidManifest.xml
    - _Requirements: 14.3, 14.4, 14.10_

  - [x] 15.3 Implement performance optimizations
    - Verify zero runBlocking calls on main thread in any Activity/Fragment/Composable lifecycle path
    - Configure Coil with 25% heap memory cache and 50MB disk cache, use AsyncImage composable
    - Create baseline profile module for app startup and key journeys (Map, Chats, People)
    - _Requirements: 14.5, 14.6, 14.7_

  - [x] 15.4 Implement crash reporting and App Startup initialization
    - Configure Firebase Crashlytics with user ID (anonymized) and active screen route as custom keys on unhandled exceptions
    - Implement App Startup Initializers for Firebase, Timber, and Coil with declared dependencies
    - _Requirements: 14.8, 14.9_

- [x] 16. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document (14 properties total)
- Unit tests validate specific examples and edge cases
- The project uses Kotlin with Jetpack Compose, Hilt, Room, Firebase, Retrofit, Socket.IO, and Kotest Property for property-based testing
- NetworkBoundResource and OfflineQueue are foundational utilities used by multiple features, hence placed in the first task group

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2", "2.1"] },
    { "id": 1, "tasks": ["1.3", "1.4", "2.2"] },
    { "id": 2, "tasks": ["1.5", "1.6", "2.3", "2.4"] },
    { "id": 3, "tasks": ["1.7", "4.1", "5.1"] },
    { "id": 4, "tasks": ["4.2", "4.3", "5.2", "6.1"] },
    { "id": 5, "tasks": ["4.4", "6.2", "6.3"] },
    { "id": 6, "tasks": ["6.4", "8.1", "8.2"] },
    { "id": 7, "tasks": ["8.3", "8.4", "9.1"] },
    { "id": 8, "tasks": ["9.2", "10.1", "10.2", "10.3"] },
    { "id": 9, "tasks": ["10.4", "10.5", "12.1"] },
    { "id": 10, "tasks": ["12.2", "12.3", "13.1"] },
    { "id": 11, "tasks": ["13.2", "13.3", "14.1"] },
    { "id": 12, "tasks": ["14.2", "14.3"] },
    { "id": 13, "tasks": ["14.4", "15.1"] },
    { "id": 14, "tasks": ["15.2", "15.3"] },
    { "id": 15, "tasks": ["15.4"] }
  ]
}
```
