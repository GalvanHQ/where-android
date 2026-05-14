# Requirements Document

## Introduction

This feature removes the Material TopAppBar from the three main tab screens (Profile, People, Chats) and replaces the search functionality on People and Chats with a Messenger-style always-visible search bar. The search bar displays recent searches (persisted via DataStore) and suggestions based on recent interactions. The Map tab already has no top bar and remains unchanged.

## Glossary

- **Search_Bar**: A shared Jetpack Compose composable rendered as a rounded pill-shaped text field with a search icon, placeholder text, and clear button. Used at the top of People and Chats screens.
- **Recent_Searches_Store**: A DataStore-backed persistence layer that stores the user's recent search queries as a list of strings, limited to a configurable maximum count.
- **Suggestions_Engine**: A component that provides a list of users the current user recently interacted with (messaged or viewed profile), ordered by recency.
- **People_Screen**: The tab screen displaying the user's friend list, friend requests, and search functionality for people.
- **Chats_Screen**: The tab screen displaying the user's conversations and search functionality for chats.
- **Profile_Screen**: The tab screen displaying the current user's profile information, stats, and shortcuts.
- **Interaction**: An event where the current user either sent a message to another user or viewed another user's profile.
- **Debounce_Interval**: A 300-millisecond delay applied after the last keystroke before executing a search query.

## Requirements

### Requirement 1: Remove Top App Bar from Profile Screen

**User Story:** As a user, I want the Profile screen to have more vertical space for content, so that I can see my profile information without a redundant title bar.

#### Acceptance Criteria

1. THE Profile_Screen SHALL render without a Material TopAppBar or Scaffold top bar.
2. THE Profile_Screen SHALL retain the existing Instagram-style layout including avatar, stats, action buttons, and shortcuts.
3. THE Profile_Screen SHALL apply the system status bar insets as top padding to prevent content from rendering behind the status bar.

### Requirement 2: Remove Top App Bar from People Screen

**User Story:** As a user, I want the People screen to use a Messenger-style search bar instead of a traditional top app bar, so that search is always accessible without extra taps.

#### Acceptance Criteria

1. THE People_Screen SHALL render without a Material TopAppBar.
2. THE People_Screen SHALL display the Search_Bar as the first element at the top of the screen content.
3. THE People_Screen SHALL display the placeholder text "Search people..." inside the Search_Bar when no text is entered.
4. THE People_Screen SHALL retain the existing friend list, pull-to-refresh, and friend request inbox card below the Search_Bar.

### Requirement 3: Remove Top App Bar from Chats Screen

**User Story:** As a user, I want the Chats screen to use a Messenger-style search bar instead of a traditional top app bar, so that search is always accessible without extra taps.

#### Acceptance Criteria

1. THE Chats_Screen SHALL render without a Material TopAppBar.
2. THE Chats_Screen SHALL display the Search_Bar as the first element at the top of the screen content.
3. THE Chats_Screen SHALL display the placeholder text "Search chats..." inside the Search_Bar when no text is entered.
4. THE Chats_Screen SHALL retain the existing conversation list, pull-to-refresh, FAB, and swipe actions below the Search_Bar.

### Requirement 4: Universal Search Bar Component — Premium UI/UX

**User Story:** As a user, I want a polished, premium-feeling search experience, so that the app feels modern and delightful to use.

#### Acceptance Criteria

1. THE Search_Bar SHALL render as a rounded pill shape using the existing `Dimens.cornerRound` radius with a subtle surface-tinted background (`surfaceContainerHigh`) to create depth.
2. THE Search_Bar SHALL display a leading search icon tinted with `onSurfaceVariant` and configurable placeholder text in `onSurfaceVariant` with reduced opacity.
3. WHEN the user enters text, THE Search_Bar SHALL display an animated trailing clear button (fade-in/scale) that resets the text field to empty on tap.
4. WHEN the user taps the Search_Bar, THE Search_Bar SHALL smoothly animate its expansion (height and content reveal) to show the recent searches section and suggestions section below it using a spring-based animation.
5. THE Search_Bar SHALL accept a `placeholderText` parameter to allow different placeholder text per screen.
6. THE Search_Bar SHALL accept callback parameters for `onQueryChanged`, `onQuerySubmitted`, and `onClearQuery`.
7. THE Search_Bar SHALL apply a subtle elevation shadow (`cardElevationSubtle`) when focused to visually lift it from the background.
8. WHEN the Search_Bar transitions between focused and unfocused states, THE Search_Bar SHALL animate the background color and elevation changes with a 200ms ease-in-out transition.
9. THE Search_Bar SHALL use the app's Material 3 color tokens consistently so it adapts to light and dark themes seamlessly.
10. WHEN search results are loading, THE Search_Bar SHALL display a slim animated progress indicator (linear, primary color) below the pill shape.

### Requirement 5: Debounced Search Execution

**User Story:** As a user, I want search results to appear automatically as I type without excessive network calls, so that the experience feels responsive and efficient.

#### Acceptance Criteria

1. WHEN the user types in the Search_Bar, THE Search_Bar SHALL wait for the Debounce_Interval (300ms) after the last keystroke before invoking the search callback.
2. WHEN the user clears the search text, THE Search_Bar SHALL cancel any pending debounced search invocation.
3. WHEN the debounced search executes on the People_Screen, THE People_Screen SHALL display matching people results inline, replacing the suggestions section.
4. WHEN the debounced search executes on the Chats_Screen, THE Chats_Screen SHALL filter and display matching conversations inline, replacing the suggestions section.

### Requirement 6: Recent Searches Persistence

**User Story:** As a user, I want my recent searches to be saved across app restarts, so that I can quickly repeat previous searches.

#### Acceptance Criteria

1. WHEN the user submits a search query, THE Recent_Searches_Store SHALL persist the query string to DataStore.
2. THE Recent_Searches_Store SHALL store a maximum of 15 recent search entries.
3. WHEN the stored entries exceed 15, THE Recent_Searches_Store SHALL remove the oldest entry before adding the new one.
4. WHEN the user taps the delete button on a recent search entry, THE Recent_Searches_Store SHALL remove that single entry from storage.
5. WHEN the user taps "Clear all", THE Recent_Searches_Store SHALL remove all stored entries.
6. THE Recent_Searches_Store SHALL maintain separate search histories for People_Screen and Chats_Screen.
7. WHEN the app restarts, THE Recent_Searches_Store SHALL restore previously saved entries and display them when the Search_Bar is focused.

### Requirement 7: Recent Searches Display

**User Story:** As a user, I want to see my recent searches when I focus the search bar, so that I can quickly repeat a previous search.

#### Acceptance Criteria

1. WHEN the Search_Bar receives focus and no text is entered, THE Search_Bar SHALL display recent searches as horizontally-scrollable Material 3 filter chips with subtle surface-tinted backgrounds.
2. Each recent search chip SHALL display the search text and a delete (X) icon button with a fade-out removal animation when dismissed.
3. WHEN the list of recent searches is non-empty, THE Search_Bar SHALL display a "Clear all" text button aligned to the trailing edge of the section header.
4. WHEN the user taps a recent search chip, THE Search_Bar SHALL populate the text field with that entry's text and trigger a search.
5. WHEN there are no recent searches stored, THE Search_Bar SHALL hide the recent searches section entirely without leaving empty space.
6. WHEN a recent search entry is removed, THE Search_Bar SHALL animate the chip out with a shrink-and-fade transition.

### Requirement 8: Suggestions Based on Recent Interactions

**User Story:** As a user, I want to see people I recently interacted with as suggestions, so that I can quickly find and contact them.

#### Acceptance Criteria

1. WHEN the Search_Bar receives focus and no text is entered, THE Suggestions_Engine SHALL display a horizontal scrollable row of circular user avatars with names below, using the existing `Dimens.avatarSizeMedium` size.
2. THE Suggestions_Engine SHALL order suggestions by most recent interaction first.
3. THE Suggestions_Engine SHALL include users the current user recently messaged.
4. THE Suggestions_Engine SHALL include users whose profiles the current user recently viewed.
5. THE Suggestions_Engine SHALL display a maximum of 15 suggestion entries.
6. WHEN the user taps a suggestion avatar on the People_Screen, THE People_Screen SHALL navigate to that user's profile.
7. WHEN the user taps a suggestion avatar on the Chats_Screen, THE Chats_Screen SHALL navigate to the conversation with that user.
8. WHEN the user enters text in the Search_Bar, THE Suggestions_Engine SHALL hide the suggestions row and show search results instead.
9. Each suggestion avatar SHALL display an online status indicator dot (using `MaterialTheme.colorScheme.tertiary`) when the user is currently online.
10. WHEN the suggestions row first appears, THE Suggestions_Engine SHALL stagger-animate each avatar into view with a scale-up and fade-in effect (50ms delay between items).

### Requirement 9: Inline Search Results

**User Story:** As a user, I want search results to appear inline below the search bar, so that I can find people or chats without navigating to a separate screen.

#### Acceptance Criteria

1. WHEN a search query produces results on the People_Screen, THE People_Screen SHALL display matching users in a vertical list below the Search_Bar with animated item appearance (fade-in + slide-up), replacing the suggestions and recent searches sections.
2. WHEN a search query produces results on the Chats_Screen, THE Chats_Screen SHALL display matching conversations in a vertical list below the Search_Bar with animated item appearance (fade-in + slide-up), replacing the suggestions and recent searches sections.
3. WHEN a search query produces zero results, THE Search_Bar SHALL display a centered empty state with an icon and message indicating no results were found.
4. WHEN the user clears the search text, THE Search_Bar SHALL restore the recent searches and suggestions sections with a crossfade transition.
5. THE inline search results SHALL use the same row styling as the existing friend list (People_Screen) and conversation list (Chats_Screen) to maintain visual consistency.
