# Meetup Destination — Tasks

## Phase 1 — Domain wiring

- [x] 1.1 Add `ClearMeetupDestinationUseCase` (validates `groupId`, delegates to `LocationRepository.clearMeetupDestination`).
- [x] 1.2 Add `ObserveMeetupDestinationUseCase` (passthrough to `LocationRepository.observeMeetupDestination`).
- [x] 1.3 Register all four meetup use cases in `UseCaseModule`: `Set`, `Clear`, `Observe`, `DetectArrival`.
- [x] 1.4 Extend `SystemEventType` with `MEETUP_DESTINATION_SET`, `MEETUP_DESTINATION_CLEARED`, `MEETUP_ARRIVED`.
- [x] 1.5 Extend `SystemMessageRenderer` with the three new event branches (self/other actor, payload-driven name).

## Phase 2 — Map UI

- [x] 2.1 Extend `GlobalMapUiState` with `meetupDestination`, `meetupDestinationDistanceText`, `meetupDestinationEtaText`, `pendingDestinationPick`, `showSetDestinationSheet`, `arrivedDestinationKey`.
- [x] 2.2 In `GlobalMapViewModel`, observe destination per active group filter; cancel previous job on filter change.
- [x] 2.3 Implement `confirmDestinationPick(groupId, name)` and `clearDestination(groupId)` in the VM, including system-message authoring and `conversationId` resolution.
- [x] 2.4 Implement `geocodeAddress(lat, lng)` helper in the VM (best-effort, swallow errors).
- [x] 2.5 Recompute `meetupDestinationDistanceText`/`EtaText` whenever destination, my location, or my speed changes.
- [x] 2.6 Implement arrival detection in `processLocationUpdates`: call `DetectArrivalUseCase.hasArrived` for the local user; if newly arrived, write `MEETUP_ARRIVED`.
- [x] 2.7 Add long-press handler on the `GoogleMap` in `GlobalMapScreen` → calls `viewModel.onMapLongClick(latLng)`.
- [x] 2.8 Add `SetMeetupDestinationSheet` composable with name field + group picker.
- [x] 2.9 Render `MeetupDestinationCard` above the FAB column when destination is active.
- [x] 2.10 Render destination pin marker on the map.

## Phase 3 — Group Info

- [x] 3.1 Extend `GroupInfoUiState` with `meetupDestination`.
- [x] 3.2 Inject `ObserveMeetupDestinationUseCase`, `SetMeetupDestinationUseCase`, `ClearMeetupDestinationUseCase` into `GroupInfoViewModel`; collect destination on init.
- [x] 3.3 Implement `setMeetup(latitude, longitude, name, address)` and `clearMeetup()` in the VM with system-message authoring.
- [x] 3.4 Add a "Meetup" section + row to `GroupInfoScreen` with picker / action sheet flow.

## Phase 4 — Chat live-meetup integration

- [x] 4.1 Add `meetupDestination` to `ChatUiState`.
- [x] 4.2 Inject `ObserveMeetupDestinationUseCase` into `ChatViewModel`; observe only when `conversation.groupId != null`.
- [x] 4.3 Pass destination through `ChatScreen` → `LiveMeetupSheet`.
- [x] 4.4 Update `LiveMeetupSheet` to render the "Meet at" pill and include the destination pin in the embedded map.

## Phase 5 — Firestore rules

- [x] 5.1 Tighten `groups/{groupId}` update rule so writes affecting `meetupDestination` require membership.

## Phase 6 — Verification

- [x] 6.1 Run `getDiagnostics` on every changed file; resolve issues.
- [x] 6.2 `./gradlew :app:compileDebugKotlin` clean.
- [~] 6.3 Unit tests not run — `:app:compileDebugUnitTestKotlin` was already broken on `main` (every `ChatViewModel(...)` test call site is missing `getOrCreateDirectConversationUseCase` + `systemMessageWriter`, plus several unrelated `ConversationRow` / `GroupInfoViewModelTest` / `MessageRepositoryPaginationTest` breaks). My change adds one extra missing parameter (`observeMeetupDestinationUseCase`) to the same broken constructor calls. No tests that compile today regress.
