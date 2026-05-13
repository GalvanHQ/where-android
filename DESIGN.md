# Where — Design System Documentation

This document defines the visual language, component library, and interaction guidelines for the **Where** Android application (`com.ovi.where`). All contributors should follow these specifications to maintain consistency across the app.

---

## 1. Color Palette

The color system is derived from primary color **#5170FF** using Material 3 tonal palettes. All tokens are defined in `Color.kt` and assigned in `Theme.kt`.

### 1.1 Light Color Scheme

| Role | Token | Hex Value |
|------|-------|-----------|
| primary | Primary40 | `#5170FF` |
| onPrimary | — | `#FFFFFF` |
| primaryContainer | Primary90 | `#DCE1FF` |
| onPrimaryContainer | Primary10 | `#001452` |
| secondary | Secondary40 | `#006878` |
| onSecondary | — | `#FFFFFF` |
| secondaryContainer | Secondary90 | `#B2EBFA` |
| onSecondaryContainer | Secondary10 | `#001F26` |
| tertiary | Tertiary40 | `#8E3A8C` |
| onTertiary | — | `#FFFFFF` |
| tertiaryContainer | Tertiary90 | `#FFD6F9` |
| onTertiaryContainer | Tertiary10 | `#3B0037` |
| error | Error40 | `#BA1A1A` |
| onError | — | `#FFFFFF` |
| errorContainer | Error90 | `#FFDAD6` |
| onErrorContainer | Error10 | `#410002` |
| background | Neutral99 | `#FEFBFF` |
| onBackground | Neutral10 | `#1B1B1F` |
| surface | Neutral99 | `#FEFBFF` |
| onSurface | Neutral10 | `#1B1B1F` |
| surfaceVariant | NeutralVar90 | `#E3E1EC` |
| onSurfaceVariant | NeutralVar30 | `#46464F` |
| outline | NeutralVar40 | `#5E5D67` |
| outlineVariant | NeutralVar80 | `#C7C5D0` |
| inverseSurface | Neutral20 | `#303034` |
| inverseOnSurface | Neutral94 | `#F0EDF1` |
| inversePrimary | Primary80 | `#B6C4FF` |
| scrim | — | `#000000` |

### 1.2 Dark Color Scheme

| Role | Token | Hex Value |
|------|-------|-----------|
| primary | Primary80 | `#B6C4FF` |
| onPrimary | Primary20 | `#002984` |
| primaryContainer | Primary30 | `#003DB8` |
| onPrimaryContainer | Primary90 | `#DCE1FF` |
| secondary | Secondary80 | `#5DD5F0` |
| onSecondary | Secondary20 | `#00363F` |
| secondaryContainer | Secondary30 | `#004E5A` |
| onSecondaryContainer | Secondary90 | `#B2EBFA` |
| tertiary | Tertiary80 | `#EBB0E8` |
| onTertiary | Tertiary20 | `#560057` |
| tertiaryContainer | Tertiary30 | `#731B72` |
| onTertiaryContainer | Tertiary90 | `#FFD6F9` |
| error | Error80 | `#FFB4AB` |
| onError | Error10 | `#410002` |
| errorContainer | Error40 | `#BA1A1A` |
| onErrorContainer | Error90 | `#FFDAD6` |
| background | Neutral10 | `#1B1B1F` |
| onBackground | Neutral90 | `#E4E1E6` |
| surface | Neutral10 | `#1B1B1F` |
| onSurface | Neutral90 | `#E4E1E6` |
| surfaceVariant | NeutralVar30 | `#46464F` |
| onSurfaceVariant | NeutralVar80 | `#C7C5D0` |
| outline | NeutralVar80 | `#C7C5D0` |
| outlineVariant | NeutralVar30 | `#46464F` |
| inverseSurface | Neutral90 | `#E4E1E6` |
| inverseOnSurface | Neutral20 | `#303034` |
| inversePrimary | Primary40 | `#5170FF` |
| scrim | — | `#000000` |

### 1.3 Semantic Colors

| Name | Token | Hex Value | Usage |
|------|-------|-----------|-------|
| LocationActive | Secondary40 | `#006878` | Active location sharing indicator |
| LocationInactive | NeutralVar40 | `#5E5D67` | Inactive location indicator |
| GoogleBlue | — | `#4285F4` | Google sign-in brand constant |

### 1.4 Avatar Colors

8 entries with distributed hues at 40-tone level for visual distinguishability:

| Index | Hex Value | Hue Description |
|-------|-----------|-----------------|
| 0 | `#5170FF` | Indigo-blue (~228°) |
| 1 | `#006878` | Teal (~190°) |
| 2 | `#8E3A8C` | Rose-violet (~301°) |
| 3 | `#6B5E00` | Olive-gold (~52°) |
| 4 | `#8B4513` | Warm brown-orange (~28°) |
| 5 | `#006E2C` | Green (~150°) |
| 6 | `#BA1A1A` | Red (~0°) |
| 7 | `#006491` | Cerulean blue (~210°) |

---

## 2. Typography Scale

Font family: **Nunito** (Google Fonts). All 15 Material 3 text styles are defined in `Type.kt`.

| Style | Weight | Size | Line Height | Letter Spacing |
|-------|--------|------|-------------|----------------|
| displayLarge | Bold (700) | 57sp | 64sp | -0.25sp |
| displayMedium | Bold (700) | 45sp | 52sp | 0sp |
| displaySmall | Bold (700) | 36sp | 44sp | 0sp |
| headlineLarge | ExtraBold (800) | 32sp | 40sp | 0sp |
| headlineMedium | ExtraBold (800) | 28sp | 36sp | 0sp |
| headlineSmall | Bold (700) | 24sp | 32sp | 0sp |
| titleLarge | Bold (700) | 22sp | 28sp | 0sp |
| titleMedium | SemiBold (600) | 16sp | 24sp | 0.15sp |
| titleSmall | SemiBold (600) | 14sp | 20sp | 0.1sp |
| bodyLarge | Normal (400) | 16sp | 24sp | 0.5sp |
| bodyMedium | Normal (400) | 14sp | 20sp | 0.25sp |
| bodySmall | Normal (400) | 12sp | 16sp | 0.4sp |
| labelLarge | SemiBold (600) | 14sp | 20sp | 0.1sp |
| labelMedium | SemiBold (600) | 12sp | 16sp | 0.5sp |
| labelSmall | Medium (500) | 11sp | 16sp | 0.5sp |

---

## 3. Shape Scale

Defined in `WhereShapes` (Theme.kt) and mirrored in `Dimens.kt`:

| Shape Token | Corner Radius |
|-------------|---------------|
| extraSmall | 6dp |
| small | 12dp |
| medium | 16dp |
| large | 24dp |
| extraLarge | 32dp |

Additional: `cornerRound` = 50dp (fully rounded, used for pills and circular elements).

---

## 4. Spacing Scale

All spacing tokens are defined in `Dimens.kt`.

### 4.1 Core Spacing

| Token | Value |
|-------|-------|
| spaceNone | 0dp |
| spaceXSmall | 2dp |
| spaceSmall | 4dp |
| spaceMedium | 8dp |
| spaceLarge | 16dp |
| spaceXLarge | 24dp |
| space2XLarge | 32dp |
| space3XLarge | 48dp |

### 4.2 Buttons

| Token | Value |
|-------|-------|
| buttonHeight | 56dp |
| buttonHeightSmall | 48dp |
| buttonHeightLarge | 64dp |

### 4.3 Icons

| Token | Value |
|-------|-------|
| iconSizeXSmall | 12dp |
| iconSizeSmall | 16dp |
| iconSizeMedium | 24dp |
| iconSizeLarge | 32dp |
| iconSizeXLarge | 48dp |
| iconSizeXXLarge | 72dp |

### 4.4 Avatars

| Token | Value |
|-------|-------|
| avatarSizeSmall | 32dp |
| avatarSizeMedium | 48dp |
| avatarSizeLarge | 64dp |
| avatarSizeXLarge | 96dp |
| avatarCircle | 120dp |

### 4.5 Cards

| Token | Value |
|-------|-------|
| cardElevation | 2dp |
| cardElevationSubtle | 1dp |
| cardElevationHigh | 6dp |

### 4.6 Strokes & Dividers

| Token | Value |
|-------|-------|
| strokeWidthThin | 2dp |
| dividerThickness | 1dp |

### 4.7 Map Markers

| Token | Value |
|-------|-------|
| markerRadius | 20dp |
| destinationMarkerSize | 48dp |

### 4.8 Splash & Onboarding

| Token | Value |
|-------|-------|
| splashIconSize | 100dp |
| indicatorWidthSelected | 24dp |
| indicatorWidthIdle | 8dp |
| indicatorHeight | 8dp |

### 4.9 Miscellaneous

| Token | Value |
|-------|-------|
| settingIconContainer | 40dp |
| badgeIconSize | 18dp |
| shimmerBarHeightL | 16dp |
| shimmerBarHeightS | 12dp |
| memberListHeight | 160dp |

---

## 5. Component Catalog

All shared composables are defined in `presentation/common/Components.kt`.

### 5.1 LoadingIndicator

Full-screen centered loading spinner with optional message.

| Parameter | Type | Default | Required |
|-----------|------|---------|----------|
| modifier | Modifier | Modifier | No |
| message | String? | null | No |

```kotlin
LoadingIndicator(message = "Loading friends…")
```

### 5.2 EmptyState

Full-screen empty state with icon, message, and optional action button.

| Parameter | Type | Default | Required |
|-----------|------|---------|----------|
| message | String | — | Yes |
| modifier | Modifier | Modifier | No |
| icon | ImageVector? | null | No |
| action | (() -> Unit)? | null | No |
| actionLabel | String? | null | No |

```kotlin
EmptyState(
    message = "No friends yet",
    icon = Icons.Default.People,
    action = { navigateToSearch() },
    actionLabel = "Find People"
)
```

### 5.3 ErrorView

Full-screen error display with message and optional retry button.

| Parameter | Type | Default | Required |
|-----------|------|---------|----------|
| message | String | — | Yes |
| modifier | Modifier | Modifier | No |
| onRetry | (() -> Unit)? | null | No |

```kotlin
ErrorView(
    message = "Failed to load data",
    onRetry = { viewModel.retry() }
)
```

### 5.4 PrimaryButton

Full-width primary action button with loading state.

| Parameter | Type | Default | Required |
|-----------|------|---------|----------|
| text | String | — | Yes |
| onClick | () -> Unit | — | Yes |
| modifier | Modifier | Modifier | No |
| enabled | Boolean | true | No |
| isLoading | Boolean | false | No |
| containerColor | Color | colorScheme.primary | No |
| contentColor | Color | colorScheme.onPrimary | No |

```kotlin
PrimaryButton(
    text = "Sign In",
    onClick = { viewModel.signIn() },
    isLoading = uiState.isLoading
)
```

### 5.5 SecondaryButton

Full-width elevated button for secondary actions.

| Parameter | Type | Default | Required |
|-----------|------|---------|----------|
| text | String | — | Yes |
| onClick | () -> Unit | — | Yes |
| modifier | Modifier | Modifier | No |
| enabled | Boolean | true | No |
| isLoading | Boolean | false | No |

```kotlin
SecondaryButton(text = "Cancel", onClick = { navController.popBackStack() })
```

### 5.6 TonalButton

Compact filled tonal button for inline actions.

| Parameter | Type | Default | Required |
|-----------|------|---------|----------|
| text | String | — | Yes |
| onClick | () -> Unit | — | Yes |
| modifier | Modifier | Modifier | No |
| enabled | Boolean | true | No |
| isLoading | Boolean | false | No |

```kotlin
TonalButton(text = "Accept", onClick = { viewModel.acceptRequest(userId) })
```

### 5.7 TextActionButton

Text-only button for tertiary actions.

| Parameter | Type | Default | Required |
|-----------|------|---------|----------|
| text | String | — | Yes |
| onClick | () -> Unit | — | Yes |
| modifier | Modifier | Modifier | No |
| enabled | Boolean | true | No |

```kotlin
TextActionButton(text = "Forgot Password?", onClick = { navigateToForgotPassword() })
```

### 5.8 GoogleSignInButton

Full-width elevated button with Google logo for OAuth sign-in.

| Parameter | Type | Default | Required |
|-----------|------|---------|----------|
| onClick | () -> Unit | — | Yes |
| modifier | Modifier | Modifier | No |
| isLoading | Boolean | false | No |
| enabled | Boolean | true | No |

```kotlin
GoogleSignInButton(onClick = { viewModel.signInWithGoogle() })
```

### 5.9 WhereTextField

Outlined text field with validation support, password toggle, and leading/trailing icons.

| Parameter | Type | Default | Required |
|-----------|------|---------|----------|
| value | String | — | Yes |
| onValueChange | (String) -> Unit | — | Yes |
| label | String | — | Yes |
| modifier | Modifier | Modifier | No |
| keyboardType | KeyboardType | Text | No |
| isPassword | Boolean | false | No |
| enabled | Boolean | true | No |
| readOnly | Boolean | false | No |
| singleLine | Boolean | true | No |
| maxLines | Int | 1 | No |
| minLines | Int | 1 | No |
| isError | Boolean | false | No |
| errorMessage | String? | null | No |
| leadingIcon | @Composable (() -> Unit)? | null | No |
| trailingIcon | @Composable (() -> Unit)? | null | No |
| keyboardActions | KeyboardActions | Default | No |
| imeAction | ImeAction | Next | No |
| capitalization | KeyboardCapitalization | None | No |

```kotlin
WhereTextField(
    value = name,
    onValueChange = { name = it },
    label = "Group Name",
    isError = nameError != null,
    errorMessage = nameError
)
```

### 5.10 EmailTextField

Pre-configured email text field with email icon and keyboard type.

| Parameter | Type | Default | Required |
|-----------|------|---------|----------|
| value | String | — | Yes |
| onValueChange | (String) -> Unit | — | Yes |
| modifier | Modifier | Modifier | No |
| enabled | Boolean | true | No |
| isError | Boolean | false | No |
| errorMessage | String? | null | No |
| keyboardActions | KeyboardActions | Default | No |
| imeAction | ImeAction | Next | No |

```kotlin
EmailTextField(
    value = email,
    onValueChange = { email = it },
    isError = emailError != null,
    errorMessage = emailError
)
```

### 5.11 PasswordTextField

Pre-configured password text field with lock icon and visibility toggle.

| Parameter | Type | Default | Required |
|-----------|------|---------|----------|
| value | String | — | Yes |
| onValueChange | (String) -> Unit | — | Yes |
| modifier | Modifier | Modifier | No |
| label | String | — | Yes |
| enabled | Boolean | true | No |
| isError | Boolean | false | No |
| errorMessage | String? | null | No |
| keyboardActions | KeyboardActions | Default | No |
| imeAction | ImeAction | Done | No |

```kotlin
PasswordTextField(
    value = password,
    onValueChange = { password = it },
    label = "Password"
)
```

### 5.12 NameTextField

Pre-configured name text field with person icon and word capitalization.

| Parameter | Type | Default | Required |
|-----------|------|---------|----------|
| value | String | — | Yes |
| onValueChange | (String) -> Unit | — | Yes |
| modifier | Modifier | Modifier | No |
| label | String | — | Yes |
| enabled | Boolean | true | No |
| isError | Boolean | false | No |
| errorMessage | String? | null | No |
| keyboardActions | KeyboardActions | Default | No |
| imeAction | ImeAction | Next | No |

```kotlin
NameTextField(
    value = firstName,
    onValueChange = { firstName = it },
    label = "First Name"
)
```

### 5.13 OnlineIndicator

Small colored dot indicating online/offline status.

| Parameter | Type | Default | Required |
|-----------|------|---------|----------|
| isOnline | Boolean | — | Yes |
| modifier | Modifier | Modifier | No |
| size | Dp | 6dp | No |

```kotlin
OnlineIndicator(isOnline = user.isOnline)
```

### 5.14 SharingStatusBadge

Pill-shaped badge showing location sharing status.

| Parameter | Type | Default | Required |
|-----------|------|---------|----------|
| isSharing | Boolean | — | Yes |
| modifier | Modifier | Modifier | No |

```kotlin
SharingStatusBadge(isSharing = user.isSharingLocation)
```

### 5.15 InfoCard

Colored information card with icon, title, and message. Supports SUCCESS, WARNING, ERROR, INFO types.

| Parameter | Type | Default | Required |
|-----------|------|---------|----------|
| title | String | — | Yes |
| message | String | — | Yes |
| modifier | Modifier | Modifier | No |
| icon | ImageVector? | null | No |
| type | InfoCardType | INFO | No |

```kotlin
InfoCard(
    title = "Location Shared",
    message = "Your friends can see your location",
    type = InfoCardType.SUCCESS
)
```

### 5.16 DividerText

Horizontal divider with centered text label (e.g., "OR").

| Parameter | Type | Default | Required |
|-----------|------|---------|----------|
| text | String | — | Yes |
| modifier | Modifier | Modifier | No |

```kotlin
DividerText(text = "OR")
```

### 5.17 AnnotatedClickableText

Text with a clickable suffix styled as a link.

| Parameter | Type | Default | Required |
|-----------|------|---------|----------|
| prefix | String | — | Yes |
| clickableText | String | — | Yes |
| onClick | () -> Unit | — | Yes |
| modifier | Modifier | Modifier | No |

```kotlin
AnnotatedClickableText(
    prefix = "Don't have an account?",
    clickableText = "Sign Up",
    onClick = { navigateToSignUp() }
)
```

### 5.18 shimmerBrush

Returns a linear gradient brush for shimmer loading animations.

| Parameter | Type | Default | Required |
|-----------|------|---------|----------|
| showShimmer | Boolean | true | No |
| targetValue | Float | 1000f | No |

```kotlin
Box(modifier = Modifier.background(shimmerBrush()))
```

### 5.19 ShimmerGroupList

Pre-built shimmer placeholder for group list loading state.

| Parameter | Type | Default | Required |
|-----------|------|---------|----------|
| modifier | Modifier | Modifier | No |

```kotlin
ShimmerGroupList()
```

### 5.20 WhereTopAppBar

Consistent top app bar with surface colors, optional back navigation, actions, and scroll behavior.

| Parameter | Type | Default | Required |
|-----------|------|---------|----------|
| title | String | — | Yes |
| onNavigateBack | (() -> Unit)? | null | No |
| actions | @Composable () -> Unit | {} | No |
| scrollBehavior | TopAppBarScrollBehavior? | null | No |

```kotlin
WhereTopAppBar(
    title = "Settings",
    onNavigateBack = { navController.popBackStack() },
    scrollBehavior = scrollBehavior
)
```

### 5.21 WhereTabHeader

Large headline header for bottom-tab root screens with action buttons.

| Parameter | Type | Default | Required |
|-----------|------|---------|----------|
| title | String | — | Yes |
| modifier | Modifier | Modifier | No |
| actions | @Composable RowScope.() -> Unit | {} | No |

```kotlin
WhereTabHeader(title = "People") {
    IconButton(onClick = { navigateToSearch() }) {
        Icon(Icons.Default.Search, contentDescription = "Search")
    }
}
```

---

## 6. Screen Inventory

18 screens defined in `Screen.kt`. Each maps to a route and a top-level layout composable.

| # | Screen | Route | Layout | Shared Components Used |
|---|--------|-------|--------|------------------------|
| 1 | Onboarding | `onboarding` | Column | PrimaryButton, SecondaryButton |
| 2 | Login | `login` | Column | EmailTextField, PasswordTextField, PrimaryButton, GoogleSignInButton, DividerText, AnnotatedClickableText, TextActionButton |
| 3 | SignUp | `register` | Column | NameTextField, EmailTextField, PasswordTextField, PrimaryButton, GoogleSignInButton, DividerText, AnnotatedClickableText |
| 4 | ForgotPassword | `forgot_password` | Column | WhereTopAppBar, EmailTextField, PrimaryButton, InfoCard |
| 5 | EmailVerification | `email_verification` | Column | WhereTopAppBar, PrimaryButton, TextActionButton, InfoCard |
| 6 | CompleteProfile | `complete_profile` | Column | WhereTopAppBar, NameTextField, PrimaryButton |
| 7 | Main | `main` | Scaffold (bottomBar) | Bottom navigation tabs (Map, Chats, People, Profile) |
| 8 | EditProfile | `edit_profile` | Scaffold (topBar) | WhereTopAppBar, WhereTextField, PrimaryButton |
| 9 | Settings | `settings` | Scaffold (topBar) | WhereTopAppBar |
| 10 | Chat | `chat/{conversationId}` | Scaffold (topBar, bottomBar) | WhereTopAppBar, WhereTextField |
| 11 | UserProfile | `user_profile/{userId}` | Column | WhereTopAppBar, TonalButton, OnlineIndicator, SharingStatusBadge |
| 12 | FriendRequests | `friend_requests` | Scaffold (topBar) | WhereTopAppBar, TonalButton, EmptyState, LoadingIndicator |
| 13 | SearchPeople | `search_people` | Scaffold (topBar) | WhereTopAppBar, WhereTextField, EmptyState, LoadingIndicator |
| 14 | GroupDetails | `group_details/{groupId}` | Scaffold (topBar) | WhereTopAppBar, TonalButton, OnlineIndicator |
| 15 | GroupMap | `group_map/{groupId}` | Box (full-bleed) | WhereTopAppBar |
| 16 | CreateGroup | `create_group` | Scaffold (topBar) | WhereTopAppBar, WhereTextField, PrimaryButton |
| 17 | JoinGroup | `join_group` | Scaffold (topBar) | WhereTopAppBar, PrimaryButton, InfoCard |
| 18 | EditGroup | `edit_group/{groupId}` | Scaffold (topBar) | WhereTopAppBar, WhereTextField, PrimaryButton |

---

## 7. Animation Specifications

### 7.1 Navigation Transitions

| Animation | Duration | Easing | Location |
|-----------|----------|--------|----------|
| Screen enter (slide in from end) | 300ms | tween (linear) | AppNavGraph.kt |
| Screen exit (slide out to start) | 300ms | tween (linear) | AppNavGraph.kt |
| Pop enter (slide in from start) | 300ms | tween (linear) | AppNavGraph.kt |
| Pop exit (slide out to end) | 300ms | tween (linear) | AppNavGraph.kt |
| Gatekeeper enter | 0ms | instant | AppNavGraph.kt |
| Gatekeeper exit (fade out) | 300ms | tween | AppNavGraph.kt |
| Onboarding enter (fade in) | 400ms | tween | AppNavGraph.kt |
| Main enter (fade in) | 400ms | tween | AppNavGraph.kt |

### 7.2 Bottom Tab Transitions

| Animation | Duration | Easing | Location |
|-----------|----------|--------|----------|
| Tab enter (fade in) | 200ms | tween | MainScaffold.kt |
| Tab exit (fade out) | 200ms | tween | MainScaffold.kt |

### 7.3 Content Animations

| Animation | Duration | Easing | Repeat Mode | Location |
|-----------|----------|--------|-------------|----------|
| Shimmer gradient sweep | 900ms | LinearEasing | Restart (infinite) | Components.kt (shimmerBrush) |
| Settings content fade-in | 500ms | FastOutSlowInEasing | None | SettingsScreen.kt |
| Profile content fade-in | 500ms | FastOutSlowInEasing | None | ProfileScreen.kt |
| EditProfile content fade-in | 500ms | FastOutSlowInEasing | None | EditProfileScreen.kt |
| Onboarding indicator width | 300ms | tween | None | OnboardingScreen.kt |

### 7.4 Live Ring Animations

| Animation | Duration | Easing | Repeat Mode | Location |
|-----------|----------|--------|-------------|----------|
| Profile avatar ring alpha | 1500ms | LinearEasing | Reverse (infinite) | ProfileScreen.kt |
| LiveRingAvatar scale pulse | 1500ms | tween | Reverse (infinite) | LiveRingAvatar.kt |
| LiveRingAvatar alpha pulse | 1500ms | tween | Reverse (infinite) | LiveRingAvatar.kt |

### 7.5 Success Animations

| Animation | Duration | Easing | Repeat Mode | Location |
|-----------|----------|--------|-------------|----------|
| Join group success (fade + scale) | spring | DampingRatioMediumBouncy, StiffnessLow | None | JoinGroupScreen.kt |

---

## 8. Accessibility Requirements

### 8.1 Touch Targets

- Minimum touch target size: **48dp × 48dp** for all interactive elements (buttons, icons, toggles)
- `Dimens.buttonHeightSmall` (48dp) is the minimum button height
- `Dimens.iconSizeXLarge` (48dp) is used for primary interactive icons

### 8.2 Content Descriptions

- All interactive `Icon` composables must include a non-null `contentDescription` parameter
- All `Image` composables displaying meaningful content must include `contentDescription`
- Decorative icons may use `contentDescription = null` only when they are accompanied by visible text that conveys the same meaning
- Password visibility toggles use descriptive labels: "Hide password" / "Show password"

### 8.3 Contrast Ratios (WCAG 2.1 AA)

| Text Category | Minimum Ratio | Applies To |
|---------------|---------------|------------|
| Normal text | 4.5:1 | Text below 14sp bold or 18sp regular |
| Large text | 3:1 | Text at 14sp bold or 18sp regular and above |

The color palette is designed to meet these ratios:
- `onPrimary` (#FFFFFF) on `primary` (#5170FF) — contrast ratio ≥ 4.5:1
- `onSurface` (#1B1B1F) on `surface` (#FEFBFF) — contrast ratio ≥ 4.5:1
- `onBackground` (#1B1B1F) on `background` (#FEFBFF) — contrast ratio ≥ 4.5:1
- `onSurfaceVariant` (#46464F) on `surface` (#FEFBFF) — contrast ratio ≥ 4.5:1
- Dark mode equivalents maintain the same minimum ratios

### 8.4 Additional Accessibility Guidelines

- Screens consuming `WindowInsets` ensure no content renders behind system bars
- Error messages are announced to screen readers via `isError` semantics on text fields
- Loading states provide descriptive text for TalkBack users
- Interactive elements use `Modifier.clickable` with semantic role annotations where appropriate
