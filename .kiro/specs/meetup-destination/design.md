# Meetup Destination — Design

## Goals

Wire the existing meetup-destination plumbing into the running app so the feature works end-to-end: a group member can set a point on the map, every other member sees it with distance + ETA, the chat timeline gets a system message, and the destination is clearable from both the map and Group Info.

## Existing pieces (already in repo)

- `domain/model/MeetupDestination.kt` — data class.
- `domain/repository/LocationRepository` — `setMeetupDestination`, `clearMeetupDestination`, `observeMeetupDestination`.
- `data/repository/LocationRepositoryImpl` — Firestore-backed implementation reading the `meetupDestination` field on the group document.
- `domain/usecase/location/SetMeetupDestinationUseCase.kt`
- `domain/usecase/location/DetectArrivalUseCase.kt`
- `presentation/map/MeetupDestinationCard.kt` — card composable.
- `core/constants/AppConstants.ARRIVAL_RADIUS_METERS = 100.0`.

## What's missing

1. DI: none of the use cases are exposed in `UseCaseModule`.
2. UI surfaces: no map screen wiring, no Group Info row, no chat-sheet integration.
3. System messages: no event types or renderer rules for set/cleared/arrived.
4. Firestore rules: writes to `meetupDestination` are gated only by "is authenticated".

## Architecture

Standard MVVM as everywhere else in the codebase. No new repository, no new data layer.

```
[GlobalMapScreen] ──────► [GlobalMapViewModel]
                              │
                              ├─► observeMeetupDestination(groupId)  ─► LocationRepository
                              ├─► SetMeetupDestinationUseCase
                              ├─► ClearMeetupDestinationUseCase  (NEW)
                              ├─► DetectArrivalUseCase
                              └─► SystemMessageWriter (set / cleared / arrived)

[GroupInfoScreen] ─────► [GroupInfoViewModel]
                              ├─► ObserveMeetupDestinationUseCase  (NEW)
                              ├─► SetMeetupDestinationUseCase
                              ├─► ClearMeetupDestinationUseCase
                              └─► SystemMessageWriter

[ChatScreen] ─────────► [ChatViewModel]
                              └─► ObserveMeetupDestinationUseCase  (only when conversation.groupId != null)
```

Data flow per group filter change:

1. User taps a group filter in `GlobalMapScreen`.
2. `GlobalMapViewModel.setGroupFilter(filter)` sets `activeGroupFilter` and starts `meetupDestinationJob` collecting `observeMeetupDestination(filter.id)`. Cancels any prior job.
3. Each emission updates `GlobalMapUiState.meetupDestination`. The screen recomputes `meetupDestinationDistance` and `meetupDestinationEta` from `myLatitude/myLongitude`.
4. Each location frame from `processLocationUpdates` runs `DetectArrivalUseCase.hasArrived(...)` for the local user. The first transition from "not arrived" to "arrived" within a session triggers a `MEETUP_ARRIVED` system message keyed off `(eventType, conversationId, destination.setAt, currentUserId)` so identical frames don't double-post.

## Public API additions

### Use cases

- `ClearMeetupDestinationUseCase(LocationRepository)` — symmetric with `SetMeetupDestinationUseCase`, validates `groupId`, then delegates.
- `ObserveMeetupDestinationUseCase(LocationRepository)` — thin pass-through returning `Flow<MeetupDestination?>`.

Both registered in `UseCaseModule`. `SetMeetupDestinationUseCase` and `DetectArrivalUseCase` also need provider methods — they exist as classes but aren't `@Provides`'d.

### System event types

Extend `SystemEventType` enum:

```kotlin
enum class SystemEventType {
    // ... existing values ...
    MEETUP_DESTINATION_SET,
    MEETUP_DESTINATION_CLEARED,
    MEETUP_ARRIVED;
    // ...
}
```

`SystemMessageRenderer` adds:

| Event | Self actor | Other actor |
|---|---|---|
| `MEETUP_DESTINATION_SET` | `You set the meetup point at "{name}"` | `{actor} set the meetup point at "{name}"` |
| `MEETUP_DESTINATION_CLEARED` | `You cleared the meetup point` | `{actor} cleared the meetup point` |
| `MEETUP_ARRIVED` | `You arrived at the meetup point` | `{actor} arrived at the meetup point` |

`name` falls back to `"the meetup point"` if the payload is empty.

### `GlobalMapUiState` additions

```kotlin
data class GlobalMapUiState(
    // ... existing fields ...
    val meetupDestination: MeetupDestination? = null,
    val meetupDestinationDistanceText: String? = null,
    val meetupDestinationEtaText: String? = null,
    val pendingDestinationPick: PendingDestinationPick? = null,
    val showSetDestinationSheet: Boolean = false,
    val arrivedDestinationKey: String? = null  // last destination key the user has reported arrival for
)

data class PendingDestinationPick(
    val latitude: Double,
    val longitude: Double,
    val address: String?
)
```

`arrivedDestinationKey` is the deterministic id `"${groupId}_${destination.setAt}"`. Once the user sends `MEETUP_ARRIVED` for that key, we don't send again until the destination is cleared or replaced (which changes the `setAt` epoch).

## UI wiring

### Global map

- New `mapLongClickListener` on the `GoogleMap`. Captures the lat/lng, kicks off a coroutine in the VM that calls `Geocoder` (already used in the project — see `LocationUtils`), and toggles `showSetDestinationSheet = true`.
- New composable `SetMeetupDestinationSheet`:
  - Shows the picked address (or "Choose a name").
  - `WhereTextField` for name (default = first geocoded address line).
  - Group picker chip row from `uiState.groups`.
  - `PrimaryButton("Set meetup point")` calls `viewModel.confirmDestinationPick(groupId, name)`.
- `MeetupDestinationCard` rendered inside the `BottomSheetScaffold` content area, anchored above the existing FAB column. Hidden when `meetupDestination == null` or `activeGroupFilter == null`.
- New `MarkerComposable` for the destination pin — large red flag icon in `Icons.Default.Flag`, similar styling to the friend pins.

### Group info

New section "Meetup" inserted between the Customize Chat section and the Shared Media section in `GroupInfoScreen`:

```
┌──────────────────────────────────────────────────────┐
│  Meetup                                              │
│  🏁  Meetup point                                    │
│      "Starbucks Downtown" / 0.4 km away              │
│      [tap → action sheet]                            │
└──────────────────────────────────────────────────────┘
```

Actions on tap:
- No destination set → opens **picker dialog** with a centered Google Map; user drags the map to position a center pin, hits "Set meetup point".
- Destination set → opens a `ModalBottomSheet` with two rows: "Show on map" (deep-links into GroupMap → GlobalMap with that group filter applied) and "Clear meetup point" (with confirm dialog).

`GroupInfoUiState` gains `meetupDestination: MeetupDestination?`. `GroupInfoViewModel` collects from `ObserveMeetupDestinationUseCase` once `groupId` is known.

### Live meetup sheet

`LiveMeetupSheet` already takes a list of `SharedLocation`s and renders them on a mini map. We add an optional `meetupDestination: MeetupDestination?` parameter:

- When non-null + has valid coords, a "Meet at: {name}" pill renders directly above the duration picker.
- The map preview includes a destination pin alongside the sharer markers, and the auto-bounds calculation includes the destination's lat/lng.

`ChatViewModel` collects the destination only when `conversation.groupId != null` and exposes `uiState.meetupDestination`. `ChatScreen` passes it to `LiveMeetupSheet`.

## System messages

Authoring is identical to the existing patterns in `GroupInfoViewModel` (rename / theme color / etc.):

- **Set:** in `GlobalMapViewModel.confirmDestinationPick(...)` and `GroupInfoViewModel.setMeetupDestination(...)`. After the repo call returns `Resource.Success`, resolve `conversationId` for the group, then write `MEETUP_DESTINATION_SET` with payload `mapOf("name" to name, "address" to address)`.
- **Cleared:** symmetric — write `MEETUP_DESTINATION_CLEARED` with payload `mapOf("name" to previousName)`.
- **Arrived:** in `GlobalMapViewModel.processLocationUpdates`, after the friend list is updated, run a single `DetectArrivalUseCase.hasArrived(...)` for the local user. Compare against the persisted `arrivedDestinationKey`; if the user newly arrived for an unseen `key`, write `MEETUP_ARRIVED` and update the key.

`SystemMessageWriter.buildDeterministicId` already includes the timestamp bucket, so retried writes within the same second collapse to a single document — no extra dedup needed.

## Firestore rules

```
match /groups/{groupId} {
  allow create: if isAuthenticated();
  allow read: if isAuthenticated();

  // Members can update general fields. Writes that touch `meetupDestination`
  // require the actor to be in memberIds.
  allow update: if isAuthenticated()
    && (
      !request.resource.data.diff(resource.data).affectedKeys().hasAny(['meetupDestination'])
      || (request.auth.uid in resource.data.memberIds)
    );

  allow delete: if isAuthenticated() && request.auth.uid == resource.data.createdBy;
  // ... existing subcollections unchanged ...
}
```

This adds a member-only constraint on `meetupDestination` updates without changing the existing broad-update behaviour for the rest of the doc — keeping the diff small and reversible.

## Validation

- `getDiagnostics` on every changed file.
- `./gradlew :app:compileDebugKotlin` to confirm it builds.
- The existing `SystemMessageRendererTest` covers all event types, so new branches must compile against the renderer's `when` exhaustively.
