# DESIGN.md — WHERE
### Visual Design Specification · v1.0
*Real-time location sharing & social coordination app for Android*

---

## 1. Design Vision

WHERE is a **premium social app** built around one core moment: knowing where your people are. The design language is confident, dark, and alive — like the city at night. It feels closer to Instagram or Snapchat than a utility app. Every screen should feel like it belongs on a flagship phone.

**Three words that define the visual identity:**
> **Glassy. Dynamic. Warm.**

- **Glassy** — layered surfaces, frosted overlays on maps, depth through translucency
- **Dynamic** — the app is always moving (live locations, timers, ETAs); the UI reflects that energy through motion and live indicators
- **Warm** — despite the dark palette, accent colors are vivid and approachable, not cold or clinical

---

## 2. Brand Personality

| Attribute | Description |
|---|---|
| Tone | Confident, friendly, premium |
| Feel | Social media meets navigation |
| Avoid | Generic maps app aesthetics, cold blues, over-engineered utility look |
| Inspired by | Instagram, Snapchat Maps, BeReal, Apple Maps dark mode |

---

## 3. Color System

### 3.1 Dark Mode (Primary — Default Experience)

Dark mode is the hero. All design decisions are made for dark first.

| Token | Hex | Usage |
|---|---|---|
| Background Primary | `#0D0D0F` | Main screen backgrounds |
| Background Secondary | `#1A1A1F` | Cards, list surfaces |
| Background Elevated | `#242429` | Bottom sheets, modals, elevated cards |
| Surface Glass | White 6% opacity + 16px blur | Map overlays, glassmorphism cards |
| Divider | White 7% opacity | Subtle separators between sections |

| Token | Hex | Usage |
|---|---|---|
| Accent Primary | `#6C63FF` | Brand identity, CTAs, active states, links |
| Accent Secondary | `#FF6B9D` | Live location indicator, active sharing, urgent actions |
| Accent Tertiary | `#00D4AA` | Arrival confirmation, success states, destination |
| Accent Warning | `#FFB84D` | ETA timers, duration warnings, countdowns |
| Error | `#FF5C5C` | Destructive actions, errors |

| Token | Hex | Usage |
|---|---|---|
| Text Primary | `#F0F0F5` | Main readable content |
| Text Secondary | `#8E8EA0` | Supporting labels, subtitles |
| Text Tertiary | `#4A4A58` | Hints, disabled text, timestamps |

### 3.2 Light Mode

Light mode is a clean, airy alternative — not the app's identity, but equally polished.

| Token | Hex | Usage |
|---|---|---|
| Background Primary | `#F5F5FA` | Slightly warm off-white, not pure white |
| Background Secondary | `#FFFFFF` | Cards, surfaces |
| Background Elevated | `#FFFFFF` | Sheets, modals with stronger shadow |
| Surface Glass | White 72% opacity + 20px blur | Light glassmorphism |
| Divider | Black 7% opacity | |

Accent colors shift slightly darker in light mode for proper contrast ratios:

| Dark Token | Light Equivalent |
|---|---|
| `#6C63FF` | `#5A52E0` |
| `#FF6B9D` | `#E8527A` |
| `#00D4AA` | `#00B896` |
| `#FFB84D` | `#F0A030` |

| Token | Hex |
|---|---|
| Text Primary | `#0D0D1A` |
| Text Secondary | `#6B6B80` |
| Text Tertiary | `#ABABBC` |

### 3.3 Semantic / Status Colors

These colors carry specific meaning throughout the app and must be used consistently.

| Meaning | Color | Where Used |
|---|---|---|
| Live / Sharing | `#FF6B9D` (Accent Secondary) | Pulsing ring on avatar, Live chip, FAB active state |
| Arrived | `#00D4AA` (Accent Tertiary) | Member tile highlight, destination ring, checkmark |
| Idle / Offline | `#4A4A58` | Avatar desaturated, last seen label |
| ETA | `#FFB84D` (Accent Warning) | ETA badge, sharing duration timer |
| Admin | `#6C63FF` (Accent Primary) | Admin badge on group member list |
| Unread | `#6C63FF` (Accent Primary) | Message badge, notification dot |

---

## 4. Typography

### 4.1 Font Families

Three fonts, each with a specific role:

**Sora** — Used for all headings, display text, screen titles, and the app logo. Rounded terminals, modern and friendly. Gives the app its social character.

**DM Sans** — Used for all body text, labels, captions, UI copy, and form fields. Highly legible at small sizes. Neutral and clean.

**DM Mono** — Used exclusively for numerical data that changes in real time: ETAs, distances, countdown timers, sharing durations. The monospaced width prevents layout jumping as numbers update.

### 4.2 Type Scale

| Role | Size | Weight | Font | Used For |
|---|---|---|---|---|
| Display Large | 28sp | SemiBold | Sora | Screen titles, splash |
| Display Medium | 24sp | SemiBold | Sora | Section headers |
| Title Large | 20sp | SemiBold | Sora | Group names, chat headers |
| Title Medium | 18sp | Medium | DM Sans | Card titles, sheet headers |
| Title Small | 16sp | Medium | DM Sans | List item primary text |
| Body Large | 15sp | Regular | DM Sans | Chat messages, descriptions |
| Body Medium | 14sp | Regular | DM Sans | Secondary info, subtitles |
| Body Small | 12sp | Regular | DM Sans | Captions, timestamps |
| Label | 11sp | Medium | DM Sans | Chips, tags, badges |
| Mono | 14sp | Regular | DM Mono | ETA, distance, timers |
| Mono Small | 12sp | Regular | DM Mono | Compact numerical labels |

### 4.3 Typography Rules

- Never use DM Mono for anything other than live-changing numbers and distances.
- Headings (Sora) should never appear at Body Medium size or smaller — use DM Sans for small labels.
- Line height for chat messages: 1.5× the font size.
- Letter spacing for Label text: +0.5px for readability at small sizes.

---

## 5. Shape & Spacing

### 5.1 Corner Radius System

All shapes in the app follow a consistent rounding scale. Rounder = more interactive/social. Sharper = informational.

| Token | Value | Used For |
|---|---|---|
| Radius XS | 6dp | Tiny inline badges, status dots |
| Radius Small | 8dp | Chips, tags, small action pills |
| Radius Medium | 12dp | List tiles, menu items, image cards |
| Radius Large | 16dp | Main cards, map overlays |
| Radius XL | 24dp | Bottom sheets, modals, expanded panels |
| Radius Pill | Full (999dp) | Buttons, text inputs, floating chips |
| Radius Circle | 50% | Avatars, location markers, icon buttons |

### 5.2 Spacing System

Based on an 8dp base unit:

| Token | Value | Used For |
|---|---|---|
| Space XS | 4dp | Icon-to-label tight coupling |
| Space SM | 8dp | Internal chip padding, dense layouts |
| Space MD | 12dp | Avatar-to-text gap, list tile internals |
| Space LG | 16dp | Screen horizontal padding, card padding |
| Space XL | 24dp | Section gaps, between major content blocks |
| Space 2XL | 32dp | Top spacing for bottom sheets, hero sections |

**Screen edge padding:** Always 16dp horizontal. Never let content touch screen edges.

### 5.3 Elevation & Depth

The app uses a three-level depth system — not Material's dp-based shadows, but intentional visual layering:

| Level | Description | Used For |
|---|---|---|
| Ground | The map or base background | The map itself |
| Level 1 | Subtle soft shadow (black 25% at 8dp blur) | Cards, list items |
| Level 2 | Medium shadow (black 40% at 24dp blur) | Bottom sheets, drawers |
| Level 3 | Branded glow shadow (Accent Primary 30% at 32dp blur) | FABs, primary CTAs, active modals |
| Glass | Background blur + semi-transparent surface | Map overlay chips, glassmorphism panels |

---

## 6. Components

### 6.1 Avatar

The avatar is the most important repeating element — it represents a real person in a live context.

**Sizes:** 32dp · 40dp · 48dp · 56dp · 72dp · 96dp

**Default state:** Profile photo cropped to circle.

**Fallback (no photo):** Gradient background generated from the user's name hash (always unique per user), white initials centered in Sora Medium.

**Live Ring:** When a user is actively sharing their location, a 2.5dp animated ring appears around their avatar. The ring cycles between Accent Primary and Accent Secondary in a slow gradient animation. This ring is the universal signal that someone is live.

**Offline state:** Avatar image desaturated to grayscale at 50% opacity.

**Status Dot:** A 10dp circle anchored to the bottom-right of the avatar. Color maps to semantic colors (Live = pink, Arrived = teal, Offline = grey).

**Stacked avatars:** When showing a group's member count in a small space, avatars overlap at 60% of their width. Max 3 visible, then a "+N" circle using Background Elevated background and Text Secondary text.

---

### 6.2 Map Location Marker

Each user on the map is represented by a custom marker — not a generic pin.

**Structure:** The user's avatar (40dp circle) sits inside a slightly larger colored ring (44dp). Below the circle, a small teardrop tail points to the exact location. The entire marker has a soft drop shadow (Level 2).

**Live state:** The ring pulses — it scales from 100% to 110% and back, infinitely, at a 1.5-second interval. Color = Accent Secondary (pink).

**Arrived state:** The ring turns Accent Tertiary (teal) and shows a small checkmark badge overlaid on the bottom-right of the avatar.

**Tapping a marker:** Opens a compact bottom sheet with that member's name, ETA, distance, and a "Navigate to them" option.

**Clustering:** When 2+ users are very close together, their markers merge into a stacked group — showing up to 3 overlapping avatars with a "+N" count if there are more.

---

### 6.3 Group Card

Used in the Groups screen list.

**Layout:** Horizontal row, 72dp tall.

**Left:** Stacked group member avatars (48dp composite, max 3 overlapping).

**Center:** Group name in Title Small SemiBold. Below it: "X members · Y live now" in Body Small, Text Secondary. The "Y live now" portion uses Accent Secondary color when Y > 0.

**Right:** If anyone is live, show a Live Chip. Otherwise, show last activity timestamp in Body Small, Text Tertiary.

**Background:** Background Secondary. On press: Background Elevated with scale 0.98.

---

### 6.4 Member List Tile

Used inside group detail, ETA screen, and map bottom sheet.

**Layout:** Horizontal row, 64dp tall, 16dp horizontal padding.

**Left:** Avatar (40dp) with live ring if applicable.

**Center:** Display name in Body Large SemiBold. Below: context-sensitive subtitle — "Sharing for 24 min" · "ETA 8 min" · "Last seen 2h ago" — in Body Small, Text Secondary.

**Right:** Distance badge (Mono Small, pill shape, Accent Primary background at 12% opacity, Accent Primary border) + vertical three-dot menu icon.

---

### 6.5 Buttons

**Primary Button**
The main call-to-action. Used once per screen maximum.
- Height: 52dp
- Shape: Pill
- Background: Gradient from Accent Primary to a 20% lighter tint, left to right
- Text: 16sp DM Sans SemiBold, white
- Shadow: Level 3 (Accent Primary glow)
- Press state: Scale down to 96%, slightly darker background
- Loading state: Spinner replaces label, same size, white
- Disabled: 30% opacity, no shadow

**Secondary Button**
For supporting actions alongside a primary.
- Height: 52dp
- Shape: Pill
- Background: Transparent
- Border: 1.5dp solid Accent Primary
- Text: 16sp DM Sans SemiBold, Accent Primary color
- Press state: Accent Primary at 8% opacity background fill

**Text Button / Link**
For low-emphasis actions.
- No background, no border
- Text: Accent Primary color, underline on press
- Used for: "Resend OTP", "Sign in instead", destructive actions in red

**Icon Button**
Circular tap target, 40dp minimum.
- Icon: 24dp
- Background: Transparent (or Background Elevated for elevated contexts)
- Ripple: Circular, accent-tinted

---

### 6.6 Input Field

- Shape: Radius Large (12dp)
- Background: Background Elevated
- Border: 1dp Divider at rest · 1.5dp Accent Primary when focused · 1.5dp Error red when invalid
- Height: 52dp for single-line · Auto-expanding for multi-line
- Padding: 16dp horizontal, 14dp top and bottom
- Label: Floating — starts inside field as placeholder, animates up to top edge on focus (Material 3 behavior)
- Text: Body Large, Text Primary
- Prefix icon: 24dp, Text Secondary color, 12dp gap to text
- Helper text: Body Small below field — Text Secondary for hints, Error color for validation messages

---

### 6.7 Chat Message Bubble

**Sent messages (right-aligned):**
- Background: Accent Primary, solid
- Shape: Radius Large on all corners except bottom-right which is Radius XS (4dp) — gives the "tail" effect
- Text: White, Body Large
- Max width: 75% of screen width
- Padding: 12dp horizontal, 10dp vertical

**Received messages (left-aligned):**
- Background: Background Elevated
- Shape: Radius Large on all corners except bottom-left which is Radius XS — mirrored tail
- Text: Text Primary, Body Large

**Timestamp:** Body Small, Text Tertiary, shown below the bubble, right-aligned for sent, left-aligned for received.

**Date separator:** Centered pill — "Today", "Yesterday", or date string — Background Elevated, Text Tertiary, Body Small. Appears between message groups when the date changes.

**System messages:** Centered, italic, Text Tertiary, Body Small, no bubble. Used for events like "Ovi started sharing location."

**Reactions:** Small pill chips below the bubble. Each shows emoji + count. Tapping adds your reaction or removes if already added.

**Location card bubble:** A special message type shown when someone shares their live location in chat.
- Shows a static map thumbnail (rounded corners, Radius Medium)
- Label: "Ovi is sharing live location" in Body Medium SemiBold
- Duration chip below label
- "View on Map" text button in Accent Primary
- Background: Background Elevated, same shape as received bubble

---

### 6.8 Live Sharing Chip

Indicates that someone is actively broadcasting their location.

- Background: Accent Secondary at 15% opacity
- Border: 1dp Accent Secondary
- Shape: Pill
- Left: Animated 4dp circle dot, Accent Secondary color, pulsing opacity (full to 30% and back, 1-second loop)
- Text: "Live · 42:17" — DM Mono, Accent Secondary color — the timer counts down in real time
- Padding: 8dp horizontal, 5dp vertical

---

### 6.9 ETA Badge

- Background: Accent Warning at 15% opacity
- Border: 1dp Accent Warning
- Shape: Pill
- Left icon: Clock, 14dp, Accent Warning color
- Text: "ETA 12 min" — DM Mono Small, Accent Warning color
- Padding: 8dp horizontal, 5dp vertical

---

### 6.10 Distance Badge

- Background: Accent Primary at 12% opacity
- Border: 1dp Accent Primary
- Shape: Pill
- Text: "2.4 km" — DM Mono Small, Accent Primary color

---

### 6.11 Bottom Navigation Bar

The main navigation. 4 tabs: Map, Groups, Chat, Profile.

- Background: Background Secondary with a 1dp top divider
- Height: 64dp + system navigation inset (handles gesture navigation and classic nav bar)
- Selected tab: Icon + label below it, both in Accent Primary. Pill-shaped subtle background behind the icon at Accent Primary 12% opacity.
- Unselected tab: Icon only, Text Tertiary color. No label.
- Badge: 8dp filled circle in Accent Secondary, top-right of icon, for unread messages or notifications. Shows count if ≥ 1.

---

### 6.12 Bottom Sheet

All secondary flows open as bottom sheets — never full navigation pushes unless the screen is a major destination.

- Background: Background Elevated
- Top corners: Radius XL (24dp). Bottom corners: 0 (full bleed to edge)
- Handle bar: 32dp wide × 4dp tall, Text Tertiary color, centered, 12dp from top of sheet
- Scrim: Black at 50% opacity behind the sheet
- Drag behavior: Snap between half-expanded (shows key content) and full-expanded (all content). Swipe down past half to dismiss.
- Content starts 24dp below the handle bar

---

### 6.13 Destination Pin (Map)

The marker for the group's meetup destination — visually distinct from user markers.

- Shape: Classic teardrop pin
- Color: Accent Tertiary (teal-mint), filled
- Center icon: Flag or star icon, white
- Size: 48dp tall
- Pulse effect: Expanding concentric ring that animates outward from the pin base, fading from Accent Tertiary at full opacity to transparent. Infinite loop, 2-second interval. Communicates "this is the target."

---

### 6.14 Notification / Toast

Appears at the top of the screen (below status bar) for location-based alerts. Appears at the bottom for general app toasts.

- Background: Background Elevated
- Shape: Radius Medium (12dp)
- Shadow: Level 2
- Left: Semantic icon (24dp) in the relevant accent color
- Content: Title in Body Large SemiBold + subtitle in Body Small, Text Secondary
- Enters with a slide-down + fade-in. Exits after 4 seconds with slide-up + fade-out.
- Tap to open the related screen.

---

## 7. Screen Designs

### 7.1 Splash Screen

Full dark Background Primary. App logo centered — a stylized location pin composed of the letter "W" and a teardrop drop, rendered in Accent Primary gradient. App name "WHERE" in Sora Bold 32sp below. Tagline "Know where they are." in DM Sans Regular 16sp, Text Secondary. Everything fades in with a 300ms stagger — logo first, then name, then tagline.

---

### 7.2 Login Screen

No card container — inputs float directly on the dark background. Subtle radial glow at the top center of the screen using Accent Primary at 8% opacity, giving a sense of depth.

Logo and app name at the top third of the screen. Below: phone/email input, then password input. Primary button "Continue" full width. Divider with "or" text. Google sign-in secondary button with Google logo. At the bottom: "Don't have an account? Sign up" with "Sign up" in Accent Primary.

Error state: input borders turn Error red. A small inline error message appears below in Body Small, Error color.

---

### 7.3 OTP Verification Screen

Six individual input boxes in a horizontal row. Each box: 48dp × 56dp, Radius Medium, Background Elevated, 1dp Divider border. Active/focused box: 1.5dp Accent Primary border with a soft Accent Primary glow shadow. Entered digit shown in Text Primary, Body Large SemiBold, centered.

Auto-advance to next box on digit entry. Auto-submit when all 6 are filled.

Below the boxes: "Resend code in 0:45" in DM Mono, Text Secondary. When timer expires, "Resend Code" becomes an active text button.

---

### 7.4 Profile Setup Screen

Step indicator at top — three small dots, current step dot is Accent Primary, others are Text Tertiary.

**Step 1 — Photo & Name:** Large circle avatar picker centered (96dp), tapping reveals camera/gallery options. Below: display name input.

**Step 2 — Username:** Username input with "@" prefix. Real-time availability check — green checkmark (Accent Tertiary) when available, red X (Error) when taken, spinner while checking.

**Step 3 — Permissions:** Illustrated cards for Location (always on + background) and Notifications. Each card has an icon, a brief one-line explanation, and an "Allow" button. Designed to feel like a gentle onboarding moment, not a scary permissions dialog.

---

### 7.5 Map Screen (Main Home)

The map is the primary surface of the app. It fills the entire screen edge to edge — beneath the status bar and navigation bar.

**Top overlay — Group Switcher Bar:**
A glassmorphism card pinned at the top (below the status bar). Shows current group name + live member count. Tap to reveal a dropdown of all groups. Right side: notification bell and search icons. Background: Surface Glass (Background Elevated at 80% opacity + 16px blur).

**Member avatar tray — bottom overlay:**
A persistent bottom panel anchored to the bottom. By default shows at a "peek" height — just the handle + a horizontal scrollable row of member avatars with their name labels underneath. Avatars have live rings when active.

Drag up to expand into a full member list with ETA badges, distance badges, and status.

Tapping an avatar snaps the map to that person's location and shows a member detail card.

**Share Location FAB:**
Bottom-right corner, 56dp circle, above the bottom panel.
- Inactive: Accent Primary fill, location arrow icon.
- Active/Sharing: Accent Secondary gradient fill, pulsing ring expanding outward from the FAB, "Stop" icon inside.
Tapping when inactive opens the Location Sharing Duration Sheet.

**Location Sharing Duration Sheet:**
Lists duration options as tappable rows: 15 min · 1 hour · 8 hours · Custom · Until I stop. Each row has a small icon and a right-side duration label. Primary button "Start Sharing" at the bottom of the sheet.

**Set Destination button:**
Inside the expanded member panel. Accent Tertiary text color, flag icon. Opens a map picker to drop a destination pin.

---

### 7.6 Groups Screen

Full-screen list with sticky top section.

**Top:** "Groups" title in Display Medium. Full-width search bar below it (pill shape, Background Elevated, search icon prefix). New group icon button top-right.

**List sections:**
- "Active Now" — groups where at least one person is sharing live. Shown first. Group cards use the Live Chip on the right.
- "All Groups" — all other groups below.

**Empty state:** Centered illustration, "No groups yet" in Title Medium, a supporting line of Body Medium text in Text Secondary, and a primary "Create Group" button.

**Create Group bottom sheet:**
Group name input at top. Circular group avatar (tap to customise or pick an emoji). Member search field with results list below. "Invite only" toggle. Primary "Create Group" button at the bottom.

---

### 7.7 Chat List Screen

Mirrors Instagram DMs or WhatsApp's conversation list.

**Top:** "Messages" title. Search bar.

**List:** Each row is a conversation tile.
- Left: Group avatar (48dp) or user avatar with live ring if active
- Center: Group/user name in Title Small SemiBold. Below: last message preview in Body Medium, Text Secondary, clipped to 1 line.
- Right: Timestamp in Body Small, Text Tertiary. Unread badge (Accent Secondary filled circle with white count) if unread messages exist.

Unread conversations have a Background Elevated tint to subtly distinguish them from read ones.

---

### 7.8 Chat Screen

**Header:** Back arrow left. Center: avatar (40dp) + name in Title Medium + "X members" or "Online" status below in Body Small, Text Secondary. Right: call icon + info icon.

**Message area:** Scrollable. Grouped by date with date separators. Supports all message bubble types from Section 6.7. System messages appear inline between bubbles.

**Input bar:** Pinned at the bottom, above the keyboard. Background: Background Secondary. Left: attachment icon. Center: pill-shaped text input (Background Elevated) with "Message..." placeholder. Right: location share icon + animated send button (circle, Accent Primary, icon scales on press).

When the keyboard is open, the input bar lifts with the keyboard. The message list shrinks to accommodate.

---

### 7.9 Profile Screen

**Top section:** Background uses a very subtle gradient — Background Primary at bottom blending to Accent Primary at 6% opacity at the top. Avatar (96dp) centered. Live ring visible if user is currently sharing. Display name in Title Large. Username in Body Medium, Text Secondary. "Edit Profile" secondary button (compact pill, not full width).

**Stats row:** Three equal columns — "Groups", "Friends", "Check-ins" — each with a number in Title Medium SemiBold above and a label in Body Small, Text Secondary below.

**Settings list:** Card-grouped rows below the stats.
- Account · Privacy & Location · Notifications · Appearance (includes dark/light toggle) · Help & Support
Each row: 56dp tall, icon left (24dp), label in Title Small, chevron right.

**Danger zone:** "Sign Out" as a full-width text button in Error red, at the very bottom. No card container — intentionally understated.

---

### 7.10 ETA / Destination Screen

Accessed when a group has an active destination set.

**Layout:** Top 40% is a focused map view showing only the destination pin and all member markers with dashed route lines connecting each member to the destination. Route lines are semi-transparent, one per member.

**Bottom 60%:** Scrollable member list.
- Section header: Destination name + "X arrived" counter
- Each tile: Member avatar + name + Distance badge + ETA badge
- Sorted: Arrived members pinned at the top with Accent Tertiary highlight background and checkmark. Others sorted by ETA ascending.

---

### 7.11 Notifications Screen

**Top:** "Notifications" title. "Mark all read" text button top-right.

**List:** Grouped into "New" and "Earlier."

**Notification tile types:**
- Location event: Location pin icon in Accent Primary. "Ovi started sharing in Weekend Squad" — name bolded.
- Arrival: Checkmark icon in Accent Tertiary. "Ovi arrived at Central Park."
- Group invite: User-plus icon in Accent Primary. "Nadia invited you to Work Lunch."
- Message: Chat icon in Accent Secondary. "Weekend Squad: 3 new messages."

**Tile anatomy:** Icon (32dp circle background) left · Text content center (title + subtitle) · Timestamp right (Body Small, Text Tertiary).

**Unread state:** Left edge 2dp bar in Accent Primary + Background Elevated tinted background.

**Read state:** Standard Background Secondary.

---

## 8. Motion & Animation

Motion is not decoration — it communicates live status, confirms actions, and guides attention.

### 8.1 Principles

- **Purposeful:** Every animation has a reason. If removing it doesn't break understanding, remove it.
- **Fast entrances, graceful exits:** Elements enter quickly (150–250ms), exit gently (200–300ms).
- **Physical feel:** Bottom sheets use spring physics, not linear easing. They should feel like real objects being dragged.
- **Live elements loop:** Anything representing a live/active state (rings, FABs, destination pins) loops infinitely at a calm, slow rhythm — never frantic.

### 8.2 Animation Reference

| Element | Behavior | Duration | Easing |
|---|---|---|---|
| Screen transition | Slide in from right + fade | 300ms | EaseInOut |
| Bottom sheet open | Slide up from bottom with spring overshoot | 350ms | Spring (medium bounce) |
| Bottom sheet close | Slide down, no overshoot | 250ms | EaseIn |
| FAB pulse (when live) | Scale 100% → 115% → 100%, infinite loop | 1500ms | EaseInOut |
| Avatar live ring | Gradient rotates around ring, continuous | 3000ms | Linear |
| Map marker pulse | Scale 90% → 110% → 90%, infinite | 1500ms | EaseInOut |
| Destination pin pulse | Expanding ring: scale 100% → 200%, opacity 100% → 0% | 2000ms | EaseOut, infinite |
| Button press | Scale to 96% | 100ms | EaseOut |
| Card press | Scale to 98%, ripple | 150ms | EaseOut |
| Typing indicator | 3 dots bouncing with 150ms stagger between each | 600ms | EaseInOut, infinite |
| Avatar stack entrance | Each avatar slides in from right, 50ms stagger | 200ms | EaseOut |
| Notification toast in | Slide down from top + fade in | 250ms | EaseOut |
| Notification toast out | Slide up + fade out | 200ms | EaseIn |
| Live chip counter | Number ticks over using a vertical flip animation | 300ms | EaseInOut |
| Map camera move | Smooth pan and zoom to target on marker tap | 500ms | EaseInOut |

### 8.3 Shared Element Transitions

When navigating from a group list item into the group's map or chat screen, the group avatar and name animate smoothly between their positions on the two screens. This gives the navigation a fluid, connected feel instead of a hard cut.

Applies to: Group Card → Map Screen, Group Card → Chat Screen.

---

## 9. Iconography

**Icon Style:** Rounded, 2dp stroke weight, line icons for most contexts. Filled/solid version used only for active/selected navigation tab states.

**Recommended set:** Lucide Icons — consistent stroke, friendly and modern, matches the app's warm personality.

**Size:** 24dp standard · 20dp in compact contexts (badges, chips) · 28dp for primary action buttons.

| Context | Icon Name |
|---|---|
| Map tab (nav) | map-pin |
| Groups tab (nav) | users |
| Chat tab (nav) | message-circle |
| Profile tab (nav) | circle-user |
| Share location | navigation |
| Stop sharing | circle-stop |
| Set destination | flag |
| Invite member | user-plus |
| Group info | info |
| More options | ellipsis-vertical |
| Send message | send |
| Attach file | paperclip |
| Camera | camera |
| Location in chat | map-pin |
| Live status | radio |
| Arrived | circle-check |
| ETA | clock |
| Offline | wifi-off |
| Back | arrow-left |
| Close | x |
| Settings row | chevron-right |
| Search | search |
| Notification | bell |

---

## 10. Empty States

Every screen that can be empty must have a designed empty state — not a blank screen.

**Structure of every empty state:**
1. Illustration — simple, single-color vector art in Accent Primary at 40% opacity. Friendly and on-topic.
2. Headline — Title Medium, Text Primary. Brief and human.
3. Supporting text — Body Medium, Text Secondary. One or two lines max.
4. CTA — Primary button if there's an action to take.

| Screen | Headline | Supporting Text |
|---|---|---|
| Groups (no groups) | "No groups yet" | "Create one or ask a friend to invite you" |
| Chat (no messages) | "Start the conversation" | "Share where you are to break the ice" |
| Notifications (empty) | "You're all caught up" | "We'll notify you when something happens" |
| Map (no one sharing) | "No one is sharing right now" | "Start sharing your location with the group" |

---

## 11. Accessibility

- All text must meet WCAG AA contrast ratio — 4.5:1 for body text, 3:1 for large text — in both dark and light modes.
- Tap targets: minimum 48dp × 48dp for all interactive elements, even if the visual is smaller.
- Avatars always include a content description (display name) for screen readers.
- Live/active states must not be communicated by color alone — always pair the pink ring with a label ("Sharing live").
- Animations respect the system-level "Reduce Motion" setting — all infinite loops and non-essential transitions are disabled when this is enabled.
- Font sizes respect system font scale — layouts must not break when the user has increased their system font size.

---

## 12. Do's and Don'ts

**Do**
- Use glassmorphism only on top of the map — it earns its place there.
- Let the map breathe — overlays should reveal the map, not cover it.
- Keep ETA and distance in DM Mono so number updates don't shift the layout.
- Show the live ring on every avatar wherever it appears if that user is sharing.
- Use spring physics for any draggable or dismissible surface.
- Use skeleton screens for loading states that match the actual layout shape.

**Don't**
- Use pure black (`#000000`) — use Background Primary (`#0D0D0F`) instead.
- Use more than one primary button per screen.
- Add borders to cards in dark mode — use elevation (shadow + background color difference) instead.
- Use gradients on backgrounds — reserve them for buttons, avatar fallbacks, and the live ring.
- Show raw coordinates anywhere in the UI — all location data is expressed as human-readable distance or ETA.
- Use placeholder grey boxes for loading states.

---

*DESIGN.md · WHERE App · v1.0 · Ismam Hasan Ovi*
