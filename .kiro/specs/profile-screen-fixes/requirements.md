# Requirements Document

## Introduction

This specification addresses three issues with the Profile Screen in the Where Android app: (1) the profile UI does not auto-update after editing because the data flow relies on `AuthStateListener` which only fires on sign-in/sign-out events, (2) the profile screen layout needs a more polished, professional redesign, and (3) the quick action buttons are non-functional due to empty onClick handlers.

## Glossary

- **Profile_Screen**: The Jetpack Compose screen (`ProfileScreen.kt`) that displays the current user's profile information, stats, and quick actions.
- **Auth_Repository**: The data layer component (`AuthRepositoryImpl`) responsible for providing the current user data as a reactive Flow.
- **Firestore_Snapshot_Listener**: A Firebase Firestore real-time listener (`addSnapshotListener`) that emits document changes as they occur.
- **Auth_State_Listener**: A Firebase Auth listener (`AuthStateListener`) that fires only on sign-in and sign-out events.
- **Profile_ViewModel**: The ViewModel (`ProfileViewModel`) that observes the current user flow and exposes UI state to the Profile_Screen.
- **Edit_Profile_ViewModel**: The ViewModel (`EditProfileViewModel`) that handles profile editing and saves changes to Firestore.
- **Quick_Action_Row**: A clickable row component in the Profile_Screen that provides shortcuts to Messages, Groups, and Location Sharing screens.
- **Bottom_Navigation**: The main scaffold bottom tab bar that switches between the app's primary sections (Messages, Groups, Map, People, Profile).

## Requirements

### Requirement 1: Real-Time Profile Data Synchronization

**User Story:** As a user, I want my profile screen to immediately reflect changes after I edit my profile, so that I do not have to restart the app to see updated information.

#### Acceptance Criteria

1. WHEN the user document in Firestore is updated, THE Auth_Repository SHALL emit the updated User object through the currentUser flow within the lifetime of the active listener.
2. WHEN the Edit_Profile_ViewModel saves profile changes to Firestore, THE Profile_Screen SHALL display the updated display name, username, bio, and photo without requiring app restart or manual refresh.
3. THE Auth_Repository SHALL use a Firestore_Snapshot_Listener on the authenticated user's document to observe real-time changes.
4. WHILE the user is authenticated, THE Auth_Repository SHALL maintain an active Firestore_Snapshot_Listener that emits on every document change.
5. IF the Firestore_Snapshot_Listener encounters an error, THEN THE Auth_Repository SHALL fall back to Firebase Auth data and log the error.
6. WHEN the user signs out, THE Auth_Repository SHALL remove the Firestore_Snapshot_Listener and emit null through the currentUser flow.

### Requirement 2: Professional Profile Screen Redesign

**User Story:** As a user, I want a polished and modern profile screen layout, so that the app feels professional and visually appealing.

#### Acceptance Criteria

1. THE Profile_Screen SHALL display the user's avatar with an animated gradient ring indicator at the top of the screen.
2. THE Profile_Screen SHALL display the user's display name, username (prefixed with @), and bio in a centered header section.
3. THE Profile_Screen SHALL display statistics (Groups count, Friends count, Shared Locations count) in a visually distinct row with icons and labels.
4. THE Profile_Screen SHALL provide an "Edit Profile" primary action button and a "Settings" secondary action button in a horizontal row.
5. THE Profile_Screen SHALL present quick action items (Messages, Groups, Location Sharing) in a card-style container with dividers between items.
6. THE Profile_Screen SHALL apply a fade-in entrance animation when the screen first appears.
7. THE Profile_Screen SHALL use Material 3 theming consistently for colors, typography, and shapes.

### Requirement 3: Functional Quick Action Navigation

**User Story:** As a user, I want the quick action buttons on my profile screen to navigate to the correct screens, so that I can quickly access Messages, Groups, and Location Sharing features.

#### Acceptance Criteria

1. WHEN the user taps the "Messages" quick action, THE Profile_Screen SHALL trigger navigation to the Messages tab in the Bottom_Navigation.
2. WHEN the user taps the "Groups" quick action, THE Profile_Screen SHALL trigger navigation to the Groups tab in the Bottom_Navigation.
3. WHEN the user taps the "Location Sharing" quick action, THE Profile_Screen SHALL trigger navigation to the Map/Location tab in the Bottom_Navigation.
4. THE Profile_Screen SHALL accept navigation callback parameters for each quick action from the parent composable.
5. THE Quick_Action_Row composable SHALL invoke the provided onClick callback when tapped.
