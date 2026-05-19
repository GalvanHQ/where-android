# Requirements Document

## Introduction

This feature optimizes the WHERE app's location sharing system to drastically reduce Firestore costs (reads and writes), implement a working Socket.IO location relay on the server, adopt a cache-first architecture using Room as the single source of truth, and deliver a professional UX with instant display and visual feedback. The key insight driving this optimization is that Firestore charges per document read — not per field — so denormalizing data into fewer documents is the primary cost-reduction strategy.

## Glossary

- **Location_Repository**: The data layer component (`LocationRepositoryImpl`) responsible for all location read/write operations across Firestore, Socket.IO, and Room
- **Location_Tracking_Service**: The Android foreground service (`LocationTrackingService`) that collects GPS coordinates during active sharing sessions
- **Socket_Relay_Server**: The Node.js Socket.IO server (`socket.js`) responsible for relaying real-time location updates between connected clients
- **Room_Cache**: The local SQLite database (via Room) storing `SharedLocationEntity` records as the single source of truth for display
- **Active_Locations_Collection**: The Firestore `activeLocations/{uid}` collection holding one document per sharing user with all location and profile data
- **Legacy_Subcollection**: The deprecated `groups/{groupId}/locations/{uid}` and `directLocationShares/{shareId}/locations/{uid}` Firestore paths
- **Global_Map_ViewModel**: The primary map screen ViewModel (`GlobalMapViewModel`) consuming location data for display
- **Map_ViewModel**: The per-group map screen ViewModel (`MapViewModel`) currently using legacy listeners
- **Denormalized_Document**: A Firestore document enriched with fields from related entities (e.g., displayName, photoUrl) to eliminate separate reads
- **Write_Throttle**: The mechanism that suppresses Firestore writes occurring within a configurable interval of the previous write
- **Location_Update_Frame**: A Socket.IO event payload containing userId, latitude, longitude, accuracy, speed, bearing, and timestamp
- **Firestore_Fallback**: The mechanism that activates a Firestore snapshot listener when Socket.IO is unavailable

## Requirements

### Requirement 1: Remove Legacy Dual-Write

**User Story:** As a developer, I want to eliminate the legacy dual-write pattern, so that every location operation costs exactly one Firestore write instead of two.

#### Acceptance Criteria

1. WHEN the Location_Repository starts a location sharing session, THE Location_Repository SHALL write only to the Active_Locations_Collection document for the current user, including a `visibleTo` array containing the current user's UID and all target participants (the friend UID for direct shares, or all group member UIDs for group shares)
2. WHEN the Location_Repository updates a location, THE Location_Repository SHALL write only to the Active_Locations_Collection document for the current user
3. WHEN the Location_Repository stops a location sharing session, THE Location_Repository SHALL write only to the Active_Locations_Collection document for the current user
4. THE Location_Repository SHALL NOT write to the Legacy_Subcollection paths during any location operation (start, update, or stop), where Legacy_Subcollection paths are defined as `groups/{groupId}/locations/{uid}` and `directLocationShares/{shareId}/locations/{uid}`
5. THE Location_Repository SHALL include a `visibleTo` array on the Active_Locations_Collection document that contains the UIDs of all users permitted to read the location, preserving the same read access control previously enforced by the legacy subcollection structure
6. IF the single write to the Active_Locations_Collection document fails, THEN THE Location_Repository SHALL return an error result indicating the write failure and SHALL NOT silently discard the location update

### Requirement 2: Denormalize User Profile Data into Active Location Documents

**User Story:** As a developer, I want to embed displayName and photoUrl directly in the activeLocations document, so that the map screen never needs separate user profile reads.

#### Acceptance Criteria

1. WHEN the Location_Repository writes to the Active_Locations_Collection, THE Location_Repository SHALL include the current user's `displayName` (maximum 50 characters, truncated if longer) and `photoUrl` (maximum 2048 characters) fields sourced from the authenticated user's profile in the document
2. WHEN the Global_Map_ViewModel receives location data, THE Global_Map_ViewModel SHALL use the denormalized `displayName` and `photoUrl` from the location document without issuing additional Firestore document reads for user profile data
3. IF the denormalized `displayName` is null or an empty string, THEN THE Global_Map_ViewModel SHALL first attempt to resolve the display name from the in-memory user cache, and if the cache has no entry for that userId, SHALL display the userId as the fallback label
4. IF the denormalized `photoUrl` is null or an empty string, THEN THE Global_Map_ViewModel SHALL first attempt to resolve the photo URL from the in-memory user cache, and if the cache has no entry for that userId, SHALL display a default placeholder avatar
5. WHEN a user updates their profile (`displayName` or `photoUrl`), THE Location_Repository SHALL include the updated values in the next `updateLocation` write to the Active_Locations_Collection

### Requirement 3: Migrate Map_ViewModel to Consolidated Listener

**User Story:** As a developer, I want the per-group MapViewModel to use the consolidated activeLocations listener, so that it no longer creates per-group subcollection listeners that waste reads.

#### Acceptance Criteria

1. THE Map_ViewModel SHALL inject ObserveActiveLocationsUseCase and observe the consolidated Active_Locations_Collection stream instead of injecting ObserveGroupLocationsUseCase and observing the Legacy_Subcollection per-group listener
2. WHEN a group filter is active, THE Map_ViewModel SHALL filter the consolidated location stream client-side by matching SharedLocation.targetId to the current groupId without creating additional Firestore listeners; the filtered result SHALL include only locations where isSharingActive is true and sharingExpiresAt has not elapsed
3. THE Map_ViewModel SHALL read displayName and photoUrl directly from the SharedLocation document fields; IF displayName is empty or null, THEN THE Map_ViewModel SHALL fall back to displaying the userId as the label without invoking GetUsersUseCase or any additional network call
4. WHEN the Map_ViewModel is cleared (onCleared is invoked), THE Map_ViewModel SHALL cancel all coroutine collection jobs scoped to viewModelScope, resulting in zero active Firestore snapshot listeners or Flow collectors attributable to this ViewModel instance
5. IF the consolidated location stream emits an error, THEN THE Map_ViewModel SHALL set the UI state to an error state with a user-visible error indication and SHALL NOT crash or leave a stale location list displayed

### Requirement 4: Implement Socket.IO Location Relay on Server

**User Story:** As a developer, I want the Socket.IO server to relay location_update events between clients, so that real-time location updates bypass Firestore reads entirely.

#### Acceptance Criteria

1. WHEN a connected client emits a `location_update` event with a valid Location_Update_Frame, THE Socket_Relay_Server SHALL broadcast the Location_Update_Frame to all other clients joined to the same location room as the sender
2. WHEN a client connects to the Socket_Relay_Server, THE Socket_Relay_Server SHALL verify the client's Firebase ID token using the existing authentication middleware before allowing the connection
3. IF a client connects without a valid Firebase ID token or with an expired token, THEN THE Socket_Relay_Server SHALL reject the connection with an authentication error and SHALL NOT join the client to any room
4. WHEN a client connects with a `locationRoom` query parameter, THE Socket_Relay_Server SHALL join the client to the specified location room
5. IF a client connects without a `locationRoom` query parameter, THEN THE Socket_Relay_Server SHALL allow the connection but SHALL NOT join the client to any location room and SHALL NOT relay location updates from that client
6. THE Location_Update_Frame SHALL contain the following fields: userId (string), latitude (number, range -90 to 90), longitude (number, range -180 to 180), accuracy (number, in meters), speed (number, in meters per second), bearing (number, range 0 to 360), and timestamp (number, Unix epoch milliseconds)
7. IF a client emits a `location_update` event with a payload missing any required Location_Update_Frame field or containing values outside the specified ranges, THEN THE Socket_Relay_Server SHALL discard the frame and SHALL NOT broadcast it to the room
8. THE Socket_Relay_Server SHALL NOT persist location updates to Firestore (the sending client handles its own Firestore write)
9. WHEN a client disconnects from the Socket_Relay_Server or explicitly leaves a location room, THE Socket_Relay_Server SHALL broadcast a `location_user_offline` event containing the disconnected user's userId to all remaining members of that location room

### Requirement 5: Client Socket.IO Location Emission

**User Story:** As a developer, I want the Location_Tracking_Service to emit location updates via Socket.IO in addition to writing to Firestore, so that connected peers receive updates in real-time without Firestore reads.

#### Acceptance Criteria

1. WHEN the Location_Tracking_Service receives a new GPS fix, THE Location_Repository SHALL emit a `location_update` event via Socket.IO containing the full Location_Update_Frame (userId, latitude, longitude, accuracy, speed, bearing, and timestamp) to the user's joined location room
2. WHILE the user is moving (speed > 1 m/s), THE Location_Repository SHALL emit Socket.IO location updates at the GPS fix interval (every 5 seconds) independent of the Firestore write throttle
3. WHILE the user is stationary (speed ≤ 1 m/s), THE Location_Repository SHALL emit Socket.IO location updates at a maximum interval of 30 seconds
4. WHILE Socket.IO is disconnected, THE Location_Repository SHALL continue writing to Firestore as the sole update mechanism without queuing or buffering Socket.IO emissions
5. WHEN Socket.IO reconnects, THE Location_Repository SHALL resume emitting location updates via Socket.IO on the next GPS fix without replaying missed updates
6. IF a Socket.IO `location_update` emission fails, THEN THE Location_Repository SHALL log the failure and continue writing to Firestore without retrying the Socket.IO emission for that fix

### Requirement 6: Cache-First Location Display

**User Story:** As a user, I want to see friend locations instantly when I open the map, so that I don't have to wait for a network response.

#### Acceptance Criteria

1. WHEN the map screen opens, THE Global_Map_ViewModel SHALL display cached locations from Room_Cache within 100 milliseconds, measured from the point the screen's composable enters the composition to the point location markers are rendered on the map
2. WHEN a location update arrives from Socket.IO or Firestore, THE Location_Repository SHALL persist the update to Room_Cache before emitting the updated location list to the UI
3. THE Room_Cache SHALL store one record per sharing user with the latest known coordinates, displayName, accuracy, speed, bearing, and timestamp, keyed by the sharing user's userId
4. WHEN the app has no network connectivity, THE Global_Map_ViewModel SHALL display the last known locations from Room_Cache and show a persistent visual indicator distinguishing the offline state from the live-data state
5. WHEN a cached location's timestamp is older than 5 minutes relative to the device's current time, THE Global_Map_ViewModel SHALL display a visual indicator on that user's map marker conveying that the position may be outdated
6. IF the Room_Cache contains no location records when the map screen opens, THEN THE Global_Map_ViewModel SHALL display an empty map without location markers and without an error state, until a live update arrives or the user navigates away

### Requirement 7: Socket.IO Primary with Firestore Fallback for Reads

**User Story:** As a developer, I want Socket.IO to be the primary source for incoming location updates with Firestore as a fallback, so that Firestore read costs are minimized during normal operation.

#### Acceptance Criteria

1. WHILE Socket.IO is connected and delivering location updates, THE Location_Repository SHALL NOT maintain an active Firestore snapshot listener for locations
2. WHEN Socket.IO has been disconnected for more than 10 seconds without delivering a location update, THE Location_Repository SHALL activate a Firestore snapshot listener as fallback on the Active_Locations_Collection filtered by the current user's UID in the visibleTo array
3. WHEN Socket.IO reconnects after a fallback period, THE Location_Repository SHALL remove the Firestore fallback listener within 5 seconds of the reconnection event
4. WHEN the Firestore fallback listener is activated, THE Location_Repository SHALL persist all received location documents to Room_Cache and emit them to the UI using the same flow as Socket.IO updates
5. WHEN transitioning from Firestore fallback back to Socket.IO, THE Location_Repository SHALL NOT emit duplicate location entries for the same userId during the transition window

### Requirement 8: Optimize Write Throttle

**User Story:** As a developer, I want to tune the write throttle to balance cost and freshness, so that location updates are frequent enough for good UX but not wasteful.

#### Acceptance Criteria

1. WHILE the user is moving (GPS speed > 1 m/s), THE Location_Repository SHALL throttle Firestore writes to a minimum interval of 10 seconds, discarding intermediate location samples that fall within the throttle window
2. WHILE the user is stationary (GPS speed ≤ 1 m/s), THE Location_Repository SHALL throttle Firestore writes to a minimum interval of 30 seconds, discarding intermediate location samples that fall within the throttle window
3. WHEN the user transitions from stationary to moving (GPS speed crosses above 1 m/s), THE Location_Repository SHALL send an immediate Firestore write with the current location regardless of throttle state and reset the throttle timer
4. WHEN the user transitions from moving to stationary (GPS speed crosses below or equals 1 m/s), THE Location_Repository SHALL send an immediate Firestore write with the current location to persist the final moving position before switching to the stationary throttle interval
5. WHILE the user is moving (GPS speed > 1 m/s), THE Location_Repository SHALL emit Socket.IO location updates at the GPS fix interval of 5 seconds, independent of the Firestore write throttle
6. WHILE the user is stationary (GPS speed ≤ 1 m/s), THE Location_Repository SHALL emit Socket.IO location updates at the GPS fix interval of 30 seconds, independent of the Firestore write throttle
7. IF a throttled Firestore write fails due to a network or server error, THEN THE Location_Repository SHALL retain the location sample and attempt to write it on the next throttle cycle without resetting the throttle timer

### Requirement 9: Eliminate Redundant Sharing Status Checks

**User Story:** As a developer, I want to check sharing status from in-memory state and the single activeLocations document, so that status checks don't trigger multiple Firestore reads.

#### Acceptance Criteria

1. WHEN checking sharing status for a given groupId, THE Location_Repository SHALL first check the in-memory `activeSharingSessions` map for a non-expired entry matching that groupId (0 Firestore reads), and return the cached active status if the entry exists and its expiry time is greater than the current system time
2. IF the in-memory `activeSharingSessions` map does not contain an entry for the requested groupId or the entry's expiry time has passed, THEN THE Location_Repository SHALL read exactly one document from the Active_Locations_Collection for the current user's UID and update the in-memory map with the result before returning
3. THE Location_Repository SHALL NOT read from Legacy_Subcollection paths (group subcollections or direct share subcollections) when checking sharing status
4. WHEN a sharing session starts, THE Location_Repository SHALL insert the groupId and expiry time into the in-memory `activeSharingSessions` map before the start method returns
5. WHEN a sharing session stops, THE Location_Repository SHALL remove the groupId entry from the in-memory `activeSharingSessions` map before the stop method returns
6. IF the single Active_Locations_Collection document read fails due to a network or Firestore error, THEN THE Location_Repository SHALL return false for the sharing status and log the error without throwing an exception to the caller

### Requirement 10: Update Room Entity for Denormalized Fields

**User Story:** As a developer, I want the Room entity to store photoUrl and targetType/targetId, so that cached data is complete for display without additional lookups.

#### Acceptance Criteria

1. THE SharedLocationEntity SHALL include photoUrl (nullable String, max 2048 characters), targetType (non-null String, one of "group" or "direct"), and targetId (non-null String, max 128 characters) fields with default values that preserve backward compatibility (photoUrl defaults to null, targetType defaults to empty string, targetId defaults to empty string)
2. WHEN a location is persisted to Room cache, THE Location_Repository SHALL include all denormalized fields (displayName, photoUrl, targetType, targetId, visibleTo) mapped from the source data, where visibleTo is stored as a JSON-serialized string of user IDs
3. THE LocationDao SHALL provide a query that returns cached locations filtered by targetId, ordered by timestamp descending, emitting results as a Flow for reactive observation in group-specific views
4. WHEN the database schema version is incremented, THE Room_Cache SHALL provide a Migration object that adds the new columns (photoUrl, targetType, targetId, visibleTo) to the shared_location table using ALTER TABLE statements with non-null defaults for targetType and targetId (empty string) and nullable defaults for photoUrl and visibleTo, preserving all existing cached rows without data loss
5. IF the toEntity mapping receives a SharedLocation with a null or empty targetType, THEN THE Location_Repository SHALL default targetType to "group" when groupId does not start with "direct:" and to "direct" when groupId starts with "direct:", ensuring cached entries always contain a valid targetType value

### Requirement 11: Clean Up Firestore Security Rules

**User Story:** As a developer, I want Firestore security rules to reflect the consolidated-only architecture, so that legacy paths can eventually be locked down.

#### Acceptance Criteria

1. THE Firestore security rules SHALL permit read access on Active_Locations_Collection (`activeLocations/{userId}`) only when the request is authenticated AND the requesting user's UID either matches the document's userId (owner) or is present in the document's `visibleTo` array
2. THE Firestore security rules SHALL permit write access on Active_Locations_Collection (`activeLocations/{userId}`) only when the request is authenticated AND the requesting user's UID matches the document ID (userId)
3. IF an unauthenticated request or a request from a user whose UID is neither the document owner nor in the `visibleTo` array targets an Active_Locations_Collection document, THEN THE Firestore security rules SHALL deny the operation
4. THE Firestore security rules SHALL retain the existing Legacy_Subcollection rule blocks (`groups/{groupId}/locations/{uid}` and `directLocationShares/{shareId}/locations/{uid}`) with their current allow/deny behavior unchanged, and each legacy rule block SHALL include a comment containing the text "DEPRECATED: remove after migration" to mark them for future removal
5. THE Firestore security rules SHALL NOT modify rule blocks for `groups`, `conversations`, `directLocationShares` (parent-level), `users`, `friendships`, or `messages` collections beyond the addition of deprecation comments on legacy location subcollections

### Requirement 12: Location Sharing UX Feedback

**User Story:** As a user, I want visual feedback showing that my location is being shared and how much time remains, so that I feel in control of my privacy.

#### Acceptance Criteria

1. WHILE a sharing session with a finite duration is active, THE Global_Map_ViewModel SHALL expose the remaining duration as a countdown string formatted as "Xh Ym" when 60 or more minutes remain or "Xm" when fewer than 60 minutes remain, updated every 60 seconds
2. WHILE a sharing session with no expiration time is active, THE Global_Map_ViewModel SHALL expose a sharing indicator state of active with a null countdown value, indicating continuous sharing with no time limit
3. WHILE a sharing session is active, THE Global_Map_ViewModel SHALL expose a boolean sharing-active indicator state that the UI can observe to render a visual sharing badge
4. WHEN a sharing session expires naturally (current time reaches or exceeds the session expiration timestamp), THE Global_Map_ViewModel SHALL update the sharing state to inactive, clear the associated sharing group identifier and expiration timestamp, and stop the location tracking service within 5 seconds of expiration
5. WHEN the user opens the map during an active sharing session, THE Global_Map_ViewModel SHALL restore the sharing state and countdown from persisted session data (sharing group identifier and expiration timestamp) within 2 seconds of ViewModel initialization
6. IF the user opens the map and the persisted session data indicates a sharing session whose expiration timestamp has already passed, THEN THE Global_Map_ViewModel SHALL update the sharing state to inactive, clear the persisted session data, and stop the location tracking service rather than restoring an expired session
