# Implementation Plan: Messenger-Style Search

## Overview

This plan converts the design into incremental coding tasks that:
1. Establish the data layer (Room entity/DAO, DataStore-backed recent searches store, interaction repository).
2. Build the domain layer (interaction repository interface, suggestions use case, search use cases).
3. Create the shared `WhereSearchBar` Jetpack Compose component with animations, recent-searches chips, suggestions row, and inline results host.
4. Wire the bar into the `PeopleScreen` and `ChatsScreen` ViewModels with debounced search.
5. Remove the `TopAppBar` from `ProfileScreen`, `PeopleScreen`, and `ChatsScreen`.
6. Hook interaction recording into the chat send and profile view code paths.

Property-based tests use Kotest Property Testing (`kotest-property`).

## Tasks

- [ ] 1. Set up package structure and shared types
  - [ ] 1.1 Create `presentation/common/search/` package with `SuggestionUiModel.kt` (data class: `userId`, `displayName`, `photoUrl`, `isOnline`) and `SearchUiState.kt` (data class with `query`, `isFocused`, `isLoading`, `recentSearches`, `suggestions`, `searchResults`, `showEmptyState`)
    - Define presentation-layer models used by both People and Chats search flows
    - _Requirements: 4.5, 4.6, 7.1, 8.1_
  - [ ] 1.2 Create `domain/model/Interaction.kt` with `Interaction` data class and `InteractionType` enum (`MESSAGE_SENT`, `PROFILE_VIEWED`)
    - _Requirements: 8.3, 8.4_

- [ ] 2. Implement Recent Searches persistence layer
  - [ ] 2.1 Create `data/local/prefs/RecentSearchesStore.kt` as a `@Singleton` class injecting `DataStore<Preferences>`
    - Use two `stringPreferencesKey` entries (`recent_searches_people`, `recent_searches_chats`) storing JSON-encoded `List<String>`
    - Implement `getRecentSearches(screenKey: String): Flow<List<String>>`, `addSearch`, `removeSearch`, `clearAll`
    - Enforce `MAX_ENTRIES = 15` with FIFO eviction inside `addSearch`
    - Handle malformed JSON by clearing the corrupted entry and returning an empty list (log via Timber)
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 6.7_
  - [ ] 2.2 Write property tests for `RecentSearchesStore` in `app/src/test/.../RecentSearchesStorePropertyTest.kt` using Kotest Property Testing (min 100 iterations) backed by an in-memory `DataStore<Preferences>`
    - **Property 5: Recent searches persistence round-trip** — Validates: Requirements 6.1, 6.7
    - **Property 6: Recent searches max capacity with FIFO eviction** — Validates: Requirements 6.2, 6.3
    - **Property 7: Single entry deletion preserves others** — Validates: Requirements 6.4
    - **Property 8: Clear all results in empty list** — Validates: Requirements 6.5
    - **Property 9: Separate histories per screen** — Validates: Requirements 6.6
    - Tag tests with `Feature: messenger-style-search, Property {number}: {property_text}`
  - [ ] 2.3 Add `RecentSearchesStore` provision to a Hilt module under `di/` (reuse the existing `DataStore<Preferences>` provider used by `UserPreferences`)
    - _Requirements: 6.1_

- [ ] 3. Implement Interaction data layer (Room)
  - [ ] 3.1 Create `data/local/entity/InteractionEntity.kt` with `@Entity(tableName = "interactions")` matching the design (`id`, `userId`, `displayName`, `photoUrl`, `type`, `timestamp`)
    - _Requirements: 8.2, 8.3, 8.4_
  - [ ] 3.2 Create `data/local/dao/InteractionDao.kt` with `upsert(entity)`, `getRecent(limit: Int): Flow<List<InteractionEntity>>` ordered by `timestamp DESC`, and `clearAll()`
    - _Requirements: 8.2, 8.5_
  - [ ] 3.3 Register `InteractionEntity` and `InteractionDao` on the existing Room database in `data/local/db/`, bumping the schema version and adding a migration that creates the `interactions` table
    - _Requirements: 8.3, 8.4_
  - [ ] 3.4 Create `domain/repository/InteractionRepository.kt` interface (`getRecentInteractions(limit): Flow<List<Interaction>>`, `recordInteraction(userId, displayName, photoUrl, type)`)
    - _Requirements: 8.2, 8.3, 8.4_
  - [ ] 3.5 Create `data/repository/InteractionRepositoryImpl.kt` implementing the interface, mapping entity ↔ domain, with `recordInteraction` writing via `upsert` (id = `"{userId}_{type}"`)
    - On DAO read failure return empty flow and log via Timber
    - _Requirements: 8.2, 8.3, 8.4_
  - [ ] 3.6 Bind `InteractionRepository` in the Hilt repository module
    - _Requirements: 8.3, 8.4_

- [ ] 4. Implement Suggestions use case
  - [ ] 4.1 Create `domain/usecase/GetSuggestionsUseCase.kt` invoking `interactionRepository.getRecentInteractions(limit)` and mapping each `Interaction` to `SuggestionUiModel`, capping at `limit` (default 15)
    - Resolve `isOnline` from the existing presence/online source if available, otherwise default to `false`
    - Order is preserved from the repository (timestamp DESC)
    - _Requirements: 8.2, 8.3, 8.4, 8.5, 8.9_
  - [ ] 4.2 Write property tests for `GetSuggestionsUseCase` using Kotest Property Testing with a fake `InteractionRepository` (min 100 iterations)
    - **Property 10: Suggestions include recently interacted users** — Validates: Requirements 8.3, 8.4
    - **Property 11: Suggestions ordered by recency** — Validates: Requirements 8.2
    - **Property 12: Suggestions max 15 invariant** — Validates: Requirements 8.5
    - Tag tests with `Feature: messenger-style-search, Property {number}: {property_text}`

- [ ] 5. Checkpoint - data and domain layers
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 6. Implement search filtering use cases
  - [ ] 6.1 Create `domain/usecase/SearchPeopleUseCase.kt` accepting `query: String` and a list (or flow) of friends, returning users where `query` is a case-insensitive substring of `displayName` or `username`
    - Empty/whitespace-only queries return an empty list
    - _Requirements: 5.3, 9.1_
  - [ ] 6.2 Create `domain/usecase/SearchChatsUseCase.kt` accepting `query: String` and a list (or flow) of conversations, returning conversations where `query` is a case-insensitive substring of the title or last message text
    - Empty/whitespace-only queries return an empty list
    - _Requirements: 5.4, 9.2_
  - [ ] 6.3 Write property test for `SearchPeopleUseCase` (Kotest Property Testing, min 100 iterations)
    - **Property 3: People search returns only matching users** — Validates: Requirements 5.3, 9.1
    - Tag with `Feature: messenger-style-search, Property 3: People search returns only matching users`
  - [ ] 6.4 Write property test for `SearchChatsUseCase` (Kotest Property Testing, min 100 iterations)
    - **Property 4: Chats search returns only matching conversations** — Validates: Requirements 5.4, 9.2
    - Tag with `Feature: messenger-style-search, Property 4: Chats search returns only matching conversations`

- [ ] 7. Build the shared `WhereSearchBar` composable
  - [ ] 7.1 Create `presentation/common/search/WhereSearchBar.kt` with the public composable signature from the design (query, callbacks, placeholder, focus, loading, recent searches, suggestions)
    - Render a rounded pill (`Dimens.cornerRound`) with `surfaceContainerHigh` background, leading search icon (`onSurfaceVariant`), placeholder (`onSurfaceVariant` reduced opacity)
    - Animate elevation and background color on focus change with a 200ms ease-in-out tween (`cardElevationSubtle` when focused)
    - Use Material 3 color tokens so light/dark themes work without overrides
    - _Requirements: 4.1, 4.2, 4.5, 4.6, 4.7, 4.8, 4.9_
  - [ ] 7.2 Add the animated trailing clear button: fade-in/scale when `query` is non-empty, invokes `onClearQuery` on tap
    - _Requirements: 4.3_
  - [ ] 7.3 Add the slim linear progress indicator (`primary` color) below the pill when `isLoading` is true
    - _Requirements: 4.10_
  - [ ] 7.4 Implement spring-based expansion animation for the dropdown content (recent searches + suggestions) revealed when focused
    - _Requirements: 4.4_
  - [ ] 7.5 Implement the recent searches section: horizontally-scrollable Material 3 filter chips with `surfaceContainerHigh` backgrounds, "Clear all" trailing text button, hidden entirely when the list is empty
    - Each chip shows search text + delete icon button; deletion plays a shrink-and-fade animation
    - Tap a chip → invoke `onRecentSearchTapped(query)`
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6_
  - [ ] 7.6 Implement the suggestions row: horizontal `LazyRow` of circular avatars (`Dimens.avatarSizeMedium`) with names below, online dot using `MaterialTheme.colorScheme.tertiary`
    - Stagger-animate items in (scale-up + fade-in, 50ms delay between items) on first appearance
    - Hide the row entirely when the suggestions list is empty
    - _Requirements: 8.1, 8.9, 8.10_
  - [ ] 7.7 Implement the inline-results / empty-state container slot in `WhereSearchBar` (or expose a content slot) so callers can render their results list, with crossfade between (recent + suggestions) and (results / empty state) based on whether `query` is non-empty
    - Empty-state: centered icon + "no results" message
    - _Requirements: 9.3, 9.4_
  - [ ] 7.8 Write Compose UI tests in `app/src/androidTest/.../WhereSearchBarTest.kt`
    - Clear button toggles with text presence, tap invokes `onClearQuery`
    - Focus toggles the expanded content
    - Empty/whitespace-only submission is ignored
    - _Requirements: 4.3, 4.4, 7.5_
  - [ ] 7.9 Write a property test for the clear-button reset behavior using Kotest Property Testing (min 100 iterations) over arbitrary non-empty strings
    - **Property 1: Clear button resets text to empty** — Validates: Requirements 4.3
    - Tag with `Feature: messenger-style-search, Property 1: Clear button resets text to empty`

- [ ] 8. Checkpoint - UI component complete
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 9. Wire `WhereSearchBar` into People screen
  - [ ] 9.1 Update `presentation/people/PeopleViewModel.kt` to expose a `SearchUiState` `StateFlow`, a `query: MutableStateFlow<String>` debounced with `kotlinx.coroutines.flow.debounce(300)` invoking `SearchPeopleUseCase`, and surface `recentSearches` from `RecentSearchesStore.getRecentSearches("people")` and `suggestions` from `GetSuggestionsUseCase`
    - Empty/whitespace-only queries cancel pending debounced searches and do not persist
    - On submission persist the trimmed query via `RecentSearchesStore.addSearch("people", query)`
    - Provide actions: `onQueryChanged`, `onQuerySubmitted`, `onClearQuery`, `onFocusChanged`, `onRecentSearchTapped`, `onRecentSearchDeleted`, `onClearAllRecentSearches`, `onSuggestionTapped`
    - _Requirements: 5.1, 5.2, 5.3, 6.1, 6.4, 6.5, 7.4, 8.6_
  - [ ] 9.2 Update `presentation/people/PeopleScreen.kt` to remove the `TopAppBar` / `Scaffold` top bar, render `WhereSearchBar` as the first content element with `placeholderText = "Search people..."`, and keep the existing friend list, pull-to-refresh, and friend request inbox card below the bar
    - Apply system status bar inset padding so content does not render behind the status bar
    - _Requirements: 2.1, 2.2, 2.3, 2.4_
  - [ ] 9.3 Render inline people search results in `PeopleScreen` (replacing recent + suggestions when `query` is non-empty) with fade-in + slide-up item animation, and an empty-state when results are zero; tapping a suggestion avatar navigates to that user's profile
    - Reuse the existing friend list row styling
    - _Requirements: 8.6, 9.1, 9.3, 9.5_
  - [ ] 9.4 Write a property test in `app/src/test/.../PeopleSearchDebouncePropertyTest.kt` using Kotest Property Testing + `kotlinx-coroutines-test` `TestScope` with virtual time to verify debounce semantics on `PeopleViewModel`'s query flow (min 100 iterations)
    - **Property 2: Debounce emits only final value** — Validates: Requirements 5.1
    - Tag with `Feature: messenger-style-search, Property 2: Debounce emits only final value`

- [ ] 10. Wire `WhereSearchBar` into Chats screen
  - [ ] 10.1 Update `presentation/chat/ChatsViewModel.kt` symmetrically to `PeopleViewModel`: `SearchUiState`, debounced query → `SearchChatsUseCase`, `RecentSearchesStore.getRecentSearches("chats")`, `GetSuggestionsUseCase`, and the same action set
    - Empty/whitespace-only queries cancel pending searches and are not persisted
    - On submission persist via `RecentSearchesStore.addSearch("chats", query)`
    - _Requirements: 5.1, 5.2, 5.4, 6.1, 6.4, 6.5, 7.4, 8.7_
  - [ ] 10.2 Update `presentation/chat/ChatsScreen.kt` to remove the `TopAppBar`, render `WhereSearchBar` with `placeholderText = "Search chats..."` as the first element, and keep the conversation list, pull-to-refresh, FAB, and swipe actions below
    - Apply system status bar inset padding
    - _Requirements: 3.1, 3.2, 3.3, 3.4_
  - [ ] 10.3 Render inline chat search results in `ChatsScreen` with fade-in + slide-up animation and an empty-state when zero results; tapping a suggestion avatar navigates to the conversation with that user
    - Reuse the existing conversation row styling
    - _Requirements: 8.7, 9.2, 9.3, 9.5_

- [ ] 11. Remove top app bar from Profile screen
  - [ ] 11.1 Update `presentation/profile/ProfileScreen.kt` to remove its `TopAppBar` / `Scaffold` top bar while keeping the Instagram-style avatar, stats, action buttons, and shortcuts
    - Apply system status bar inset padding so content does not render behind the status bar
    - _Requirements: 1.1, 1.2, 1.3_

- [ ] 12. Hook interaction recording into existing flows
  - [ ] 12.1 In the chat send code path (e.g., `ChatViewModel` send action), call `InteractionRepository.recordInteraction(userId, displayName, photoUrl, InteractionType.MESSAGE_SENT)` after a successful send
    - _Requirements: 8.3_
  - [ ] 12.2 In the profile view code path (e.g., `UserProfileViewModel` `init` / load), call `InteractionRepository.recordInteraction(userId, displayName, photoUrl, InteractionType.PROFILE_VIEWED)` once per view
    - _Requirements: 8.4_
  - [ ] 12.3 Write unit tests verifying both call sites invoke `recordInteraction` with the correct `InteractionType` using a fake `InteractionRepository`
    - _Requirements: 8.3, 8.4_

- [ ] 13. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for a faster MVP.
- Each task references granular requirement clauses for traceability.
- Property tests use Kotest Property Testing (`kotest-property`) with a minimum of 100 iterations and the tag format `Feature: messenger-style-search, Property {number}: {property_text}`.
- The Map tab is intentionally untouched (no tasks for it).
- Animations, theming, and accessibility are validated via Compose UI tests and manual QA, not via PBT (per design "What Is NOT Tested via PBT").

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2", "3.1"] },
    { "id": 1, "tasks": ["2.1", "3.2", "3.4", "6.1", "6.2"] },
    { "id": 2, "tasks": ["2.2", "2.3", "3.3", "3.5", "6.3", "6.4", "7.1"] },
    { "id": 3, "tasks": ["3.6", "4.1", "7.2", "7.3", "7.4"] },
    { "id": 4, "tasks": ["4.2", "7.5", "7.6", "7.7"] },
    { "id": 5, "tasks": ["7.8", "7.9", "9.1", "10.1", "11.1"] },
    { "id": 6, "tasks": ["9.2", "10.2"] },
    { "id": 7, "tasks": ["9.3", "9.4", "10.3", "12.1", "12.2"] },
    { "id": 8, "tasks": ["12.3"] }
  ]
}
```
