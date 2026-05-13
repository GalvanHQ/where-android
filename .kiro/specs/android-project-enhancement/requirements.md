# Requirements Document

## Introduction

This document specifies the requirements for a comprehensive enhancement of the "Where" Android application — a location-sharing and group-coordination app built with Jetpack Compose, Hilt, Room, Firebase, and a Node.js chat server. The enhancement spans 12 phases: codebase audit, Material 3 theme migration (primary #5170FF), design system documentation, UI/UX redesign of all screens, animations and navigation improvements, completion of unimplemented features, unified permission handling, offline-first support, network call optimization, notification management, code cleanup, and production readiness.

## Glossary

- **App**: The "Where" Android application (package `com.ovi.where`)
- **Theme_System**: The Material 3 theming layer comprising Color.kt, Theme.kt, Type.kt, Shape.kt, and Dimens.kt
- **Audit_Engine**: The static analysis process that scans the codebase for TODOs, FIXMEs, unused code, debug logs, and permission usages
- **Design_System**: The documented set of colors, typography, shapes, spacing, components, and accessibility guidelines
- **Navigation_Controller**: The single Compose NavController managing all screen transitions in the app
- **Permission_Manager**: A unified component that handles runtime permission requests, rationale dialogs, and Settings deep links
- **Connectivity_Observer**: A component that monitors network connectivity state and exposes it as a reactive Flow
- **Offline_Queue**: A WorkManager-backed queue that persists write operations performed while offline and replays them when connectivity is restored
- **Network_Bound_Resource**: A pattern that serves cached Room data first, then fetches from the network and updates the cache
- **Notification_Helper**: A singleton responsible for creating notification channels, building notifications, and managing notification preferences
- **Shimmer_Placeholder**: An animated loading placeholder that mimics content layout before data arrives
- **Screen**: A full-page Composable destination in the navigation graph (currently: Onboarding, Login, SignUp, ForgotPassword, EmailVerification, CompleteProfile, Main, EditProfile, Settings, Chat, UserProfile, FriendRequests, SearchPeople, GroupDetails, GroupMap, CreateGroup, JoinGroup, EditGroup)

## Requirements

### Requirement 1: Codebase Audit Report

**User Story:** As a developer, I want an automated audit of the entire codebase, so that I have a clear inventory of technical debt, incomplete features, and cleanup targets before starting enhancements.

#### Acceptance Criteria

1. WHEN the Audit_Engine is executed, THE Audit_Engine SHALL scan all Kotlin source files under the main source set (excluding generated files, build output, and test source sets) and produce an AUDIT.md file at the project root
2. WHEN the Audit_Engine produces the AUDIT.md file, THE Audit_Engine SHALL list every Screen defined in the navigation sealed class with its associated UI states (loading, error, empty, content), marking each state as "present" or "missing" based on whether the screen's composable or ViewModel handles that state
3. WHEN the Audit_Engine produces the AUDIT.md file, THE Audit_Engine SHALL identify and list all comments matching TODO, FIXME, HACK, and STUB markers (case-insensitive) as well as functions whose body contains only a TODO() call or returns a hardcoded placeholder value, with file path and line number for each
4. WHEN the Audit_Engine produces the AUDIT.md file, THE Audit_Engine SHALL identify unused imports, unused variables, unused functions, and commented-out code blocks (defined as 3 or more consecutive lines that are commented out) with file path and line number for each
5. WHEN the Audit_Engine produces the AUDIT.md file, THE Audit_Engine SHALL list all Timber.d and Log.d debug log statements with file path and line number
6. WHEN the Audit_Engine produces the AUDIT.md file, THE Audit_Engine SHALL list all declared permissions in AndroidManifest.xml and map each to the code locations that use the permission
7. WHEN the Audit_Engine produces the AUDIT.md file, THE Audit_Engine SHALL document all network call patterns including Retrofit interface method declarations, Firestore collection/document queries, and Socket.IO event listeners and emitters with file path and line number for each
8. WHEN the Audit_Engine produces the AUDIT.md file, THE Audit_Engine SHALL list all notification channels defined in the codebase with their importance levels
9. IF the Audit_Engine encounters a Kotlin source file that cannot be parsed, THEN THE Audit_Engine SHALL log the file path and parsing error in a "Parse Errors" section of AUDIT.md and continue scanning the remaining files

### Requirement 2: Material 3 Theme Migration

**User Story:** As a designer, I want the app to use a cohesive Material 3 color scheme derived from primary color #5170FF, so that the visual identity is consistent and modern.

#### Acceptance Criteria

1. THE Theme_System SHALL define a primary color of #5170FF and derive a Material 3 tonal palette covering primary, secondary, tertiary, error, neutral, and neutral-variant color roles, each with at minimum tonal steps 10, 20, 30, 40, 80, 90, and 99 for both light and dark schemes
2. THE Theme_System SHALL update Color.kt to contain the derived tonal palette values replacing the current Pink-based, Green-based, and Purple-based palette values while preserving the GoogleBlue brand constant unchanged
3. THE Theme_System SHALL update Theme.kt to assign the new color tokens to all slots in both LightColorScheme and DarkColorScheme, maintaining the same slot coverage as the current implementation
4. THE Theme_System SHALL maintain the existing Nunito font family in Type.kt and the existing shape scale in WhereShapes without modification
5. WHEN a hardcoded color literal (Color(0xFF...)) is found in a Composable function outside of Color.kt, THE Theme_System SHALL replace it with the semantically matching MaterialTheme.colorScheme token based on the color's role (e.g., brand accent maps to primary, success maps to secondary, background maps to surface)
6. THE Theme_System SHALL preserve the AvatarColors list with exactly 8 entries whose hues are distributed to maintain visual distinguishability and whose tone values are derived from the new palette's 40-tone level
7. THE Theme_System SHALL set the status bar color to colorScheme.surface, the navigation bar color to colorScheme.surface, and configure isAppearanceLightStatusBars and isAppearanceLightNavigationBars to true in light mode and false in dark mode
8. THE Theme_System SHALL preserve the LocationActive and LocationInactive semantic color constants in Color.kt, updating their values to align with the new palette's secondary and neutral-variant roles respectively

### Requirement 3: Design System Documentation

**User Story:** As a developer, I want a comprehensive DESIGN.md document, so that all contributors follow the same visual and interaction guidelines.

#### Acceptance Criteria

1. THE Design_System SHALL produce a DESIGN.md file at the project root containing the documented color palette for both light and dark color schemes, listing each Material 3 color role (primary, onPrimary, primaryContainer, onPrimaryContainer, secondary, onSecondary, secondaryContainer, onSecondaryContainer, tertiary, onTertiary, tertiaryContainer, onTertiaryContainer, error, onError, errorContainer, onErrorContainer, background, onBackground, surface, onSurface, surfaceVariant, onSurfaceVariant, outline, outlineVariant, inverseSurface, inverseOnSurface, inversePrimary, scrim) with its hex value and the semantic token name from Color.kt; the requirement is satisfied only when the file exists AND contains the color palette documentation
2. THE Design_System SHALL document the typography scale listing all 15 Material 3 text styles (displayLarge, displayMedium, displaySmall, headlineLarge, headlineMedium, headlineSmall, titleLarge, titleMedium, titleSmall, bodyLarge, bodyMedium, bodySmall, labelLarge, labelMedium, labelSmall) with font family, font weight, font size in sp, line height in sp, and letter spacing in sp for each style
3. THE Design_System SHALL document the shape scale (extraSmall: 6dp, small: 12dp, medium: 16dp, large: 24dp, extraLarge: 32dp) with corner radius values matching WhereShapes in Theme.kt
4. THE Design_System SHALL document the spacing scale from Dimens.kt listing each named token (spaceNone through space3XLarge) with its dp value, and each additional dimension category (buttons, icons, avatars, cards, strokes, map markers, splash/onboarding) with token names and dp values
5. THE Design_System SHALL include a component catalog listing all shared Composables defined in Components.kt, documenting for each composable: its name, all required parameters with types, all optional parameters with types and default values, and at least 1 code usage example per composable
6. THE Design_System SHALL include a screen inventory listing all 18 screens defined in Screen.kt, mapping each screen route to a description of its top-level layout composable (Column, Row, Scaffold, Box) and the shared components it uses from Components.kt
7. THE Design_System SHALL document animation specifications for each animation defined in the codebase, including duration in milliseconds, easing curve name, repeat mode, and the composable or screen where it is applied
8. THE Design_System SHALL document accessibility requirements specifying minimum touch target size of 48dp, the requirement for contentDescription on all interactive Icon and Image composables, and minimum contrast ratios of 4.5:1 for normal text (below 14sp bold or 18sp regular) and 3:1 for large text (14sp bold or 18sp regular and above) per WCAG 2.1 AA

### Requirement 4: UI/UX Redesign — Layout and Structure

**User Story:** As a user, I want every screen to have consistent layout structure, so that the app feels polished and predictable.

#### Acceptance Criteria

1. THE App SHALL apply consistent horizontal padding of Dimens.spaceLarge (16dp) to all screen content areas, except full-bleed screens (GlobalMapScreen, GroupMap) where content extends edge-to-edge
2. THE App SHALL use Scaffold with a topBar parameter (when the screen displays an app bar), a bottomBar parameter (when the screen displays bottom navigation), and shall apply the Scaffold-provided content padding to the inner content on every screen that uses an app bar or bottom navigation
3. THE App SHALL consume WindowInsets for both the status bar and the navigation bar so that no interactive or text content renders behind either system bar
4. WHEN a Screen displays a scrollable list, THE App SHALL use LazyColumn or LazyGrid instead of Column with verticalScroll for lists exceeding 20 items
5. THE App SHALL display a shimmer skeleton placeholder within 0 milliseconds of entering a loading state (no delay) on every Screen that fetches remote data, and the placeholder SHALL remain visible until data loads or an error state is reached
6. THE App SHALL display an EmptyState composable with an icon, a descriptive message (maximum 120 characters), and an optional action button when a list has zero items
7. IF a network request fails, THEN THE App SHALL display an ErrorView composable with an error message describing the failure reason and a retry button that re-triggers the failed request
8. WHEN the Main screen is displayed, THE App SHALL show a bottom navigation bar containing exactly 4 tabs (Map, Chats, People, Profile) each with a filled icon variant for the selected state, an outlined icon variant for the unselected state, and a text label matching the tab name
9. THE App SHALL apply identical vertical spacing of Dimens.spaceLarge (16dp) between the top app bar and the first content element on all screens that use a top app bar

### Requirement 5: UI/UX Redesign — Interactive Components

**User Story:** As a user, I want modern interactive components throughout the app, so that interactions feel responsive and intuitive.

#### Acceptance Criteria

1. THE App SHALL use the WhereTopAppBar composable with a non-null scrollBehavior parameter (enterAlways or exitUntilCollapsed) on every screen whose body contains a scrollable LazyColumn or LazyVerticalGrid, including People_Screen, Friend_Requests_Screen, Search_Users_Screen, ChatsScreen, and GroupDetailsScreen
2. THE App SHALL use Material 3 Card composables with tonalElevation of 1dp and shape of RoundedCornerShape(12.dp) for all list items representing groups, conversations, or users across every screen that renders such items
3. THE App SHALL use Material 3 AlertDialog for destructive confirmations (unfriend, block, delete) and ModalBottomSheet for contextual multi-option menus (long-press actions, sort/filter selections)
4. THE App SHALL provide pullToRefresh (Material 3 pull-to-refresh indicator) on People_Screen, Friend_Requests_Screen, and ChatsScreen, triggering a re-fetch of the corresponding remotely-sourced list data and displaying the refresh indicator until the new data emission arrives or a timeout of 10 seconds elapses
5. THE App SHALL support swipe-to-dismiss gestures on conversation list items in ChatsScreen and notification items, requiring a horizontal swipe displacement of at least 40% of the item width to trigger the action, and SHALL display a 5-second undo snackbar before committing the deletion or archival
6. THE App SHALL provide a search bar with input debounced by 300 milliseconds on the Search_Users_Screen only (no other screens), and SHALL not invoke the search backend until the query contains at least 2 characters; IF the search bar component fails to render or is disabled, THEN THE App SHALL prevent all search functionality on that screen
7. THE App SHALL use FilterChip composables for sort and filter options on People_Screen and ChatsScreen, rendering selected chips with a filled tonal style and unselected chips with an outlined style
8. THE App SHALL use FloatingActionButton for primary creation actions, specifically: a FAB on the Groups screen for "Create Group" and a FAB on ChatsScreen for "Start Conversation"

### Requirement 6: Animations and Transitions

**User Story:** As a user, I want smooth animations and transitions, so that the app feels fluid and responsive.

#### Acceptance Criteria

1. WHEN navigating between screens, THE Navigation_Controller SHALL apply fade-through transitions with a duration between 300ms and 400ms using EaseInOut easing
2. WHEN items are added to or removed from a LazyColumn, THE App SHALL animate the item appearance and disappearance using animateItem modifier with a fade and vertical-slide animation lasting between 200ms and 300ms
3. WHEN the user presses a button or taps an icon, THE App SHALL apply a scale-down to 0.95 of original size and a fade to 0.7 opacity only for the duration of the actual press, returning to 1.0 scale and 1.0 opacity within 150ms of release; WHEN the button is not being pressed, THE App SHALL NOT apply any opacity or scale effects
4. WHEN the app launches, THE App SHALL display an animated splash screen using the Android 12+ SplashScreen API with a maximum display duration of 1000ms before transitioning to the first content screen
5. WHILE data is loading, THE App SHALL display shimmer animations on placeholder content matching the expected content layout dimensions, with a linear gradient sweep duration of 900ms repeating until loading completes or a timeout of 30 seconds is reached
6. IF the device has the "Remove animations" or "Reduce motion" accessibility setting enabled, THEN THE App SHALL skip or reduce all transition and animation durations to no more than 50ms

### Requirement 7: Navigation Architecture

**User Story:** As a developer, I want a single, type-safe navigation graph, so that navigation is predictable and deep links work reliably.

#### Acceptance Criteria

1. THE App SHALL use a single root-level NavController instance for all top-level and detail screen navigation, with the exception of nested navigation within the Main scaffold bottom tabs
2. THE App SHALL define type-safe route objects using Kotlin sealed classes or serializable route classes for all 19 navigation destinations (Onboarding, Login, SignUp, ForgotPassword, EmailVerification, CompleteProfile, Main, EditProfile, Settings, Chat, UserProfile, FriendRequests, SearchPeople, GroupDetails, GroupMap, CreateGroup, JoinGroup, EditGroup, and the Gatekeeper route)
3. WHEN the system back button is pressed, THE Navigation_Controller SHALL pop the current destination from the back stack and display the previous destination, using launchSingleTop semantics to prevent consecutive duplicate entries of the same route
4. WHEN a deep link URI with scheme "where://" is received and the URI path matches a registered route (chat, user_profile, group_details, group_map, or friend_requests), THE Navigation_Controller SHALL navigate to the corresponding Screen after the authentication gatekeeper resolves successfully
5. IF a deep link URI with scheme "where://" is received and the URI path does not match any registered route, THEN THE Navigation_Controller SHALL discard the deep link and remain on the current screen without crashing; other navigation operations unrelated to the invalid deep link SHALL continue to function normally
6. WHEN the app is restored from process death, THE Navigation_Controller SHALL restore the full navigation back stack including all destination routes and their associated arguments (such as conversationId, userId, and groupId) to the state held immediately before the process was killed

### Requirement 8: Complete Unimplemented Features

**User Story:** As a user, I want all settings sub-screens and stub features to be fully functional, so that the app delivers on its promised capabilities.

#### Acceptance Criteria

1. WHEN the user taps "Notifications" in Settings, THE App SHALL navigate to a Notification Preferences screen displaying toggles for each notification category (friend requests, location updates, group activity, and chat messages), with each toggle persisted to DataStore so that the selected state is retained across app restarts
2. WHEN the user taps "Appearance" in Settings, THE App SHALL navigate to an Appearance screen where the user can select light, dark, or system-default theme, and THE App SHALL apply the selected theme immediately without requiring an app restart
3. WHEN the user taps "Data & Storage" in Settings, THE App SHALL navigate to a Data & Storage screen displaying the current cache size in human-readable format (KB, MB, or GB) and providing a "Clear Cache" button
4. WHEN the user taps the "Clear Cache" button on the Data & Storage screen, THE App SHALL display a confirmation dialog, and upon confirmation, delete cached data and update the displayed cache size to reflect the new value; the requirement is satisfied when the confirmation dialog is shown and the deletion is initiated (timing of the UI update is a separate concern)
5. WHEN the user taps "Account Security" in Settings, THE App SHALL navigate to a Security screen providing a "Change Password" action that triggers a Firebase Auth password-reset email to the user's registered address, and a "Delete Account" action
6. WHEN the user taps "Delete Account" on the Security screen, THE App SHALL display a confirmation dialog warning that this action is irreversible, require re-authentication, and upon confirmation permanently delete the account and navigate the user to the Onboarding screen; the complete flow including deletion and navigation SHALL be enforced end-to-end
7. IF account deletion or password-reset email fails due to network error or authentication failure, THEN THE App SHALL display an error message indicating the failure reason and preserve the user's current session without data loss
8. WHEN the user taps "Privacy" in Settings, THE App SHALL navigate to a Privacy screen where the user can set location sharing default (always share, share with friends only, or never share) and profile visibility (visible to everyone, friends only, or hidden), with selections persisted to DataStore
9. WHEN the user taps "Help & Support" in Settings, THE App SHALL navigate to a Help screen displaying at least 5 FAQ entries in an expandable list and a "Contact Support" button that opens the device email client pre-addressed to the support email
10. WHEN the user taps "About Where" in Settings, THE App SHALL navigate to an About screen displaying the app version name and version code, a scrollable list of open-source licenses, and tappable links for Terms of Service and Privacy Policy that open in the device's default browser

### Requirement 9: Unified Permission Handling

**User Story:** As a user, I want clear permission requests with explanations, so that I understand why the app needs each permission and can grant them confidently.

#### Acceptance Criteria

1. THE Permission_Manager SHALL provide a single entry point for requesting any runtime permission used by the app (ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION, ACCESS_BACKGROUND_LOCATION, POST_NOTIFICATIONS)
2. WHEN a permission is requested and the user has not previously granted or permanently denied it, THE Permission_Manager SHALL display a rationale dialog that includes the name of the permission and a one-sentence explanation of the app feature that requires it, before invoking the system permission dialog
3. IF the user has permanently denied a permission (shouldShowRequestPermissionRationale returns false after denial), THEN THE Permission_Manager SHALL display a dialog directing the user to the app's Settings page with a deep link to the system app settings; this settings dialog SHALL only be shown for permanent denials and not for other scenarios
4. WHEN ACCESS_BACKGROUND_LOCATION is requested, THE Permission_Manager SHALL first verify that ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION is already granted (coarse location alone is sufficient), and only then request ACCESS_BACKGROUND_LOCATION in a separate system dialog
5. WHEN the POST_NOTIFICATIONS permission is needed on Android 13+, THE Permission_Manager SHALL request it before attempting to show any notification
6. IF the user denies a permission without permanently denying it, THEN THE Permission_Manager SHALL cancel the current permission-dependent operation and return the user to the previous screen without data loss
7. IF the user dismisses the rationale dialog without proceeding, THEN THE Permission_Manager SHALL not invoke the system permission dialog and SHALL cancel the permission-dependent operation

### Requirement 10: Offline-First Support

**User Story:** As a user, I want the app to work seamlessly when I lose connectivity, so that I can still view cached data and queue actions for later.

#### Acceptance Criteria

1. THE App SHALL use Room as the single source of truth for all displayed data (conversations, messages, groups, user profiles)
2. THE Connectivity_Observer SHALL expose a StateFlow<Boolean> indicating current network availability
3. WHILE the device has no network connectivity AND the Connectivity_Observer reports network_available as false, THE App SHALL display a banner at the top of the screen indicating offline status that does not obscure content and does not exceed 48dp in height, and WHEN connectivity is restored, THE App SHALL dismiss the banner within 3 seconds
4. WHEN the user performs a write operation (send message, create group, update profile) while offline, THE Offline_Queue SHALL execute the operation immediately against local data, persist the operation to the queue for server sync, display a pending indicator on the affected item, and execute the server sync when connectivity is restored
5. THE Offline_Queue SHALL use WorkManager with network connectivity constraints and a maximum of 3 retry attempts per operation with exponential backoff to deliver queued operations
6. WHEN connectivity is restored, THE Offline_Queue SHALL replay queued operations in the order they were created, processing a maximum of 50 queued operations per replay cycle
7. IF a queued operation fails after all retry attempts are exhausted, THEN THE Offline_Queue SHALL mark the operation as failed, notify the user with an in-app indication on the affected item, and provide an option to retry or discard the failed operation
8. WHILE the device has no network connectivity, THE Offline_Queue SHALL accept and persist a maximum of 200 pending operations, and IF the queue limit is reached, THEN THE App SHALL inform the user that new write operations cannot be queued until connectivity is restored

### Requirement 11: Network Call Optimization

**User Story:** As a developer, I want to minimize redundant network calls, so that the app is faster and uses less data.

#### Acceptance Criteria

1. THE App SHALL eliminate duplicate API calls triggered by Compose recomposition by ensuring network requests are initiated only from ViewModel init blocks or explicit user actions (tap, pull-to-refresh, or navigation events)
2. THE App SHALL implement the Network_Bound_Resource pattern for all list-fetching operations (conversations, messages, group members): serve Room cache immediately, fetch from network in background, and update Room on success
3. IF a network fetch in the Network_Bound_Resource pattern fails, THEN THE App SHALL continue serving the existing Room cache and expose the error state to the UI without discarding cached data; WHEN a network fetch succeeds, THE App SHALL replace the cached data with the fresh response data
4. WHEN fetching data that supports ETag headers, THE App SHALL send If-None-Match headers and handle 304 Not Modified responses by serving cached data without overwriting the local store; THE App SHALL also handle 304 responses gracefully even if If-None-Match headers were not sent due to edge cases
5. THE App SHALL cache user profile data and group metadata in Room with a staleness threshold of 5 minutes before re-fetching
6. IF a re-fetch of stale cached data fails, THEN THE App SHALL continue serving the stale cached data and retry on the next access or after a delay of no more than 60 seconds
7. THE App SHALL limit the number of concurrently active Firestore snapshot listeners to no more than 10 by combining related document listeners into collection-level queries where multiple documents from the same collection are observed simultaneously

### Requirement 12: Notification Management

**User Story:** As a user, I want organized and actionable notifications, so that I can stay informed without being overwhelmed.

#### Acceptance Criteria

1. THE Notification_Helper SHALL be a singleton that centralizes all notification creation and channel management
2. WHEN the App process starts, THE Notification_Helper SHALL create the following notification channels: messages (high importance), social (default importance), location_updates (high importance), group_activity (default importance), general (default importance)
3. IF the app targets Android 13+ and POST_NOTIFICATIONS permission has not been granted, THEN THE Notification_Helper SHALL not post the notification and SHALL discard it silently without crashing
4. WHEN a notification is tapped, THE App SHALL navigate to the corresponding Screen via a deep-link PendingIntent using the following mapping: new_message → Chat screen for that conversationId, friend_request → FriendRequests screen, friend_accepted → UserProfile screen for that userId, member_joined or member_left → GroupDetails screen for that groupId, location_update → GroupMap screen for that groupId
5. THE Notification_Helper SHALL group message notifications by conversationId using NotificationCompat.Group and SHALL display a summary notification when 2 or more message notifications from distinct conversations are active
6. THE App SHALL persist user notification preferences as per-channel enable/disable flags in DataStore and SHALL check the preference for the target channel before posting; IF the channel is disabled in user preferences, THEN THE App SHALL suppress the notification without displaying it
7. IF a received FCM message contains an unrecognized type value, THEN THE Notification_Helper SHALL post the notification to the general channel using the provided title and body

### Requirement 13: Code Cleanup

**User Story:** As a developer, I want a clean codebase free of dead code and debug artifacts, so that the project is maintainable and the APK size is minimized.

#### Acceptance Criteria

1. THE App SHALL remove all Timber.d and Log.d debug log statements from production code, replacing those that guard error-recovery paths or track state transitions affecting user-visible behavior with Timber.i for informational flow or Timber.w for recoverable error conditions
2. THE App SHALL remove all unused variables, functions, imports, and parameters identified by Android Lint (unused resource and code inspections) and Kotlin IDE inspections with zero remaining warnings in those categories
3. THE App SHALL remove all commented-out code blocks, retaining only comments that explain why a design decision was made or reference an external issue tracker ID
4. THE App SHALL remove unused drawable, string, and layout resources as identified by the Android Lint "Unused Resources" inspection
5. THE App SHALL replace all magic numbers and string literals with named constants or resource references, excluding the values 0, 1, and -1 when used as index bounds or arithmetic identities, and excluding Compose dp/sp literals of 16 or fewer characters used inline in Modifier chains
6. THE App SHALL standardize naming conventions: camelCase for functions and properties, PascalCase for classes and composables, SCREAMING_SNAKE_CASE for constants
7. WHEN all cleanup changes are applied, THE App SHALL compile without errors and all existing unit and instrumentation tests SHALL pass with no new failures introduced

### Requirement 14: Production Readiness

**User Story:** As a developer, I want the app to be secure, performant, and ready for Play Store release, so that users have a reliable experience.

#### Acceptance Criteria

1. THE App SHALL enable R8 code shrinking and obfuscation for the release build type with ProGuard keep rules for Firebase (all com.google.firebase.** classes), Room (all @Entity and @Dao annotated classes), Hilt (all @HiltViewModel and @Module annotated classes), and Kotlin serialization (@Serializable annotated classes)
2. THE App SHALL define at minimum two build variants: debug (debuggable = true, applicationIdSuffix = ".debug", cleartext traffic allowed, Timber debug tree planted) and release (isMinifyEnabled = true, isShrinkResources = true, debuggable = false, signed with release keystore)
3. THE App SHALL store all API keys and secrets in local.properties or BuildConfig fields sourced from environment variables, and local.properties SHALL be listed in .gitignore so that secrets are not committed to version control
4. THE App SHALL disallow cleartext HTTP traffic in the release build via a network_security_config.xml that sets cleartextTrafficPermitted="false" for all domains
5. THE App SHALL contain zero calls to runBlocking on the main thread and SHALL prohibit all other forms of main thread blocking (synchronous network calls, heavy computation, or blocking I/O) in any code path reachable from Activity, Fragment, or Composable lifecycle
6. THE App SHALL use Coil with a memory cache size of 25% of available heap and a disk cache size of 50MB for all image loading, using AsyncImage composable for Compose screens
7. THE App SHALL include a baseline profile module that generates profiles for app startup and key user journeys (navigation to Map, Chats, and People screens)
8. IF an unhandled exception occurs AND Firebase Crashlytics is available and properly initialized, THEN THE App SHALL report the crash via Firebase Crashlytics with the current user ID (anonymized) and the active screen route name attached as custom keys; IF Crashlytics is unavailable or fails to initialize, crash reporting is not required
9. THE App SHALL use the App Startup library to initialize Firebase, Timber, and Coil via separate Initializer implementations that declare their dependencies, ensuring deterministic initialization order without blocking the main thread
10. THE App SHALL set android:allowBackup="false" in AndroidManifest.xml to prevent unencrypted backup of user data
