# Meetup Destination — Requirements

## Background

The Where app's primary value prop is "stop asking friends where they are when planning a meetup". Live-location sharing is one half of that. The other half — set a common destination so everyone can see distance + ETA from where they currently are — is half-built: model, repository APIs, two use cases, and the `MeetupDestinationCard` composable all exist, but nothing wires them into the running app. This spec finishes the feature.

## User stories

### 1. Set a meetup point from the map

**As a** group member already on the global map,
**I want** to long-press anywhere on the map to set it as the meetup point for one of my groups,
**so that** everyone in the group can see distance and ETA to the agreed-upon spot without me having to text them coordinates.

Acceptance:
1.1 Long-pressing the map opens a bottom sheet that lists the user's groups (including any active group filter at the top).
1.2 The sheet shows the picked latitude/longitude and a reverse-geocoded address (best-effort via Android `Geocoder`).
1.3 The sheet has an editable "name" field (default = first line of geocoded address, or "Meetup point").
1.4 Tapping "Set meetup point" calls `SetMeetupDestinationUseCase` and dismisses the sheet.
1.5 If the geocoder fails or returns nothing, the sheet still sets the destination using just the name and coordinates.
1.6 The sheet only appears when at least one group is loaded; otherwise a snackbar tells the user to create or join a group first.

### 2. See the active destination on the map

**As a** group member,
**I want** the map to clearly show the meetup point and my distance/ETA to it,
**so that** I know where to go and when I'll arrive.

Acceptance:
2.1 When `observeMeetupDestination(groupId)` emits an active destination for the currently filtered group, the map shows a destination marker (`MeetupDestinationCard` flag icon) at that lat/lng.
2.2 A `MeetupDestinationCard` floats above the bottom sheet, showing the destination name, address, my distance, and my ETA.
2.3 Distance is formatted using the existing `LocationUtils.formatDistance` helper.
2.4 ETA uses the same heuristic as the friend-ETA computation (assume 13.9 m/s ≈ 50 km/h when no live speed available).
2.5 The card hides automatically when no destination is set or when the user has no active group filter.

### 3. Clear the destination

**As a** group member,
**I want** to clear the meetup point when the meetup is over,
**so that** stale destinations don't clutter the map.

Acceptance:
3.1 Tapping the close button on the `MeetupDestinationCard` calls `clearMeetupDestination(groupId)`.
3.2 An "Are you sure?" confirmation appears before clearing.
3.3 The destination disappears from the map and from any group member's view in real time.

### 4. Manage destination from Group Info

**As a** group member,
**I want** a clearly labelled "Meetup point" row in Group Info,
**so that** I can set, view, or clear the destination without being on the map screen.

Acceptance:
4.1 Group Info screen renders a dedicated "Meetup" section with a single row: leading flag icon, "Meetup point" title, and either the destination name or "None set" as the subtitle.
4.2 Tapping the row when no destination is set opens a map picker dialog (full-screen Compose dialog wrapping a Google Map with a center pin).
4.3 Tapping the row when a destination is already set opens an action sheet with "Show on map" and "Clear meetup point" options.
4.4 Confirming "Clear" calls the same `clearMeetupDestination` API.

### 5. System messages in the group chat

**As a** group chat participant,
**I want** to see a centered system line whenever someone sets, clears, or arrives at the meetup point,
**so that** the chat history reflects the meetup coordination flow.

Acceptance:
5.1 Setting a destination authors a `MEETUP_DESTINATION_SET` system message via `SystemMessageWriter` with payload `name` (and optional `address`).
5.2 Clearing a destination authors a `MEETUP_DESTINATION_CLEARED` system message.
5.3 When the local user enters the arrival radius (`AppConstants.ARRIVAL_RADIUS_METERS`, 100m) for the first time during the active session, the client authors a `MEETUP_ARRIVED` system message keyed off `(eventType, conversationId, sessionStartedAt, actorId)` so duplicate location frames don't double-post.
5.4 Existing `SystemMessageRenderer` is extended with substitution rules for the three new event types, with self/other pronoun handling consistent with the rest of the renderer.
5.5 No FCM push is sent for these system messages — same suppression contract as every other `SystemMessageWriter` event (Requirement 8.1 of `group-system-messages`).

### 6. Reflect destination in the live meetup sheet

**As a** chat participant opening the Live Meetup sheet,
**I want** to see the destination pin on the embedded map preview and the destination name as a header,
**so that** the in-chat surface mirrors what the global map shows.

Acceptance:
6.1 When `observeMeetupDestination(conversation.groupId)` emits a destination, `LiveMeetupSheet` shows a small "Meet at: {name}" pill above the duration picker.
6.2 The embedded map preview includes the destination pin alongside live sharer markers.
6.3 The map auto-zooms to fit both the destination and all sharers.
6.4 The sheet stays unchanged for direct (non-group) conversations — destinations are group-scoped only.

### 7. Authorization & data integrity

**As a** group member,
**I want** the meetup point to only be writable by group members,
**so that** outsiders can't pollute group state.

Acceptance:
7.1 Firestore rules restrict updates to the `meetupDestination` field on a group document to authenticated users in the group's `memberIds` array.
7.2 Other group fields keep their current update rule (any authenticated user) to avoid scope creep — a separate spec already covers tightening the broad group write rule.
7.3 The rule denial is verified via `firebase emulators` smoke test in the design notes (manual, not automated).

## Out of scope

- Google Places autocomplete. Long-press + reverse geocode is enough for v1.
- Geofencing API. We continue to detect arrival client-side over the existing location stream.
- Multiple simultaneous destinations per group.
- Per-user "I'm here" reactions on the destination pin (parking lot for v2).
- Direct-conversation (1-to-1) meetup destinations. Groups only for v1 because Firestore rules and chat surfaces are group-scoped.

## Non-functional requirements

- **No new Firestore reads in the steady state.** The destination listener is one extra snapshot listener per active group filter. We keep this count bounded by listening only when a filter is active or when the user is in `GroupInfoScreen` for that group.
- **Battery.** No new background work, no new location services. The arrival check piggybacks on each location frame already being processed in the map view model.
- **Offline.** Setting a destination while offline queues the Firestore write through Firestore's existing offline persistence — no app-level retry queue needed.
