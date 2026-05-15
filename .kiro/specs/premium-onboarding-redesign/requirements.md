# Requirements Document

## Introduction

Redesign the onboarding flow for the "Where" Android app to deliver a premium, visually rich first-time experience. The current onboarding uses simple Material icons in colored circles. The new design replaces these with full-bleed imagery, stacked card layouts, social-proof user lists, and floating icon grids — inspired by a high-end reference design. Each screen retains the app logo at the top, a bold headline, subtitle, animated page indicators, and a full-width action button at the bottom.

## Glossary

- **Onboarding_Flow**: The three-screen introductory experience shown to first-time users before they reach the main app.
- **Onboarding_Screen**: A single page within the Onboarding_Flow, displayed inside a HorizontalPager.
- **Hero_Section**: The large visual area occupying the upper portion of each Onboarding_Screen, containing imagery or illustrative content.
- **Page_Indicator**: The row of animated dots below the Hero_Section that communicates the user's current position within the Onboarding_Flow.
- **Action_Button**: The full-width button at the bottom of each Onboarding_Screen ("Next" or "Get Started").
- **App_Logo**: The "Where" brand mark displayed at the top of every Onboarding_Screen.
- **Stacked_Card_Layout**: A layered card composition on Screen 1 showing overlapping image cards with a call-to-action overlay.
- **Social_Proof_Section**: A list of user profile cards with follow buttons on Screen 2, demonstrating community activity.
- **Category_Grid**: A floating icon grid or globe illustration on Screen 3, representing the app's feature categories.
- **Image_Placeholder**: A styled container with defined dimensions and a content-description label indicating what image the developer should supply.

## Requirements

### Requirement 1: App Logo Display

**User Story:** As a new user, I want to see the app branding on every onboarding screen, so that I immediately recognize which app I am setting up.

#### Acceptance Criteria

1. THE Onboarding_Screen SHALL display the App_Logo centered horizontally within the top 20% of the visible screen area on all three onboarding pages.
2. THE App_Logo SHALL render at a fixed size of 64×64 dp and maintain identical size and vertical position across all three pages of the Onboarding_Flow.
3. THE App_Logo SHALL be rendered using the drawable resource (ic_launcher_foreground) tinted with the app's primary brand color (#5170FF).
4. IF the App_Logo drawable resource fails to load, THEN THE Onboarding_Screen SHALL display a fallback placeholder of the same 64×64 dp dimensions using the primary brand color (#5170FF) as background.

### Requirement 2: Screen 1 — Stacked Card Hero with Location Sharing Theme

**User Story:** As a new user, I want to see an engaging visual of stacked image cards on the first screen, so that I understand the app offers a rich location-sharing experience.

#### Acceptance Criteria

1. WHEN the first Onboarding_Screen is displayed, THE Hero_Section SHALL render a Stacked_Card_Layout containing exactly 2 overlapping image cards, each with RoundedCornerShape(24.dp) corners, where the back card is offset by 12dp horizontally and 8dp vertically from the front card and rotated between 3 and 6 degrees, with elevations of 2dp for the back card and 6dp for the front card.
2. WHEN the first Onboarding_Screen is displayed, THE Stacked_Card_Layout SHALL include a non-interactive overlay badge on the top card with a minimum size of 24dp by 24dp, positioned at the top-end corner of the card, using the primaryContainer color as background, to visually convey interactivity without requiring user tap.
3. WHEN the first Onboarding_Screen is displayed, THE Stacked_Card_Layout SHALL contain Image_Placeholders with contentDescription set to a non-empty string indicating: "Replace with a lifestyle photo showing friends sharing location or a map screenshot with pins."
4. WHEN the first Onboarding_Screen is displayed, THE Onboarding_Screen SHALL show the headline "Share Your Location" in headlineMedium typography below the Hero_Section.
5. WHEN the first Onboarding_Screen is displayed, THE Onboarding_Screen SHALL show the subtitle "Let friends and family know where you are in real time — safely and privately." in bodyLarge typography below the headline.

### Requirement 3: Screen 2 — Social Proof Hero with Group Theme

**User Story:** As a new user, I want to see a list of user profiles with follow buttons on the second screen, so that I understand the app has an active community I can join.

#### Acceptance Criteria

1. WHEN the second Onboarding_Screen is displayed, THE Hero_Section SHALL render a Social_Proof_Section containing between 3 and 5 user profile cards arranged vertically within a surface-colored container with rounded corners (cornerMedium = 16dp) and card elevation between 2dp and 6dp.
2. THE Social_Proof_Section SHALL display each profile card as a horizontal row containing: a circular avatar Image_Placeholder of 48dp diameter, a display name (maximum 30 characters, truncated with ellipsis if exceeded) in bodyLarge typography, a subtitle line in bodyMedium typography, and a "Follow" button aligned to the end of the row.
3. THE Social_Proof_Section "Follow" buttons SHALL be decorative (non-interactive) and styled with a filled primary-colored background and onPrimary text color, using labelLarge typography and rounded shape (cornerSmall = 12dp).
4. THE Social_Proof_Section profile avatar Image_Placeholders SHALL include content descriptions indicating: "Replace with sample user avatar photos or use generated placeholder avatars."
5. WHEN the second Onboarding_Screen is displayed, THE Onboarding_Screen SHALL show the headline "Create or Join Groups" in headlineMedium typography below the Hero_Section.
6. WHEN the second Onboarding_Screen is displayed, THE Onboarding_Screen SHALL show the subtitle "Create groups for family, friends, or events. Invite anyone with a simple 8-character code." in bodyLarge typography below the headline.

### Requirement 4: Screen 3 — Category Grid Hero with Connectivity Theme

**User Story:** As a new user, I want to see a floating icon grid or globe illustration on the third screen, so that I understand the breadth of features available in the app.

#### Acceptance Criteria

1. WHEN the third Onboarding_Screen is displayed, THE Hero_Section SHALL render a Category_Grid containing exactly 6 floating icon elements arranged in a scattered pattern around a central Image_Placeholder.
2. THE Category_Grid SHALL include the following Material icons: LocationOn, Group, Map, Chat, Notifications, and Navigation, each rendered inside a circular container with a diameter between 40dp and 56dp and a background of surfaceVariant color.
3. THE Category_Grid SHALL apply an elevation of 2dp to 4dp to each icon container element to create a floating appearance with visible shadow.
4. THE Category_Grid SHALL include an Image_Placeholder at the center with a diameter between 80dp and 120dp and content description indicating: "Replace with a globe illustration or world-map graphic to represent global connectivity."
5. WHEN the third Onboarding_Screen is displayed, THE Onboarding_Screen SHALL show the headline "Stay Connected" in headlineMedium typography below the Hero_Section.
6. WHEN the third Onboarding_Screen is displayed, THE Onboarding_Screen SHALL show the subtitle "See everyone's location on a live map, get ETAs, and feel closer to the people you care about." in bodyLarge typography below the headline.
7. THE Category_Grid SHALL provide a contentDescription on each icon element that identifies the represented feature (e.g., "Location", "Group", "Map", "Chat", "Notifications", "Navigation") for accessibility.

### Requirement 5: Page Indicators

**User Story:** As a new user, I want to see animated page dots, so that I know how many screens remain and which one I am currently viewing.

#### Acceptance Criteria

1. THE Onboarding_Flow SHALL display a Page_Indicator row containing one dot per onboarding page (3 dots total), positioned vertically between the Hero_Section content and the Action_Button.
2. WHEN the user navigates to a page, THE Page_Indicator SHALL animate the corresponding dot from 8dp circular to a 24dp-wide pill shape (8dp height) using a 300ms tween animation, while all other dots remain 8dp circular.
3. THE Page_Indicator active dot SHALL use the MaterialTheme primary color and inactive dots SHALL use the MaterialTheme outlineVariant color.
4. THE Page_Indicator row SHALL provide an accessibility content description that conveys the current page position and total page count (e.g., "Page 2 of 3").

### Requirement 6: Navigation Buttons

**User Story:** As a new user, I want clear navigation buttons, so that I can progress through or skip the onboarding.

#### Acceptance Criteria

1. WHILE the user is on the first or second Onboarding_Screen, THE Action_Button SHALL display "Next" as a full-width button with primaryContainer background and onPrimaryContainer text color.
2. WHILE the user is on the third Onboarding_Screen, THE Action_Button SHALL display "Get Started" as a full-width button with primary background and onPrimary text color.
3. THE Action_Button SHALL use a rounded shape (cornerLarge = 24dp) and buttonHeight (56dp) dimensions to create a pill-shaped premium appearance.
4. WHEN the user taps the "Next" Action_Button, THE Onboarding_Flow SHALL animate to the next page using animateScrollToPage.
5. WHEN the user taps the "Get Started" Action_Button, THE Onboarding_Flow SHALL mark onboarding as complete and navigate to the Login screen.
6. THE Onboarding_Flow SHALL provide a "Skip" text button in the top-right corner on all screens that, when tapped, marks onboarding as complete and navigates to the Login screen.

### Requirement 7: Premium Visual Polish

**User Story:** As a new user, I want the onboarding to feel polished and high-quality, so that I trust the app is well-crafted.

#### Acceptance Criteria

1. THE Onboarding_Screen SHALL reference only MaterialTheme.colorScheme tokens (primary, primaryContainer, background, onBackground, onSurfaceVariant, outlineVariant) for all text, icon, background, button, and indicator colors, with no hardcoded color values.
2. THE Onboarding_Screen page content cards SHALL apply elevation between 2dp (cardElevation) and 6dp (cardElevationHigh) using values defined in the app's Dimens object, where the foreground card uses cardElevationHigh (6dp) and any supporting background elements use cardElevation (2dp).
3. THE Onboarding_Screen SHALL display a stacked card effect with exactly 2 background cards behind the foreground card, each rotated between 2 and 5 degrees (inclusive) in alternating directions to create a fanned appearance.
4. THE Onboarding_Screen background SHALL use MaterialTheme.colorScheme.background as its surface color with no additional decorative overlays or patterns.
5. WHEN the user swipes horizontally or taps the Next button, THE Onboarding_Screen SHALL animate the page transition using HorizontalPager with an animation duration between 300ms and 500ms.

### Requirement 8: Image Placeholder Guidance

**User Story:** As a developer, I want clear placeholder containers with guidance labels, so that I know exactly what images to add later.

#### Acceptance Criteria

1. THE Image_Placeholder SHALL render as a container with RoundedCornerShape using the medium corner radius (16dp), filled with MaterialTheme.colorScheme.surfaceVariant, with a minimum size of 120dp × 120dp, and a centered icon above a centered label indicating the expected image type.
2. THE Image_Placeholder SHALL include a non-empty contentDescription string of no more than 80 characters that identifies the recommended image type (e.g., "Profile photo placeholder" or "Group cover image placeholder") for accessibility and developer guidance.
3. IF no image resource is provided, THEN THE Image_Placeholder SHALL display Icons.Default.Image at a size of 48dp in MaterialTheme.colorScheme.onSurfaceVariant color as a visual indicator.
4. THE Image_Placeholder SHALL meet the minimum touch target size of 48dp × 48dp and SHALL be distinguishable from the screen background by using the surfaceVariant fill which provides a contrast ratio of at least 3:1 against the surface background.

### Requirement 9: Responsive Layout

**User Story:** As a user on any device size, I want the onboarding screens to adapt gracefully, so that the experience looks good on both small and large phones.

#### Acceptance Criteria

1. THE Onboarding_Screen layout SHALL allocate the pager section (hero icon, title, and description) a weight of 1f relative to the fixed-height bottom controls (indicators and navigation buttons), such that the pager section occupies between 45% and 55% of the total column height after system bar insets are applied.
2. THE Onboarding_Screen layout SHALL use Compose `Modifier.weight` for the pager section and fixed dp sizes (from the Dimens object) for the bottom controls, so that on screens ranging from 5 inches to 6.7 inches the pager section expands or contracts while the bottom controls retain their defined dimensions.
3. THE Onboarding_Screen SHALL apply `statusBarsPadding` at the top and `navigationBarsPadding` at the bottom of the root Column to prevent any content from rendering beneath the system status bar or navigation bar.
4. WHILE the Onboarding_Screen is displayed on a screen with less than 600dp of available height, THE Onboarding_Screen SHALL still render the hero icon, title text, description text, page indicators, and navigation button without clipping or overlapping any element.
