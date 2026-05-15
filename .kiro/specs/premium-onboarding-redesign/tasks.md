# Implementation Plan: Premium Onboarding Redesign

## Overview

Replace the existing simple icon-in-circle onboarding with a premium, visually rich three-screen experience using stacked card layouts, social proof user lists, and floating icon grids. The implementation uses Jetpack Compose with the existing `OnboardingScreen` route and `OnboardingViewModel` contract preserved.

## Tasks

- [x] 1. Set up data models, dimension constants, and string resources
  - [x] 1.1 Create onboarding data models and static content definitions
    - Create `OnboardingPageData` data class with `heroType`, `headlineRes`, `subtitleRes` fields
    - Create `HeroType` enum with `STACKED_CARDS`, `SOCIAL_PROOF`, `CATEGORY_GRID`
    - Create `ProfileCardData` data class with `displayName`, `subtitle`, `avatarContentDescription`
    - Create `CategoryIconData` data class with `icon`, `label`, `sizeDp`
    - Define `onboardingPages`, `socialProofProfiles`, and `categoryIcons` static lists
    - _Requirements: 2.4, 2.5, 3.5, 3.6, 4.5, 4.6_

  - [x] 1.2 Add dimension constants to the Dimens object
    - Add `cardElevation = 2.dp`, `cardElevationHigh = 6.dp`
    - Add `cornerMedium = 16.dp`, `cornerSmall = 12.dp`, `cornerLarge = 24.dp`
    - Add `buttonHeight = 56.dp`, `logoSize = 64.dp`
    - Add `indicatorIdle = 8.dp`, `indicatorActive = 24.dp`
    - Add `placeholderMinSize = 120.dp`, `placeholderIconSize = 48.dp`
    - _Requirements: 7.2, 6.3, 5.2, 8.1_

  - [x] 1.3 Add string resources for onboarding headlines and subtitles
    - Add `onboarding_headline_1` = "Share Your Location"
    - Add `onboarding_subtitle_1` = "Let friends and family know where you are in real time — safely and privately."
    - Add `onboarding_headline_2` = "Create or Join Groups"
    - Add `onboarding_subtitle_2` = "Create groups for family, friends, or events. Invite anyone with a simple 8-character code."
    - Add `onboarding_headline_3` = "Stay Connected"
    - Add `onboarding_subtitle_3` = "See everyone's location on a live map, get ETAs, and feel closer to the people you care about."
    - Add button labels: `onboarding_next` = "Next", `onboarding_get_started` = "Get Started", `onboarding_skip` = "Skip"
    - _Requirements: 2.4, 2.5, 3.5, 3.6, 4.5, 4.6, 6.1, 6.2, 6.6_

- [x] 2. Implement reusable shared components
  - [x] 2.1 Implement `ImagePlaceholder` composable
    - Create a rounded container (16dp corners) filled with `surfaceVariant`
    - Minimum size 120dp × 120dp with centered `Icons.Default.Image` at 48dp in `onSurfaceVariant`
    - Add centered label below the icon indicating expected image type
    - Accept `contentDescription` parameter (non-empty, max 80 chars) for accessibility
    - Ensure minimum touch target of 48dp × 48dp
    - _Requirements: 8.1, 8.2, 8.3, 8.4_

  - [x] 2.2 Implement `AppLogoHeader` composable
    - Render `ic_launcher_foreground` at 64×64dp tinted with primary brand color
    - Center horizontally within the top area of the screen
    - Implement fallback: if drawable fails to load, display a 64×64dp Box filled with primary color
    - _Requirements: 1.1, 1.2, 1.3, 1.4_

  - [x] 2.3 Implement `PageIndicatorRow` composable
    - Render 3 dots, one per page
    - Active dot: 24dp wide × 8dp pill shape using `primary` color with `animateDpAsState` and `tween(300)`
    - Idle dots: 8dp × 8dp circle using `outlineVariant` color
    - Respect `LocalReducedMotion` — use `snap()` when reduced motion is enabled
    - Add accessibility content description: "Page X of Y"
    - _Requirements: 5.1, 5.2, 5.3, 5.4_

  - [x] 2.4 Implement `OnboardingActionButton` composable
    - Full-width pill button with `cornerLarge = 24dp` and height 56dp
    - When page < last: display "Next" with `primaryContainer` background and `onPrimaryContainer` text
    - When page == last: display "Get Started" with `primary` background and `onPrimary` text
    - _Requirements: 6.1, 6.2, 6.3_

  - [x] 2.5 Write property tests for PageIndicatorRow and ActionButton
    - **Property 5: Page indicator state correctness** — Generate page counts (1..10) and current indices, verify exactly one active indicator at correct position, verify accessibility description contains correct values
    - **Property 6: Action button state correctness** — Generate page indices and page counts, verify button text and background color match requirements
    - **Validates: Requirements 5.1, 5.2, 5.4, 6.1, 6.2**

- [x] 3. Implement Screen 1 — Stacked Card Hero
  - [x] 3.1 Implement `StackedCardHero` composable
    - Render 2 overlapping image cards with `RoundedCornerShape(24.dp)` corners
    - Back card: offset 12dp horizontal, 8dp vertical, rotated 3–6 degrees, elevation 2dp
    - Front card: elevation 6dp, no rotation
    - Add overlay badge on top card: 24dp × 24dp minimum, `primaryContainer` background, positioned at top-end
    - Each card contains an `ImagePlaceholder` with content description for lifestyle/location photo
    - _Requirements: 2.1, 2.2, 2.3, 7.2, 7.3_

  - [x] 3.2 Write property test for content description validity in StackedCardHero
    - **Property 1: Content description validity** — Generate random strings, verify all placeholder content descriptions are non-empty and ≤80 characters
    - **Validates: Requirements 2.3, 8.2**

- [x] 4. Implement Screen 2 — Social Proof Hero
  - [x] 4.1 Implement `ProfileCard` composable
    - Horizontal row: circular avatar `ImagePlaceholder` (48dp diameter), display name in `bodyLarge` (max 30 chars, ellipsis overflow), subtitle in `bodyMedium`, decorative "Follow" button at end
    - Follow button: filled primary background, `onPrimary` text, `labelLarge` typography, rounded 12dp, non-interactive (enabled = false or clickable removed)
    - _Requirements: 3.2, 3.3, 3.4_

  - [x] 4.2 Implement `SocialProofHero` composable
    - Render 3–5 `ProfileCard` items vertically inside a `Surface` with 16dp rounded corners and elevation 2–6dp
    - Use `socialProofProfiles` static data (4 profiles)
    - Avatar placeholders include content description for developer guidance
    - _Requirements: 3.1, 3.4_

  - [x] 4.3 Write property tests for Social Proof section
    - **Property 2: Social proof card count invariant** — Generate lists of ProfileCardData with size 1..10, verify only 3..5 are accepted
    - **Property 3: Display name truncation** — Generate random strings of length 0..100, apply truncation logic, verify output correctness
    - **Validates: Requirements 3.1, 3.2**

- [x] 5. Checkpoint - Verify shared components and hero screens 1–2
  - Ensure all tests pass, ask the user if questions arise.

- [x] 6. Implement Screen 3 — Category Grid Hero
  - [x] 6.1 Implement `FloatingIconElement` composable
    - Circular container with diameter between 40dp and 56dp
    - Background: `surfaceVariant` color
    - Elevation: 2dp to 4dp for floating appearance
    - Render Material icon centered inside
    - Content description identifying the represented feature for accessibility
    - _Requirements: 4.2, 4.3, 4.7_

  - [x] 6.2 Implement `CategoryGridHero` composable
    - Render exactly 6 `FloatingIconElement` items in a scattered pattern around a central `ImagePlaceholder`
    - Central placeholder: 80–120dp diameter with content description for globe/world-map graphic
    - Use `categoryIcons` static data for the 6 icons (LocationOn, Group, Map, Chat, Notifications, Navigation)
    - Position icons using offset modifiers to create scattered/floating layout
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.7_

  - [x] 6.3 Write property test for Category Grid structure
    - **Property 4: Category grid structure invariant** — Generate lists of CategoryIconData with varying sizes, verify exactly 6 elements with container diameters between 40dp and 56dp
    - **Validates: Requirements 4.1, 4.2**

- [x] 7. Implement main OnboardingScreen with pager and navigation
  - [x] 7.1 Implement `OnboardingPageLayout` composable
    - Vertical column structure: `AppLogoHeader` → Hero section (delegated by `heroType`) → Headline (`headlineMedium`) → Subtitle (`bodyLarge`)
    - Hero section uses `when` branch on `HeroType` to render `StackedCardHero`, `SocialProofHero`, or `CategoryGridHero`
    - All colors from `MaterialTheme.colorScheme` tokens only
    - _Requirements: 1.1, 7.1, 7.4_

  - [x] 7.2 Implement the main `OnboardingScreen` composable with HorizontalPager
    - Replace existing `OnboardingScreen` content at the same file path
    - Root Column with `statusBarsPadding` at top and `navigationBarsPadding` at bottom
    - "Skip" TextButton in top-right corner on all screens — taps `completeOnboarding()` + `onFinish()`
    - `HorizontalPager` with `pageCount = 3` hosting `OnboardingPageLayout` for each page
    - Pager section uses `Modifier.weight(1f)`, bottom controls use fixed dp sizes
    - `PageIndicatorRow` below pager
    - `OnboardingActionButton` at bottom
    - "Next" button calls `animateScrollToPage(currentPage + 1)`
    - "Get Started" button calls `viewModel.completeOnboarding()` then `onFinish()`
    - Page transition animation 300–500ms via HorizontalPager defaults
    - Background uses `MaterialTheme.colorScheme.background`
    - _Requirements: 6.1, 6.2, 6.4, 6.5, 6.6, 7.1, 7.4, 7.5, 9.1, 9.2, 9.3, 9.4_

  - [x] 7.3 Write property test for Next navigation
    - **Property 7: Next navigation advances page** — Generate page indices < pageCount-1, verify invoking Next results in page index i+1
    - **Validates: Requirements 6.4**

  - [x] 7.4 Write unit tests for OnboardingScreen
    - Verify each page renders correct headline and subtitle text
    - Verify logo is present on all 3 pages
    - Verify Skip button is present and triggers `onFinish`
    - Verify Get Started on last page triggers `completeOnboarding()` + `onFinish`
    - Verify stacked card hero has 2 cards with overlay badge
    - Verify social proof hero has non-interactive Follow buttons
    - Verify category grid has 6 icons and center placeholder
    - _Requirements: 1.1, 2.4, 3.5, 4.5, 6.5, 6.6_

- [x] 8. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document
- Unit tests validate specific examples and edge cases
- The existing `OnboardingViewModel` and navigation contract remain unchanged — only the screen composable content is replaced
- All colors must use `MaterialTheme.colorScheme` tokens — no hardcoded hex values in composables
- Implementation language: Kotlin with Jetpack Compose

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2", "1.3"] },
    { "id": 1, "tasks": ["2.1", "2.2", "2.3", "2.4"] },
    { "id": 2, "tasks": ["2.5", "3.1", "4.1", "6.1"] },
    { "id": 3, "tasks": ["3.2", "4.2", "6.2"] },
    { "id": 4, "tasks": ["4.3", "6.3", "7.1"] },
    { "id": 5, "tasks": ["7.2"] },
    { "id": 6, "tasks": ["7.3", "7.4"] }
  ]
}
```
