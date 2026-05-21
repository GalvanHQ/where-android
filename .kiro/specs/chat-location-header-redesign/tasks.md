# Chat Location Header Redesign

## Problem
The chat screen's location sharing UI (banner, MiniMapOverlay, bottom sheet, 📍 button) feels disconnected and inferior to the map screen. Location should be the HERO of the chat when a meetup is happening.

## Solution: Persistent Location Header
Replace the separate banner + MiniMapOverlay + toggle button with a single **LiveMeetupHeader** component.

## Implementation Tasks

### Task 1: Create `LiveMeetupHeader.kt`
**Path:** `app/src/main/java/com/ovi/where/presentation/chat/components/LiveMeetupHeader.kt`

A 130dp strip below the app bar that shows:
- Embedded mini Google Map (fills the strip, rounded bottom corners)
- Pulsing avatar markers for all active sharers (reuse `PulsingAvatarMarker` from MiniMapOverlay)
- Bottom overlay row: avatar chips of sharers + distance/ETA text
- Right side: "Share" pill button (if not sharing) OR "Sharing • 45m" status pill (if sharing, tappable to stop)
- Tap the map area → navigate to full map screen with this group/friend pre-filtered
- Auto-zoom camera to fit all sharers (same logic as current MiniMapOverlay)
- Shows automatically when ANY participant is sharing (you or them)
- Hides with smooth collapse animation when no one is sharing

### Task 2: Update ChatScreen.kt
- Remove the `LiveLocationSharingBanner` usage
- Remove the `MiniMapOverlay` usage and the `hasAutoShownMiniMap` logic
- Remove the `onMapTap` header button (no longer needed — the header IS the map)
- Add `LiveMeetupHeader` between the app bar and the message list
- The header's "Share" button calls `viewModel.onLocationShareButtonTap()` (opens duration picker)
- The header's "Sharing" pill taps → `viewModel.stopLiveLocationSharing()`
- The header's map tap → navigate to map screen (same as current `onExpandToFullMap`)

### Task 3: One-tap share (📍 button improvement)
- Single tap: start sharing with last-used duration (default 1h) — no bottom sheet
- Long press: open the duration picker bottom sheet for customization
- After starting, the header appears immediately showing your pin

### Task 4: Remove dead code
- Delete `MiniMapOverlay.kt` (functionality merged into LiveMeetupHeader)
- Remove `showMiniMap` state from ChatViewModel
- Remove `toggleMiniMap()` function
- Clean up unused imports

### Task 5: Sync with map screen state
- LiveMeetupHeader reads from `uiState.cachedLocations` (same data source)
- "Sharing" status derived from `locationRepository.getTargetExpiries()[thisTargetId]`
- No separate `isLiveLocationSharingActive` boolean needed — derive from repo state

## Key Files to Modify
- `app/src/main/java/com/ovi/where/presentation/chat/ChatScreen.kt`
- `app/src/main/java/com/ovi/where/presentation/chat/ChatViewModel.kt`
- `app/src/main/java/com/ovi/where/presentation/chat/components/LiveMeetupHeader.kt` (NEW)
- `app/src/main/java/com/ovi/where/presentation/chat/components/MiniMapOverlay.kt` (DELETE)
- `app/src/main/java/com/ovi/where/presentation/chat/components/LocationShareSheet.kt` (keep LiveLocationSharingBanner for now as fallback)

## Design Reference
- Height: 130dp (compact but shows enough map context)
- Map: no gestures (tap navigates to full map), zoom 15 for single sharer, auto-bounds for multiple
- Avatar markers: same `PulsingAvatarMarker` style as current MiniMapOverlay
- Bottom overlay: semi-transparent surface with avatar row + status
- "Share" button: primary colored pill, same style as map screen's FAB but inline
- Animations: expand/collapse with `AnimatedVisibility` (expandVertically + fadeIn)
