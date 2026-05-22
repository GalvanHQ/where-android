package com.ovi.where.presentation.map

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager as SystemLocationManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.ovi.where.R
import com.ovi.where.core.common.Resource
import com.ovi.where.core.common.UiEvent
import com.ovi.where.core.common.UiText
import com.ovi.where.core.constants.AppConstants.DISTANCE_KM_THRESHOLD_METERS
import com.ovi.where.core.constants.AppConstants.MILLIS_PER_DAY
import com.ovi.where.core.constants.AppConstants.MILLIS_PER_HOUR
import com.ovi.where.core.constants.AppConstants.MILLIS_PER_MINUTE
import com.ovi.where.core.constants.AppConstants.TIME_AGO_REFRESH_INTERVAL_MS
import com.ovi.where.core.constants.AppConstants.USER_FETCH_DEBOUNCE_MS
import com.ovi.where.data.local.prefs.UserPreferences
import com.ovi.where.data.location.LocationManager
import com.ovi.where.domain.model.SharedLocation
import com.ovi.where.domain.model.MeetupDestination
import com.ovi.where.domain.model.SystemEventType
import com.ovi.where.domain.model.User
import com.ovi.where.domain.repository.ConversationRepository
import com.ovi.where.domain.repository.LocationRepository
import com.ovi.where.domain.usecase.chat.GetOrCreateDirectConversationUseCase
import com.ovi.where.domain.usecase.friend.ObserveFriendsUseCase
import com.ovi.where.domain.usecase.group.GetUserGroupsUseCase
import com.ovi.where.domain.usecase.location.ClearMeetupDestinationUseCase
import com.ovi.where.domain.usecase.location.DetectArrivalUseCase
import com.ovi.where.domain.usecase.location.ObserveActiveLocationsUseCase
import com.ovi.where.domain.usecase.location.ObserveMeetupDestinationUseCase
import com.ovi.where.domain.usecase.location.SetMeetupDestinationUseCase
import com.ovi.where.domain.usecase.location.StartLocationSharingUseCase
import com.ovi.where.domain.usecase.location.StopLocationSharingUseCase
import com.ovi.where.domain.usecase.user.GetUsersUseCase
import com.ovi.where.domain.usecase.auth.ObserveCurrentUserUseCase
import com.ovi.where.service.LocationTrackingService
import dagger.hilt.android.lifecycle.HiltViewModel
import timber.log.Timber
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A single friend's location as shown on the global map.
 */
data class FriendLocationUiModel(
    val userId: String,
    val displayName: String,
    val username: String,
    val photoUrl: String?,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float = 0f,
    val speed: Float = 0f,
    val bearing: Float = 0f,
    val timeAgo: String,
    val isActive: Boolean,
    val groupId: String,
    val groupName: String,
    val distanceMeters: Float? = null,
    /** Formatted ETA string (e.g., "5 min", "1h 12m"). Null if distance < 50m or no user location. */
    val etaText: String? = null,
    /** Raw epoch millis for timeAgo recalculation on ticker */
    val lastUpdatedTimestamp: Long = 0L,
    /**
     * The user's current free-form meetup note ("custom status"), if any.
     * Empty when the user has no active meetup or hasn't set a note.
     * Rendered as a caption bubble above their pin.
     */
    val meetupNote: String = "",
    /**
     * The user's current meetup participation status, if any active meetup
     * has them in its participants map. Drives the pin's status badge
     * (e.g. "Arrived" / "Can't make it"). Null when not part of any active
     * meetup.
     */
    val meetupStatus: com.ovi.where.domain.model.MeetupParticipantStatus? = null
)

/**
 * Maximum number of member avatars previewed per group in stacked-avatar
 * surfaces (the meetup-destination picker, etc.). Three balances visual
 * density with read cost.
 */
private const val MAX_PREVIEW_AVATARS = 3

/**
 * Default duration (in minutes) of the live-location share that auto-starts
 * when a meetup destination is set. This is a fail-safe ceiling — the real
 * lifecycle owner is the arrival auto-stop in [GlobalMapViewModel.checkSelfArrival]
 * and the clear handler. 4 hours covers the long tail of real meetups
 * (across-town drives, restaurant reservations, multi-stop errands)
 * without forcing the user to babysit the share.
 */
private const val MEETUP_AUTO_SHARE_DURATION_MINUTES: Long = 4 * 60

/** Simple group representation for the filter pill / sheet. */
data class GroupFilter(
    val id: String,
    val name: String,
    val isDirect: Boolean = false,
    val photoUrl: String? = null,
    /**
     * Photo URLs of the first few group members (in arbitrary order). Used
     * by stacked-avatar previews — e.g. the meetup-destination group picker.
     * Empty for direct targets and for groups whose user profiles haven't
     * been fetched yet. Items may be null when a member has no photo set.
     */
    val memberPhotos: List<String?> = emptyList()
)

@HiltViewModel
class GlobalMapViewModel @Inject constructor(
    application: Application,
    private val getUserGroupsUseCase: GetUserGroupsUseCase,
    private val observeActiveLocationsUseCase: ObserveActiveLocationsUseCase,
    private val startLocationSharingUseCase: StartLocationSharingUseCase,
    private val stopLocationSharingUseCase: StopLocationSharingUseCase,
    private val getUsersUseCase: GetUsersUseCase,
    private val locationRepository: LocationRepository,
    private val locationManager: LocationManager,
    private val firebaseAuth: FirebaseAuth,
    private val getOrCreateDirectConversationUseCase: GetOrCreateDirectConversationUseCase,
    private val observeFriendsUseCase: ObserveFriendsUseCase,
    private val userPreferences: UserPreferences,
    private val observeCurrentUserUseCase: ObserveCurrentUserUseCase,
    private val setMeetupDestinationUseCase: SetMeetupDestinationUseCase,
    private val clearMeetupDestinationUseCase: ClearMeetupDestinationUseCase,
    private val observeMeetupDestinationUseCase: ObserveMeetupDestinationUseCase,
    private val detectArrivalUseCase: DetectArrivalUseCase,
    private val updateMeetupParticipantStatusUseCase: com.ovi.where.domain.usecase.location.UpdateMeetupParticipantStatusUseCase,
    private val updateMeetupParticipantNoteUseCase: com.ovi.where.domain.usecase.location.UpdateMeetupParticipantNoteUseCase,
    private val conversationRepository: ConversationRepository,
    private val systemMessageWriter: com.ovi.where.data.repository.SystemMessageWriter
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(GlobalMapUiState())
    val uiState: StateFlow<GlobalMapUiState> = _uiState.asStateFlow()

    private val _uiEvent = Channel<UiEvent>()
    val uiEvent = _uiEvent.receiveAsFlow()

    private val _navigateToChat = MutableStateFlow<String?>(null)
    val navigateToChat: StateFlow<String?> = _navigateToChat.asStateFlow()

    // User profile cache (userId → User)
    private val userCache = mutableMapOf<String, User>()

    // Debounced user-fetch tracking
    private val pendingUserFetchIds = mutableSetOf<String>()
    private var userFetchJob: Job? = null

    // Single consolidated location stream job
    private var locationStreamJob: Job? = null
    private var friendsJob: Job? = null

    // Auto-refresh timeAgo ticker
    private var timeAgoRefreshJob: Job? = null

    // Sharing countdown ticker
    private var countdownJob: Job? = null

    // Meetup destination listener (per active group filter)
    private var meetupDestinationJob: Job? = null

    // Placement-mode geocoder job — cancelled on every new camera idle so
    // only the latest pan's address ever lands in state.
    private var placementGeocodeJob: Job? = null

    // Cache raw SharedLocation list for re-processing without re-subscribing
    @Volatile
    private var lastRawLocations: List<SharedLocation> = emptyList()

    val currentUserId: String? get() = firebaseAuth.currentUser?.uid

    private val locationProviderReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == SystemLocationManager.PROVIDERS_CHANGED_ACTION) {
                checkGpsEnabled()
            }
        }
    }

    init {
        restoreLastCameraPosition()
        loadGroupsAndStartObserving()
        observeFriends()
        observeConsolidatedLocations()
        observeLocationOffDialogPref()
        observeCurrentUser()
        checkGpsEnabled()
        registerLocationProviderReceiver()
        startTimeAgoTicker()
        autoLocateOnLaunch()
        restoreSharingState()
        observeRepoSharingState()
    }

    /**
     * Loads the last-known user location from DataStore so the map camera can
     * open near the user immediately — even if location services are currently
     * off. Always flips [GlobalMapUiState.cameraRestored] once the read finishes
     * (regardless of whether a saved coord exists), unblocking GoogleMap composition.
     */
    private fun restoreLastCameraPosition() {
        viewModelScope.launch {
            val saved = userPreferences.getLastKnownLocation()
            _uiState.value = if (saved != null) {
                _uiState.value.copy(
                    initialCameraLat = saved.first,
                    initialCameraLng = saved.second,
                    initialCameraZoom = 14f,
                    cameraRestored = true
                )
            } else {
                // First-ever launch — leave (0,0) zoom 2 default.
                _uiState.value.copy(cameraRestored = true)
            }
        }
    }

    /**
     * Silent auto-locate on ViewModel init (and when the user returns to the
     * Map tab after the previous instance was cleared). Updates the marker
     * position but does **not** request a camera move — otherwise every tab
     * return would visibly zoom from the saved-state camera to the live fix.
     */
    private fun autoLocateOnLaunch() {
        viewModelScope.launch {
            locateMeInternal(moveCamera = false)
        }
    }

    /**
     * Restores sharing state from persisted session data on ViewModel init.
     * If the persisted session has already expired, clears it and stops the service.
     * Otherwise, restores the countdown and sharing indicator.
     *
     * Requirement 12.5: Restore within 2 seconds of ViewModel initialization.
     * Requirement 12.6: If expired, set inactive and clear instead of restoring.
     */
    private fun restoreSharingState() {
        viewModelScope.launch {
            try {
                val savedMarker = userPreferences.sharingTargetId.first()
                val savedExpiry = userPreferences.sharingExpiresAt.first()

                if (savedMarker.isNullOrEmpty()) return@launch

                val now = System.currentTimeMillis()

                // Quick check using the persisted overall expiry
                if (savedExpiry != null && savedExpiry != Long.MAX_VALUE && now >= savedExpiry) {
                    Timber.d("restoreSharingState: persisted session expired, cleaning up")
                    _uiState.value = _uiState.value.copy(
                        isSharing = false,
                        sharingTargetIds = emptyList(),
                        sharingTargetExpiries = emptyMap(),
                        sharingExpiresAt = null,
                        sharingCountdown = null
                    )
                    userPreferences.clearSharingSession()
                    stopLocationService()
                    return@launch
                }

                // Restore active session — fetch authoritative targetIds + expiries from Firestore
                val targetIds = locationRepository.checkSharingStatus()
                if (targetIds.isEmpty()) {
                    userPreferences.clearSharingSession()
                    return@launch
                }
                val expiries = locationRepository.getTargetExpiries()
                val overall = expiries.values.maxOrNull()
                _uiState.value = _uiState.value.copy(
                    isSharing = true,
                    sharingTargetIds = targetIds,
                    sharingTargetExpiries = expiries,
                    sharingExpiresAt = if (overall == Long.MAX_VALUE) null else overall,
                    sharingCountdown = formatCountdown(if (overall == Long.MAX_VALUE) null else overall)
                )
                startCountdownTicker()
            } catch (e: Exception) {
                Timber.e(e, "restoreSharingState failed")
            }
        }
    }

    private fun startCountdownTicker() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (true) {
                delay(15_000L) // poll every 15s for finer per-target expiry granularity
                val state = _uiState.value
                val expiries = state.sharingTargetExpiries
                if (expiries.isEmpty()) break

                val now = System.currentTimeMillis()
                val expired = expiries.filterValues { it != Long.MAX_VALUE && it <= now }.keys
                val active = expiries - expired

                if (expired.isNotEmpty()) {
                    // Stop each expired target server-side. Last one will end the session.
                    expired.forEach { targetId ->
                        stopLocationSharingUseCase(targetId)
                    }
                    val targetNames = expired.joinToString(", ") { id ->
                        (state.groups + state.directTargets).firstOrNull { it.id == id }?.name ?: id
                    }
                    _uiEvent.send(UiEvent.ShowSnackbar(UiText.DynamicString("Stopped sharing with $targetNames")))
                }

                if (active.isEmpty()) {
                    countdownJob?.cancel()
                    _uiState.value = _uiState.value.copy(
                        isSharing = false,
                        sharingTargetIds = emptyList(),
                        sharingTargetExpiries = emptyMap(),
                        sharingExpiresAt = null,
                        sharingCountdown = null
                    )
                    stopLocationService()
                    userPreferences.clearSharingSession()
                    _uiEvent.send(UiEvent.ShowSnackbar(UiText.StringResource(R.string.toast_sharing_stopped)))
                    break
                }

                val overall = active.values.maxOrNull()
                _uiState.value = _uiState.value.copy(
                    sharingTargetIds = active.keys.toList(),
                    sharingTargetExpiries = active,
                    sharingExpiresAt = if (overall == Long.MAX_VALUE) null else overall,
                    sharingCountdown = formatCountdown(if (overall == Long.MAX_VALUE) null else overall)
                )
            }
        }
    }

    /**
     * Mirrors `LocationRepository.activeSharingState` into the UI state so the
     * map's sharing pill, FAB countdown, and "My shares" tab always match the
     * single repo flow. Using `ActiveSharingState`'s helper properties means
     * we never recompute `targetIds` or `overallExpiry` ad-hoc here — the
     * domain model owns that derivation.
     */
    private fun observeRepoSharingState() {
        viewModelScope.launch {
            locationRepository.activeSharingState.collect { state ->
                _uiState.value = _uiState.value.copy(
                    isSharing = state.isSharing,
                    sharingTargetIds = state.targetIds,
                    sharingTargetExpiries = state.targetExpiries,
                    sharingExpiresAt = state.overallExpiry,
                    sharingCountdown = formatCountdown(state.overallExpiry)
                )
            }
        }
    }

    /**
     * Formats the remaining time until expiry as a countdown string.
     * Delegates to [SharingTimeFormatter] so the map FAB pill and the chat
     * header pill always render the same value for the same expiry.
     */
    private fun formatCountdown(expiresAt: Long?): String? {
        return com.ovi.where.presentation.common.SharingTimeFormatter
            .formatRemaining(expiresAt)
    }

    private fun checkGpsEnabled() {
        val lm = getApplication<Application>().getSystemService(Context.LOCATION_SERVICE) as SystemLocationManager
        val enabled = lm.isProviderEnabled(SystemLocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(SystemLocationManager.NETWORK_PROVIDER)
        _uiState.value = _uiState.value.copy(isLocationEnabled = enabled)
    }

    private fun registerLocationProviderReceiver() {
        val app = getApplication<Application>()
        val filter = IntentFilter(SystemLocationManager.PROVIDERS_CHANGED_ACTION)
        app.registerReceiver(locationProviderReceiver, filter)
    }

    private fun unregisterLocationProviderReceiver() {
        try {
            getApplication<Application>().unregisterReceiver(locationProviderReceiver)
        } catch (_: IllegalArgumentException) { /* not registered */ }
    }

    private fun observeCurrentUser() {
        viewModelScope.launch {
            observeCurrentUserUseCase().collect { user ->
                _uiState.value = _uiState.value.copy(myPhotoUrl = user?.photoUrl)
            }
        }
    }

    private fun observeLocationOffDialogPref() {
        viewModelScope.launch {
            userPreferences.isLocationOffDialogShown.collect { shown ->
                if (!shown) {
                    _uiState.value = _uiState.value.copy(showLocationOffDialog = true)
                }
            }
        }
    }

    // ── Consolidated Location Stream (replaces N groupJobs + directLocationJob) ──

    private fun loadGroupsAndStartObserving() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            when (val result = getUserGroupsUseCase()) {
                is Resource.Success -> {
                    val groups = result.data ?: emptyList()
                    _uiState.value = _uiState.value.copy(
                        groups = groups.map {
                            GroupFilter(
                                id = it.id,
                                name = it.name,
                                photoUrl = it.avatarUrl,
                                // Seed empty — photos arrive asynchronously via
                                // resolveGroupMemberPhotos() below.
                                memberPhotos = emptyList()
                            )
                        }
                    )
                    // Hydrate member photos in the background so the meetup
                    // group picker (and any other stacked-avatar surface)
                    // shows real faces.
                    resolveGroupMemberPhotos(groups)
                    // Subscribe to every group's meetup destination so the
                    // pin / chip / place card stay visible regardless of
                    // which filter is active.
                    observeAllMeetupDestinations(groups.map { it.id })
                    // Check sharing sessions in background
                    checkAllSharingSessions()
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.message
                    )
                }
                else -> {}
            }
        }
    }

    /**
     * Fetches profile photos for up to `MAX_PREVIEW_AVATARS` members of each
     * group and updates [GlobalMapUiState.groups] in place. Reuses the
     * shared [userCache] so groups that share members don't trigger
     * duplicate fetches, and uses [getUsersUseCase] for the missing ids.
     */
    private fun resolveGroupMemberPhotos(groups: List<com.ovi.where.domain.model.Group>) {
        if (groups.isEmpty()) return
        viewModelScope.launch {
            val maxPreview = MAX_PREVIEW_AVATARS
            // Pick the first N member ids per group, then dedupe globally so
            // we hit Firestore once per unique uid.
            val previewIds: Map<String, List<String>> = groups.associate { g ->
                g.id to g.memberIds.take(maxPreview)
            }
            val unknownIds = previewIds.values.flatten().distinct()
                .filter { it.isNotBlank() && it !in userCache }
            if (unknownIds.isNotEmpty()) {
                when (val result = getUsersUseCase(unknownIds)) {
                    is Resource.Success ->
                        result.data?.forEach { userCache[it.id] = it }
                    else -> {} // Best-effort — fall back to color dots.
                }
            }
            // Re-map state with photos pulled from the now-warm cache.
            val updated = _uiState.value.groups.map { gf ->
                val ids = previewIds[gf.id].orEmpty()
                if (ids.isEmpty()) gf
                else gf.copy(
                    memberPhotos = ids.map { uid -> userCache[uid]?.photoUrl }
                )
            }
            _uiState.value = _uiState.value.copy(groups = updated)
        }
    }

    private fun observeFriends() {
        friendsJob?.cancel()
        friendsJob = viewModelScope.launch {
            observeFriendsUseCase().collect { friends ->
                // FriendEntry already carries display fields — no need to cache as User.
                val directTargets = friends.map {
                    GroupFilter(
                        id = "direct:${it.friendUid}",
                        name = it.displayName,
                        isDirect = true,
                        photoUrl = it.photoUrl
                    )
                }
                _uiState.value = _uiState.value.copy(directTargets = directTargets)
                checkAllSharingSessions()
            }
        }
    }

    /**
     * Single consolidated Firestore listener for ALL locations visible to current user.
     * Replaces N per-group listeners + direct location listener.
     *
     * Firebase read optimization: This is a SINGLE snapshot listener on the
     * `activeLocations` collection filtered by `visibleTo` array-contains.
     * Firestore counts 1 read per document returned per snapshot emission.
     * Re-processing (filter change, user cache update) reuses [lastRawLocations]
     * without triggering additional Firestore reads.
     */
    private fun observeConsolidatedLocations() {
        locationStreamJob?.cancel()
        locationStreamJob = viewModelScope.launch {
            observeActiveLocationsUseCase()
                .distinctUntilChanged()
                .collect { locations ->
                    lastRawLocations = locations
                    processLocationUpdates(locations)
                }
        }
    }

    private fun processLocationUpdates(locations: List<SharedLocation>) {
        val uid = currentUserId ?: return
        val activeFilter = _uiState.value.activeGroupFilter

        // Client-side filter: match if filter id is in targetIds (or legacy targetId)
        val filtered = if (activeFilter != null) {
            locations.filter { activeFilter.id in it.targetIds || it.targetId == activeFilter.id }
        } else {
            locations
        }

        // Deduplicate by userId — keep latest timestamp per user
        val deduped = filtered
            .filter { it.userId != uid && it.isSharingActive }
            .groupBy { it.userId }
            .mapValues { (_, entries) -> entries.maxByOrNull { it.timestamp }!! }
            .values.toList()

        // Build UI models with distance-from-me
        // Use denormalized displayName/photoUrl from location documents first,
        // then fall back to in-memory user cache, then userId as last resort.
        // This eliminates separate Firestore user profile reads.
        val myLat = _uiState.value.myLatitude
        val myLng = _uiState.value.myLongitude
        val hasMyLoc = _uiState.value.hasMyLocation
        val groups = _uiState.value.groups
        val directTargets = _uiState.value.directTargets

        // Only fetch user profiles for locations with empty denormalized fields
        val unknownIds = deduped
            .filter { it.displayName.isEmpty() && !userCache.containsKey(it.userId) }
            .map { it.userId }
        if (unknownIds.isNotEmpty()) {
            debouncedFetchUsers(unknownIds)
        }

        val activeMeetups = _uiState.value.meetupDestinationsByGroup
        val friendLocations = deduped.map { loc ->
            // Fallback chain: denormalized field → in-memory cache → userId
            val displayName = loc.displayName.ifEmpty {
                userCache[loc.userId]?.displayName ?: loc.userId
            }
            val photoUrl = loc.photoUrl?.takeIf { it.isNotEmpty() }
                ?: userCache[loc.userId]?.photoUrl

            val target = groups.firstOrNull { it.id == loc.targetId }
                ?: directTargets.firstOrNull { it.id == loc.targetId }
            val groupName = target?.name ?: if (loc.targetType == "direct") "Direct" else ""

            val distance = if (hasMyLoc && loc.latitude != 0.0 && loc.longitude != 0.0) {
                val results = FloatArray(1)
                android.location.Location.distanceBetween(myLat, myLng, loc.latitude, loc.longitude, results)
                results[0]
            } else null

            // Stale indicator: mark locations > 5 minutes old
            val isStale = loc.timestamp > 0L &&
                (System.currentTimeMillis() - loc.timestamp) > 5 * 60_000L

            // Find this user's most recent meetup participation (if any).
            // Prefer the meetup whose group matches the location's target,
            // falling back to any meetup the user is in.
            val matchingMeetup = activeMeetups[loc.targetId]
                ?.takeIf { it.participants.containsKey(loc.userId) }
                ?: activeMeetups.values
                    .filter { it.participants.containsKey(loc.userId) }
                    .maxByOrNull { it.setAt }
            val participantEntry = matchingMeetup?.participants?.get(loc.userId)

            FriendLocationUiModel(
                userId = loc.userId,
                displayName = displayName,
                username = userCache[loc.userId]?.username ?: "",
                photoUrl = photoUrl,
                latitude = loc.latitude,
                longitude = loc.longitude,
                accuracy = loc.accuracy,
                speed = loc.speed,
                bearing = loc.bearing,
                timeAgo = formatTimeAgo(loc.timestamp),
                isActive = loc.isSharingActive && !isStale,
                groupId = loc.targetId,
                groupName = groupName,
                distanceMeters = distance,
                etaText = computeEta(distance, loc.speed),
                lastUpdatedTimestamp = loc.timestamp,
                meetupNote = participantEntry?.note.orEmpty(),
                meetupStatus = participantEntry?.status
            )
        }

        _uiState.value = _uiState.value.copy(
            friendLocations = friendLocations,
            isLoading = false
        )
        // Destination metrics depend on the local user's speed (sourced from
        // their own SharedLocation row). Recompute on every batch so the ETA
        // refreshes as the user moves.
        recomputeDestinationMetrics()
        if (_uiState.value.hasMyLocation) {
            checkSelfArrival(_uiState.value.myLatitude, _uiState.value.myLongitude)
        }
    }

    /** Debounced user fetch — collect IDs for 200ms then batch fetch. */
    private fun debouncedFetchUsers(ids: List<String>) {
        synchronized(pendingUserFetchIds) { pendingUserFetchIds.addAll(ids) }
        userFetchJob?.cancel()
        userFetchJob = viewModelScope.launch {
            delay(USER_FETCH_DEBOUNCE_MS)
            val toFetch: List<String>
            synchronized(pendingUserFetchIds) {
                toFetch = pendingUserFetchIds.toList()
                pendingUserFetchIds.clear()
            }
            if (toFetch.isNotEmpty()) fetchUsers(toFetch)
        }
    }

    /**
     * Fetches user profiles and re-processes current locations to update display names.
     * Uses [lastRawLocations] to avoid triggering another Firestore read.
     */
    private suspend fun fetchUsers(userIds: List<String>) {
        when (val result = getUsersUseCase(userIds)) {
            is Resource.Success -> {
                result.data?.forEach { user -> userCache[user.id] = user }
                // Re-process with cached data — no additional Firestore reads
                if (lastRawLocations.isNotEmpty()) {
                    processLocationUpdates(lastRawLocations)
                }
            }
            else -> {}
        }
    }

    /**
     * Auto-refresh timeAgo every 30s.
     * Recalculates formatted time strings from raw timestamps and increments
     * [GlobalMapUiState.timeAgoTick] to break StateFlow structural equality.
     */
    private fun startTimeAgoTicker() {
        timeAgoRefreshJob?.cancel()
        timeAgoRefreshJob = viewModelScope.launch {
            while (true) {
                delay(TIME_AGO_REFRESH_INTERVAL_MS)
                val current = _uiState.value
                if (current.friendLocations.isNotEmpty()) {
                    _uiState.value = current.copy(
                        friendLocations = current.friendLocations.map { friend ->
                            friend.copy(timeAgo = formatTimeAgo(friend.lastUpdatedTimestamp))
                        },
                        timeAgoTick = current.timeAgoTick + 1
                    )
                }
            }
        }
    }

    /**
     * Checks sharing status using a SINGLE Firestore read on the consolidated
     * `activeLocations/{uid}` document. Restores the in-memory session for any
     * still-active session.
     */
    private fun checkAllSharingSessions() {
        viewModelScope.launch {
            try {
                currentUserId ?: return@launch
                val activeTargetIds = locationRepository.checkSharingStatus()
                if (activeTargetIds.isNotEmpty()) {
                    val expiries = locationRepository.getTargetExpiries()
                    val overall = expiries.values.maxOrNull()
                    _uiState.value = _uiState.value.copy(
                        isSharing = true,
                        sharingTargetIds = activeTargetIds,
                        sharingTargetExpiries = expiries,
                        sharingExpiresAt = if (overall == Long.MAX_VALUE) null else overall
                    )
                    startCountdownTicker()
                }
            } catch (e: Exception) {
                Timber.e(e, "checkAllSharingSessions failed")
            }
        }
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    /**
     * Applies a group filter and re-processes cached locations.
     * Does NOT restart the Firestore listener — reuses [lastRawLocations].
     * Firebase read optimization: 0 additional reads on filter change.
     */
    fun setGroupFilter(filter: GroupFilter?) {
        _uiState.value = _uiState.value.copy(activeGroupFilter = filter)
        // Re-process cached data without re-subscribing to Firestore
        if (lastRawLocations.isNotEmpty()) {
            processLocationUpdates(lastRawLocations)
        }
        // Meetup destinations are observed across ALL groups, not per-filter,
        // so they remain visible regardless of the active filter.
    }

    // ── Meetup destination ──────────────────────────────────────────────────

    /**
     * Subscribes to meetup-destination snapshots for **every** group the
     * current user is in. Multi-group state lives in
     * [GlobalMapUiState.meetupDestinationsByGroup]; the single-value
     * [GlobalMapUiState.meetupDestination] is the most-recently-set one
     * (sorted by `setAt`) so the chip / pin / place card always have a
     * canonical "active meetup" to render.
     *
     * Filter changes never affect this — a meetup the user set in any
     * group remains visible whether the filter is on that group, on
     * "All Friends", or on a different group.
     */
    private fun observeAllMeetupDestinations(groupIds: List<String>) {
        meetupDestinationJob?.cancel()
        val targets = groupIds.distinct().filter { it.isNotBlank() }
        if (targets.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                meetupDestinationsByGroup = emptyMap(),
                meetupDestination = null,
                meetupDestinationGroupId = null,
                meetupDestinationDistanceText = null,
                meetupDestinationEtaText = null
            )
            return
        }
        meetupDestinationJob = viewModelScope.launch {
            // Merge per-group listeners with combine() so we always have the
            // freshest snapshot of every group's destination. Each listener
            // emits the group's current MeetupDestination? — null for groups
            // with no active meetup.
            val flows: List<kotlinx.coroutines.flow.Flow<Pair<String, MeetupDestination?>>> =
                targets.map { groupId ->
                    observeMeetupDestinationUseCase(groupId)
                        .map { destination -> groupId to destination }
                }
            combine(flows) { pairs: Array<Pair<String, MeetupDestination?>> ->
                pairs.toMap()
            }.collect { snapshot ->
                applyMeetupSnapshot(snapshot)
            }
        }
    }

    /**
     * Reduces a per-group destination snapshot down to UI state:
     *  • Strips groups with no active destination.
     *  • Picks the most recently set destination as the canonical [meetupDestination].
     *  • Recomputes the arrival-key set so we don't double-announce arrivals
     *    for a destination the user has already arrived at.
     *  • Recomputes distance / ETA against the canonical destination.
     *  • Triggers an arrival check immediately when a fresh destination lands —
     *    the user may already be inside the 100m radius.
     *  • Auto-starts a live share with the destination's group when this
     *    user is a participant in `ON_THE_WAY` status.
     *  • If this user is the destination's creator and every participant has
     *    reached a terminal status (ARRIVED / CANT_MAKE_IT), auto-clears
     *    the destination.
     */
    private fun applyMeetupSnapshot(snapshot: Map<String, MeetupDestination?>) {
        val activeByGroup = snapshot
            .mapNotNull { (groupId, dest) ->
                if (dest != null && dest.isActive && dest.hasValidLocation) {
                    groupId to dest
                } else null
            }
            .toMap()

        // Most-recently-set wins as the canonical "current" destination.
        val canonical = activeByGroup
            .maxByOrNull { it.value.setAt }

        // Drop arrival keys for destinations that no longer exist (the user
        // can be ready to "arrive again" if a new destination is set later
        // for the same group).
        val activeKeys = activeByGroup
            .map { (groupId, dest) -> destinationKey(groupId, dest) }
            .toSet()
        val prunedArrivalKeys = _uiState.value.arrivedDestinationKeys
            .filter { it in activeKeys }
            .toSet()

        _uiState.value = _uiState.value.copy(
            meetupDestinationsByGroup = activeByGroup,
            meetupDestination = canonical?.value,
            meetupDestinationGroupId = canonical?.key,
            arrivedDestinationKeys = prunedArrivalKeys
        )
        recomputeDestinationMetrics()
        if (_uiState.value.hasMyLocation) {
            // Run arrival checks against every active destination so users
            // who set multiple meetups still get correct arrival firing.
            checkSelfArrival(_uiState.value.myLatitude, _uiState.value.myLongitude)
        }

        // Per-member auto-behaviour: every device reacts to its own
        // participant entry. The setter doesn't have a privileged path
        // here — they go through the same logic as everyone else.
        reconcileParticipantBehavior(activeByGroup)

        // Creator-only auto-clear when every participant is in a terminal
        // status. Only the creator's device fires the clear so we don't
        // race writes from N members.
        maybeAutoClearMeetups(activeByGroup)

        // Notes / statuses for friend pins flow through FriendLocationUiModel
        // which is built from the cached locations list. Trigger a rebuild
        // so the new notes propagate without waiting for the next GPS tick.
        if (lastRawLocations.isNotEmpty()) {
            processLocationUpdates(lastRawLocations)
        }
    }

    /**
     * For each active destination, looks at the local user's participant
     * entry and reconciles auto-share state:
     *
     *   ON_THE_WAY   → ensure live share to this group is running.
     *   ARRIVED      → ensure meetup-owned share for this group is stopped.
     *   CANT_MAKE_IT → ensure meetup-owned share for this group is stopped.
     *
     * Status transitions written by other devices (this user's own
     * `markCantMakeIt` from elsewhere, sync after re-login, etc.) flow
     * through here too so the local share state stays consistent with the
     * server-of-truth participant entry.
     */
    private fun reconcileParticipantBehavior(activeByGroup: Map<String, MeetupDestination>) {
        val uid = currentUserId ?: return
        activeByGroup.forEach { (groupId, destination) ->
            val myEntry = destination.participants[uid] ?: return@forEach
            when (myEntry.status) {
                com.ovi.where.domain.model.MeetupParticipantStatus.ON_THE_WAY ->
                    autoStartShareForMeetup(groupId)
                com.ovi.where.domain.model.MeetupParticipantStatus.ARRIVED,
                com.ovi.where.domain.model.MeetupParticipantStatus.CANT_MAKE_IT ->
                    // Self has reached a terminal state on this destination
                    // (possibly written by another device of theirs / arrival
                    // detector). Any share targeting this group exists only
                    // because of the meetup, so drop it unconditionally.
                    stopAnyShareToGroup(groupId)
            }
        }

        // Stop *all* shares for groups whose destinations are no longer
        // active. The destination ending is the lifecycle owner of the
        // meetup-driven share — clear/auto-clear means "the reason for
        // sharing is gone". Strong-stop here so every device kills its
        // share, not just the one whose meetup write started it.
        val noLongerActive = _uiState.value.sharingTargetIds
            .filter { it !in activeByGroup.keys }
            .filter { it in _uiState.value.meetupOwnedShareGroupIds || it in (_uiState.value.previousMeetupGroupIds) }
        noLongerActive.forEach { stopAnyShareToGroup(it) }

        // Track the set of groups that currently have an active meetup
        // for *this user*. We use the difference between snapshots to
        // know which groups just transitioned out of having a meetup —
        // those are the ones we hard-stop the share for.
        val currentMeetupGroupIds = activeByGroup.keys
            .filter { gid -> activeByGroup[gid]?.participants?.containsKey(uid) == true }
            .toSet()
        val justEnded = _uiState.value.previousMeetupGroupIds - currentMeetupGroupIds
        justEnded.forEach { stopAnyShareToGroup(it) }
        if (currentMeetupGroupIds != _uiState.value.previousMeetupGroupIds) {
            _uiState.value = _uiState.value.copy(previousMeetupGroupIds = currentMeetupGroupIds)
        }
    }

    /**
     * Creator-only side effect: when every participant of a destination is
     * in a terminal status, clear the destination. Each device that is the
     * setter sees the same snapshot so only the creator's device fires the
     * clear — preventing N members from racing the same write.
     */
    private fun maybeAutoClearMeetups(activeByGroup: Map<String, MeetupDestination>) {
        val uid = currentUserId ?: return
        activeByGroup.forEach { (groupId, destination) ->
            if (destination.setBy != uid) return@forEach
            val participants = destination.participants
            if (participants.isEmpty()) return@forEach
            val allTerminal = participants.values.all { it.status.isTerminal }
            if (!allTerminal) return@forEach
            // Skip if we've already attempted clear for this destination key
            // — prevents a runaway loop if Firestore is slow to ack.
            val key = destinationKey(groupId, destination)
            if (key in _autoClearedKeys) return@forEach
            _autoClearedKeys += key
            viewModelScope.launch {
                clearMeetupDestinationUseCase(groupId)
            }
        }
    }

    /** Latches that prevent the same destination from being auto-cleared twice. */
    private val _autoClearedKeys = mutableSetOf<String>()

    /** Stable per-session id used to deduplicate `MEETUP_ARRIVED` writes. */
    private fun destinationKey(groupId: String, destination: MeetupDestination): String =
        "${groupId}_${destination.setAt}"

    /**
     * Re-renders the distance + ETA labels on the destination card AND the
     * participants list. Cheap — just reformats existing values, no Firestore
     * reads. Called on every snapshot, location update, and user-cache
     * hydration so the UI stays consistent.
     */
    private fun recomputeDestinationMetrics() {
        val state = _uiState.value
        val destination = state.meetupDestination
        if (destination == null || !destination.hasValidLocation) {
            _uiState.value = state.copy(
                meetupDestinationDistanceText = null,
                meetupDestinationEtaText = null,
                meetupParticipants = emptyList(),
                isMeetupCreator = false,
                selfMeetupStatus = com.ovi.where.domain.model.MeetupParticipantStatus.ON_THE_WAY,
                selfMeetupNote = ""
            )
            return
        }

        // Self-distance / ETA against the destination.
        val (distanceText, etaText) = if (state.hasMyLocation) {
            val results = FloatArray(1)
            android.location.Location.distanceBetween(
                state.myLatitude, state.myLongitude,
                destination.latitude, destination.longitude,
                results
            )
            val distance = results[0]
            val mySpeed = lastRawLocations.firstOrNull { it.userId == currentUserId }?.speed ?: 0f
            formatDistance(distance) to computeEta(distance, mySpeed)
        } else {
            null to null
        }

        val uid = currentUserId
        val groupId = state.meetupDestinationGroupId
        val isCreator = uid != null && destination.setBy == uid
        val selfEntry = uid?.let { destination.participants[it] }
        val selfStatus = selfEntry?.status
            ?: com.ovi.where.domain.model.MeetupParticipantStatus.ON_THE_WAY
        val selfNote = selfEntry?.note.orEmpty()

        // Build participant rows. Sort: arrived → on the way → can't make it,
        // then by name. Self always pinned to the top.
        val now = System.currentTimeMillis()
        val staleThresholdMs = 5 * 60_000L
        val participantRows = destination.participants
            .map { (participantUid, entry) ->
                val user = userCache[participantUid]
                val displayName = user?.displayName?.ifBlank { null } ?: "Member"
                val photoUrl = user?.photoUrl

                // "Inactive" = no recent live-share heartbeat. Only meaningful
                // for ON_THE_WAY users (terminal statuses already have a
                // dedicated badge).
                val locationFrame = lastRawLocations.firstOrNull { it.userId == participantUid }
                val isInactive = entry.status == com.ovi.where.domain.model.MeetupParticipantStatus.ON_THE_WAY
                    && (locationFrame == null
                        || (locationFrame.timestamp > 0 && now - locationFrame.timestamp > staleThresholdMs))

                // Per-participant distance to the destination — only for
                // ON_THE_WAY users with a known location.
                val perUserDistanceLabel = if (locationFrame != null
                    && locationFrame.latitude != 0.0
                    && locationFrame.longitude != 0.0
                    && entry.status == com.ovi.where.domain.model.MeetupParticipantStatus.ON_THE_WAY
                ) {
                    val results = FloatArray(1)
                    android.location.Location.distanceBetween(
                        locationFrame.latitude, locationFrame.longitude,
                        destination.latitude, destination.longitude,
                        results
                    )
                    formatDistance(results[0])
                } else null

                com.ovi.where.presentation.map.components.MeetupParticipantUiModel(
                    userId = participantUid,
                    displayName = displayName,
                    photoUrl = photoUrl,
                    status = entry.status,
                    isYou = participantUid == uid,
                    isCreator = participantUid == destination.setBy,
                    isInactive = isInactive,
                    distanceLabel = perUserDistanceLabel,
                    note = entry.note
                )
            }
            .sortedWith(
                compareByDescending<com.ovi.where.presentation.map.components.MeetupParticipantUiModel> { it.isYou }
                    .thenBy { rowOrderForStatus(it.status) }
                    .thenBy { it.displayName.lowercase() }
            )

        // Hydrate user cache for any participant we don't know about yet —
        // names and photos appear once the fetch completes (re-runs this
        // metric pass, no UI flicker because rows have stable keys).
        val unknownIds = destination.participants.keys
            .filter { it.isNotBlank() && it != uid && it !in userCache }
        if (unknownIds.isNotEmpty()) {
            debouncedFetchUsers(unknownIds)
        }

        _uiState.value = state.copy(
            meetupDestinationDistanceText = distanceText,
            meetupDestinationEtaText = etaText,
            meetupParticipants = participantRows,
            isMeetupCreator = isCreator,
            selfMeetupStatus = selfStatus,
            selfMeetupNote = selfNote
        )
    }

    /**
     * Sort key so the list reads top-to-bottom as: people heading there,
     * people who arrived, people who opted out. Within each bucket the
     * caller does a name sort.
     */
    private fun rowOrderForStatus(status: com.ovi.where.domain.model.MeetupParticipantStatus): Int =
        when (status) {
            com.ovi.where.domain.model.MeetupParticipantStatus.ON_THE_WAY -> 0
            com.ovi.where.domain.model.MeetupParticipantStatus.ARRIVED -> 1
            com.ovi.where.domain.model.MeetupParticipantStatus.CANT_MAKE_IT -> 2
        }

    /**
     * Called by [GlobalMapScreen] when the user long-presses the map.
     * Captures the lat/lng, kicks off a best-effort reverse-geocode, then
     * shows the "Set meetup point" sheet.
     */
    fun onMapLongClick(latitude: Double, longitude: Double) {
        viewModelScope.launch {
            // If there are no groups, we can't pick a destination — bail out.
            if (_uiState.value.groups.isEmpty()) {
                _uiEvent.send(
                    UiEvent.ShowSnackbar(
                        UiText.DynamicString("Join or create a group to set a meetup point")
                    )
                )
                return@launch
            }
            _uiState.value = _uiState.value.copy(
                pendingDestinationPick = PendingDestinationPick(
                    latitude = latitude,
                    longitude = longitude,
                    address = null,
                    isResolvingAddress = true
                ),
                showSetDestinationSheet = true
            )
            val resolved = geocodeAddress(latitude, longitude)
            // Only update if the user hasn't already cancelled / re-picked.
            val currentPick = _uiState.value.pendingDestinationPick
            if (currentPick != null &&
                currentPick.latitude == latitude &&
                currentPick.longitude == longitude
            ) {
                _uiState.value = _uiState.value.copy(
                    pendingDestinationPick = currentPick.copy(
                        address = resolved,
                        isResolvingAddress = false
                    )
                )
            }
        }
    }

    fun dismissSetDestinationSheet() {
        _uiState.value = _uiState.value.copy(
            showSetDestinationSheet = false,
            pendingDestinationPick = null
        )
    }

    /**
     * Enters discoverable placement mode. The map screen renders a centered
     * crosshair + bottom action bar instead of the regular FABs. The user
     * pans/zooms to the spot and confirms via the action bar.
     *
     * No-op (with a snackbar) when the user has no groups, since the
     * destination is group-scoped and there's nothing to pin it to.
     */
    fun enterMeetupPlacement() {
        if (_uiState.value.groups.isEmpty()) {
            viewModelScope.launch {
                _uiEvent.send(
                    UiEvent.ShowSnackbar(
                        UiText.DynamicString("Join or create a group to set a meetup point")
                    )
                )
            }
            return
        }
        _uiState.value = _uiState.value.copy(
            isMeetupPlacementMode = true,
            placementAddress = null,
            isResolvingPlacementAddress = false,
            // Make sure no stale modal is still open.
            showSetDestinationSheet = false,
            pendingDestinationPick = null
        )
    }

    /** Cancels placement mode without setting a destination. */
    fun cancelMeetupPlacement() {
        placementGeocodeJob?.cancel()
        _uiState.value = _uiState.value.copy(
            isMeetupPlacementMode = false,
            placementAddress = null,
            isResolvingPlacementAddress = false
        )
    }

    /**
     * Called by the screen each time the camera idles while in placement
     * mode. Reverse-geocodes the centered coordinates so the action bar can
     * show the resolved address without spamming the geocoder while the
     * user pans.
     */
    fun onPlacementCameraIdle(latitude: Double, longitude: Double) {
        if (!_uiState.value.isMeetupPlacementMode) return
        // Cancel the previous geocode so a slow / timed-out request can't
        // overwrite a fresher result and leave the spinner stuck.
        placementGeocodeJob?.cancel()
        _uiState.value = _uiState.value.copy(isResolvingPlacementAddress = true)
        placementGeocodeJob = viewModelScope.launch {
            val resolved = geocodeAddress(latitude, longitude)
            // Only apply the result if the user is still in placement mode —
            // they may have cancelled while geocoding was in flight.
            if (_uiState.value.isMeetupPlacementMode) {
                _uiState.value = _uiState.value.copy(
                    // Empty result → fall through to a coord label so the
                    // action bar never reads "Finding address" forever.
                    placementAddress = resolved
                        ?: "Lat ${"%.5f".format(latitude)}, Lng ${"%.5f".format(longitude)}",
                    isResolvingPlacementAddress = false
                )
            }
        }
    }

    /**
     * Confirms the placement mode pick. Mirrors the long-press flow — sets
     * the pending pick, then either auto-confirms when there's exactly one
     * eligible group (or the active filter resolves to one) or opens the
     * redesigned sheet for group selection.
     */
    fun confirmMeetupPlacement(latitude: Double, longitude: Double) {
        val state = _uiState.value
        if (!state.isMeetupPlacementMode) return
        val groups = state.groups
        if (groups.isEmpty()) {
            cancelMeetupPlacement()
            return
        }

        val pendingPick = PendingDestinationPick(
            latitude = latitude,
            longitude = longitude,
            address = state.placementAddress,
            isResolvingAddress = state.isResolvingPlacementAddress
        )
        _uiState.value = state.copy(
            isMeetupPlacementMode = false,
            placementAddress = null,
            isResolvingPlacementAddress = false,
            pendingDestinationPick = pendingPick,
            showSetDestinationSheet = true
        )

        // Kick off a fresh geocode if we don't have an address yet — the
        // sheet will pick it up via the existing pendingDestinationPick flow.
        if (pendingPick.address.isNullOrBlank()) {
            viewModelScope.launch {
                val resolved = geocodeAddress(latitude, longitude)
                val current = _uiState.value.pendingDestinationPick
                if (current != null &&
                    current.latitude == latitude &&
                    current.longitude == longitude
                ) {
                    _uiState.value = _uiState.value.copy(
                        pendingDestinationPick = current.copy(
                            address = resolved,
                            isResolvingAddress = false
                        )
                    )
                }
            }
        }
    }

    /**
     * Confirms the picked destination for [groupId]. Authors a
     * `MEETUP_DESTINATION_SET` system message into the group's chat once the
     * Firestore write succeeds.
     */
    fun confirmDestinationPick(groupId: String, name: String) {
        val pick = _uiState.value.pendingDestinationPick ?: return
        val trimmed = name.trim().ifBlank { pick.address?.substringBefore(',')?.trim().orEmpty() }
            .ifBlank { "Meetup point" }
        viewModelScope.launch {
            // Resolve memberIds before the write so the seed participants
            // map is correct. Falls back to the cached group's memberIds if
            // the network fetch fails — better than empty.
            val memberIds = try {
                locationRepository.getGroupMemberIds(groupId)
            } catch (e: Exception) {
                Timber.w(e, "Falling back to local memberIds for $groupId")
                emptyList()
            }
            when (
                setMeetupDestinationUseCase(
                    groupId = groupId,
                    latitude = pick.latitude,
                    longitude = pick.longitude,
                    name = trimmed,
                    address = pick.address.orEmpty(),
                    memberIds = memberIds
                )
            ) {
                is Resource.Success -> {
                    _uiState.value = _uiState.value.copy(
                        showSetDestinationSheet = false,
                        pendingDestinationPick = null
                    )

                    // Frame the camera on the new pin so the user has immediate
                    // visual feedback that the destination took effect. The
                    // multi-group observer (observeAllMeetupDestinations) is
                    // already listening and will populate the destination
                    // state on the next snapshot — no filter switch needed.
                    _uiState.value = _uiState.value.copy(requestDestinationFocus = true)

                    // Auto-start a live-location share with the destination's
                    // group. WHERE's whole purpose is to kill the "where are
                    // you?" loop on meetups — defaulting to opt-in here is the
                    // wrong tradeoff. Sharing is scoped to this group only,
                    // and auto-stops on arrival (see checkSelfArrival) or
                    // when the destination is cleared.
                    autoStartShareForMeetup(groupId)

                    writeMeetupSystemMessage(
                        groupId = groupId,
                        eventType = SystemEventType.MEETUP_DESTINATION_SET,
                        payload = mapOf(
                            "name" to trimmed,
                            "address" to pick.address.orEmpty()
                        ),
                        fallbackText = run {
                            val actor = firebaseAuth.currentUser?.displayName ?: "Someone"
                            "$actor set the meetup point at \"$trimmed\""
                        }
                    )
                    _uiEvent.send(
                        UiEvent.ShowSnackbar(
                            UiText.DynamicString("Meetup point set")
                        )
                    )
                }
                is Resource.Error -> {
                    _uiEvent.send(
                        UiEvent.ShowSnackbar(
                            UiText.DynamicString("Couldn't set meetup point. Try again.")
                        )
                    )
                }
                else -> {}
            }
        }
    }

    /**
     * Clears the canonical active meetup destination. Creator-only — other
     * participants see the Clear control hidden in the UI, and even if the
     * call were forced, the server-side rule should reject. Works
     * regardless of which group filter is currently active because the
     * destination state is no longer filter-bound.
     */
    fun clearMeetupDestination() {
        val state = _uiState.value
        val groupId = state.meetupDestinationGroupId ?: return
        val previous = state.meetupDestination ?: return
        val uid = currentUserId
        if (previous.setBy.isNotBlank() && previous.setBy != uid) {
            viewModelScope.launch {
                _uiEvent.send(
                    UiEvent.ShowSnackbar(
                        UiText.DynamicString("Only the creator can clear this meetup")
                    )
                )
            }
            return
        }
        viewModelScope.launch {
            when (clearMeetupDestinationUseCase(groupId)) {
                is Resource.Success -> {
                    // Meetup is over — kill our share to this group too.
                    // The reconcile pass on every other device will do the
                    // same when their snapshot lands.
                    stopAnyShareToGroup(groupId)

                    writeMeetupSystemMessage(
                        groupId = groupId,
                        eventType = SystemEventType.MEETUP_DESTINATION_CLEARED,
                        payload = mapOf("name" to previous.name),
                        fallbackText = run {
                            val actor = firebaseAuth.currentUser?.displayName ?: "Someone"
                            "$actor cleared the meetup point"
                        }
                    )
                    _uiEvent.send(
                        UiEvent.ShowSnackbar(
                            UiText.DynamicString("Meetup point cleared")
                        )
                    )
                }
                is Resource.Error -> {
                    _uiEvent.send(
                        UiEvent.ShowSnackbar(
                            UiText.DynamicString("Couldn't clear meetup point")
                        )
                    )
                }
                else -> {}
            }
        }
    }

    /**
     * One-shot arrival check across every active meetup destination.
     *
     * For each destination the user is part of: if they've just entered the
     * 100m radius and we haven't authored `MEETUP_ARRIVED` for this
     * destination's `(groupId, setAt)` key yet, write the system message,
     * latch the key, flip the participant status to ARRIVED, and stop the
     * meetup-owned share for that group.
     */
    private fun checkSelfArrival(myLat: Double, myLng: Double) {
        val state = _uiState.value
        val active = state.meetupDestinationsByGroup
        if (active.isEmpty()) return
        val uid = currentUserId

        val newKeys = mutableSetOf<String>()
        active.forEach { (groupId, destination) ->
            if (!destination.hasValidLocation || !destination.isActive) return@forEach
            val key = destinationKey(groupId, destination)
            if (key in state.arrivedDestinationKeys) return@forEach
            // Skip if this user isn't a participant or isn't on the way
            // (already arrived / opted out).
            val myEntry = uid?.let { destination.participants[it] }
            if (myEntry?.status != com.ovi.where.domain.model.MeetupParticipantStatus.ON_THE_WAY) return@forEach
            if (!detectArrivalUseCase.hasArrived(myLat, myLng, destination)) return@forEach

            newKeys += key
            viewModelScope.launch {
                writeMeetupSystemMessage(
                    groupId = groupId,
                    eventType = SystemEventType.MEETUP_ARRIVED,
                    payload = mapOf("name" to destination.name),
                    fallbackText = run {
                        val actor = firebaseAuth.currentUser?.displayName ?: "Someone"
                        "$actor arrived at the meetup point"
                    }
                )
                // Server-side latch — drives every other member's view of
                // this user's arrival, plus the auto-clear check.
                updateMeetupParticipantStatusUseCase(
                    groupId = groupId,
                    status = com.ovi.where.domain.model.MeetupParticipantStatus.ARRIVED
                )
            }
            // You're here — kill the meetup-owned share for this group so we
            // stop pinging your GPS to the group. Manual shares the user
            // started themselves are untouched.
            stopMeetupAutoShareForGroup(groupId)
        }

        if (newKeys.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(
                arrivedDestinationKeys = state.arrivedDestinationKeys + newKeys
            )
        }
    }

    /**
     * Marks the calling user as "can't make it" for the given group's
     * meetup. Stops their meetup-owned share immediately and writes the
     * status to Firestore so other members see the change. Available to
     * any participant — the constraint here is "you can only flip your
     * own entry", not "only the creator can do this".
     */
    fun markCantMakeIt(groupId: String) {
        val state = _uiState.value
        val destination = state.meetupDestinationsByGroup[groupId] ?: return
        if (!destination.isActive) return
        viewModelScope.launch {
            updateMeetupParticipantStatusUseCase(
                groupId = groupId,
                status = com.ovi.where.domain.model.MeetupParticipantStatus.CANT_MAKE_IT
            )
            // Local stop is fast — server snapshot will reconcile in next
            // tick anyway, but this avoids a UX lag where the share pill
            // lingers for the round-trip.
            stopAnyShareToGroup(groupId)
            _uiEvent.send(
                UiEvent.ShowSnackbar(
                    UiText.DynamicString("You let the group know you can't make it")
                )
            )
        }
    }

    /**
     * Updates the calling user's free-form note ("custom status") on the
     * canonical active meetup destination. Empty string clears the note.
     *
     * Targets the canonical (most-recently-set) meetup; if the user is in
     * multiple concurrent meetups across different groups, the canonical
     * one is the one rendered on the place card so updating it there is
     * the natural intent.
     */
    fun setMeetupNote(note: String) {
        val state = _uiState.value
        val groupId = state.meetupDestinationGroupId ?: return
        viewModelScope.launch {
            when (updateMeetupParticipantNoteUseCase(groupId, note)) {
                is Resource.Success -> {
                    if (note.isBlank()) {
                        _uiEvent.send(
                            UiEvent.ShowSnackbar(
                                UiText.DynamicString("Status cleared")
                            )
                        )
                    } else {
                        _uiEvent.send(
                            UiEvent.ShowSnackbar(
                                UiText.DynamicString("Status updated")
                            )
                        )
                    }
                }
                is Resource.Error -> {
                    _uiEvent.send(
                        UiEvent.ShowSnackbar(
                            UiText.DynamicString("Couldn't update status")
                        )
                    )
                }
                else -> {}
            }
        }
    }

    /**
     * Resolves the conversation for [groupId] and writes a system message via
     * [SystemMessageWriter]. Failures are swallowed — the destination state
     * change matters more than the cosmetic timeline entry.
     */
    private suspend fun writeMeetupSystemMessage(
        groupId: String,
        eventType: SystemEventType,
        payload: Map<String, String>,
        fallbackText: String
    ) {
        val conversationId = try {
            conversationRepository.getConversationIdByGroupId(groupId)
        } catch (e: Exception) {
            Timber.w(e, "writeMeetupSystemMessage: failed to resolve conversationId for $groupId")
            return
        } ?: return
        systemMessageWriter.writeSystemMessage(
            conversationId = conversationId,
            eventType = eventType,
            payload = payload,
            fallbackText = fallbackText
        )
    }

    /**
     * Reverse-geocode helper. Best-effort: returns null on failure or empty
     * results. Stays off the main thread; uses [android.location.Geocoder]'s
     * synchronous overload (Android 13+ has an async API but the sync one is
     * fine on `Dispatchers.IO`).
     */
    /**
     * Reverse-geocode helper.
     *
     * On Android 33+ the synchronous [android.location.Geocoder.getFromLocation]
     * is deprecated and returns empty silently on many devices. We use the
     * async overload there via a cancellable coroutine, and fall back to the
     * sync API on older versions. A 4s timeout prevents the resolving spinner
     * from hanging forever when the geocoder backend is slow or offline.
     *
     * Returns the best available address line, or null if the geocoder is
     * absent / returned nothing / timed out.
     */
    private suspend fun geocodeAddress(latitude: Double, longitude: Double): String? {
        val app = getApplication<Application>()
        if (!android.location.Geocoder.isPresent()) {
            Timber.w("geocodeAddress: Geocoder.isPresent() == false on this device")
            return null
        }
        val geocoder = android.location.Geocoder(app, java.util.Locale.getDefault())
        return try {
            kotlinx.coroutines.withTimeoutOrNull(4_000L) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    // Android 13+ — async API. The sync one is deprecated and
                    // returns null on a lot of OEM builds.
                    kotlinx.coroutines.suspendCancellableCoroutine<List<android.location.Address>?> { cont ->
                        geocoder.getFromLocation(latitude, longitude, 1) { addresses ->
                            if (cont.isActive) cont.resumeWith(Result.success(addresses))
                        }
                    }
                } else {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        @Suppress("DEPRECATION")
                        geocoder.getFromLocation(latitude, longitude, 1)
                    }
                }
            }?.firstOrNull()?.let(::formatAddress)
        } catch (e: Exception) {
            Timber.w(e, "geocodeAddress failed")
            null
        }
    }

    /**
     * Picks the best human-readable label out of a [android.location.Address].
     * Prefers `getAddressLine(0)` (full street line); falls back through
     * locality / sub-locality / feature name / admin area so we never end up
     * showing nothing when the geocoder partially succeeded.
     */
    private fun formatAddress(addr: android.location.Address): String? {
        val line = addr.getAddressLine(0)?.takeIf { it.isNotBlank() }
        if (line != null) return line
        val parts = listOfNotNull(
            addr.featureName?.takeIf { it.isNotBlank() },
            addr.subLocality?.takeIf { it.isNotBlank() },
            addr.locality?.takeIf { it.isNotBlank() },
            addr.adminArea?.takeIf { it.isNotBlank() },
            addr.countryName?.takeIf { it.isNotBlank() }
        ).distinct()
        return if (parts.isEmpty()) null else parts.joinToString(", ")
    }

    fun selectFriend(friend: FriendLocationUiModel?) {
        _uiState.value = _uiState.value.copy(selectedFriend = friend, showFriendSheet = friend != null)
    }

    fun dismissFriendSheet() {
        _uiState.value = _uiState.value.copy(showFriendSheet = false, selectedFriend = null)
    }

    fun showGroupPicker(show: Boolean) {
        _uiState.value = _uiState.value.copy(showGroupPicker = show)
    }

    fun showShareSheet(show: Boolean) {
        _uiState.value = _uiState.value.copy(showShareSheet = show)
    }

    fun showLocationOffDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showLocationOffDialog = show)
    }

    /**
     * Starts a multi-target sharing session.
     * @param targetIds list of group ids and "direct:{friendId}" entries.
     */
    fun startSharing(targetIds: List<String>, durationMinutes: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, showShareSheet = false)
            when (startLocationSharingUseCase(targetIds, durationMinutes)) {
                is Resource.Success -> {
                    val expiry = if (durationMinutes > 0) {
                        System.currentTimeMillis() + durationMinutes * MILLIS_PER_MINUTE
                    } else Long.MAX_VALUE
                    val expiries = targetIds.associateWith { expiry }
                    val overall = expiries.values.maxOrNull()
                    _uiState.value = _uiState.value.copy(
                        isSharing = true,
                        sharingTargetIds = targetIds,
                        sharingTargetExpiries = expiries,
                        sharingExpiresAt = if (overall == Long.MAX_VALUE) null else overall,
                        sharingCountdown = formatCountdown(if (overall == Long.MAX_VALUE) null else overall),
                        isLoading = false
                    )
                    startLocationService(durationMinutes)
                    startCountdownTicker()
                    _uiEvent.send(UiEvent.ShowSnackbar(UiText.StringResource(R.string.toast_sharing_started)))
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    _uiEvent.send(UiEvent.ShowSnackbar(UiText.StringResource(R.string.error_failed_start_sharing)))
                }
                else -> {}
            }
        }
    }

    /**
     * Adds a single target with its own duration to the active sharing session.
     * Existing targets keep their original expiries.
     */
    fun addSharingTarget(targetId: String, durationMinutes: Long) {
        viewModelScope.launch {
            // If no active session, fall back to start
            if (!_uiState.value.isSharing) {
                startSharing(listOf(targetId), durationMinutes)
                return@launch
            }
            when (locationRepository.addSharingTarget(targetId, durationMinutes)) {
                is Resource.Success -> {
                    val newExpiry = if (durationMinutes > 0) {
                        System.currentTimeMillis() + durationMinutes * MILLIS_PER_MINUTE
                    } else Long.MAX_VALUE
                    val newExpiries = _uiState.value.sharingTargetExpiries + (targetId to newExpiry)
                    val overall = newExpiries.values.maxOrNull()
                    _uiState.value = _uiState.value.copy(
                        sharingTargetIds = newExpiries.keys.toList(),
                        sharingTargetExpiries = newExpiries,
                        sharingExpiresAt = if (overall == Long.MAX_VALUE) null else overall,
                        sharingCountdown = formatCountdown(if (overall == Long.MAX_VALUE) null else overall)
                    )
                    // Ensure the ticker covers the new expiry
                    startCountdownTicker()
                }
                is Resource.Error -> {
                    _uiEvent.send(UiEvent.ShowSnackbar(UiText.StringResource(R.string.error_failed_start_sharing)))
                }
                else -> {}
            }
        }
    }

    /** Stops the entire sharing session. */
    fun stopSharing() {
        if (!_uiState.value.isSharing) return
        viewModelScope.launch {
            when (stopLocationSharingUseCase()) {
                is Resource.Success -> {
                    countdownJob?.cancel()
                    _uiState.value = _uiState.value.copy(
                        isSharing = false,
                        sharingTargetIds = emptyList(),
                        sharingTargetExpiries = emptyMap(),
                        sharingExpiresAt = null,
                        sharingCountdown = null
                    )
                    stopLocationService()
                    _uiEvent.send(UiEvent.ShowSnackbar(UiText.StringResource(R.string.toast_sharing_stopped)))
                }
                is Resource.Error -> {
                    _uiEvent.send(UiEvent.ShowSnackbar(UiText.StringResource(R.string.error_failed_stop_sharing)))
                }
                else -> {}
            }
        }
    }

    /** Removes one target from the active sharing session. */
    fun removeSharingTarget(targetId: String) {
        viewModelScope.launch {
            when (stopLocationSharingUseCase(targetId)) {
                is Resource.Success -> {
                    val newExpiries = _uiState.value.sharingTargetExpiries - targetId
                    if (newExpiries.isEmpty()) {
                        countdownJob?.cancel()
                        _uiState.value = _uiState.value.copy(
                            isSharing = false,
                            sharingTargetIds = emptyList(),
                            sharingTargetExpiries = emptyMap(),
                            sharingExpiresAt = null,
                            sharingCountdown = null
                        )
                        stopLocationService()
                    } else {
                        val overall = newExpiries.values.maxOrNull()
                        _uiState.value = _uiState.value.copy(
                            sharingTargetIds = newExpiries.keys.toList(),
                            sharingTargetExpiries = newExpiries,
                            sharingExpiresAt = if (overall == Long.MAX_VALUE) null else overall,
                            sharingCountdown = formatCountdown(if (overall == Long.MAX_VALUE) null else overall)
                        )
                    }
                }
                else -> {}
            }
        }
    }

    /** Public entry point — user-initiated (FAB tap, permission grant). Animates the camera. */
    fun locateMe() {
        viewModelScope.launch { locateMeInternal(moveCamera = true) }
    }

    /**
     * Shared implementation. [moveCamera] controls whether we set
     * `requestCameraMove = true` — only the user-initiated path should,
     * so silent re-locates after tab navigation don't visibly snap.
     */
    @Suppress("MissingPermission")
    private suspend fun locateMeInternal(moveCamera: Boolean) {
        locationManager.getCurrentLocation()?.let { loc ->
            _uiState.value = _uiState.value.copy(
                myLatitude = loc.latitude,
                myLongitude = loc.longitude,
                hasMyLocation = true,
                requestCameraMove = moveCamera || _uiState.value.requestCameraMove
            )
            // Refresh distance/ETA + arrival detection now that we have a fix.
            recomputeDestinationMetrics()
            checkSelfArrival(loc.latitude, loc.longitude)
            // Throttle the DataStore write: only persist when the new fix is
            // meaningfully different from the saved one. Stops every tab return
            // (which recreates this ViewModel) from spamming a write with the
            // same coords. Threshold ~50m balances accuracy with write count.
            val saved = userPreferences.getLastKnownLocation()
            val shouldWrite = saved == null || run {
                val results = FloatArray(1)
                android.location.Location.distanceBetween(
                    saved.first, saved.second, loc.latitude, loc.longitude, results
                )
                results[0] >= 50f
            }
            if (shouldWrite) {
                userPreferences.saveLastKnownLocation(loc.latitude, loc.longitude)
            }
        }
    }

    fun onCameraMoveConsumed() {
        _uiState.value = _uiState.value.copy(requestCameraMove = false)
    }

    /** Asks the screen to animate the camera onto the active meetup destination. */
    fun requestDestinationFocus() {
        if (_uiState.value.meetupDestination?.hasValidLocation == true) {
            _uiState.value = _uiState.value.copy(requestDestinationFocus = true)
        }
    }

    fun onDestinationFocusConsumed() {
        _uiState.value = _uiState.value.copy(requestDestinationFocus = false)
    }

    /** Opens the place-card sheet for the active destination. No-op when none. */
    fun openMeetupPlaceCard() {
        if (_uiState.value.meetupDestination?.hasValidLocation == true) {
            _uiState.value = _uiState.value.copy(showMeetupPlaceCard = true)
        }
    }

    fun dismissMeetupPlaceCard() {
        _uiState.value = _uiState.value.copy(showMeetupPlaceCard = false)
    }

    /**
     * Consumes the post-set "share with this group?" prompt without acting
     * on it. Called by the screen when the snackbar dismisses by timeout
     * or swipe.
     */
    fun consumeShareForGroupPrompt() {
        _uiState.value = _uiState.value.copy(promptShareForGroupId = null)
    }

    /**
     * Accepts the post-set prompt and starts a 1-hour live-location share
     * targeted at the prompt's group. If the user is already sharing with
     * other targets, this adds the group as an additional target instead of
     * replacing the session.
     */
    fun acceptShareForGroupPrompt(durationMinutes: Long = 60L) {
        val groupId = _uiState.value.promptShareForGroupId ?: return
        _uiState.value = _uiState.value.copy(promptShareForGroupId = null)
        if (_uiState.value.isSharing) {
            addSharingTarget(groupId, durationMinutes)
        } else {
            startSharing(listOf(groupId), durationMinutes)
        }
    }

    /**
     * Auto-starts a live share targeted at [groupId] after a meetup
     * destination is set. No-op when the user is already sharing with
     * this group. Records the group in [meetupOwnedShareGroupIds] so the
     * arrival / clear flows know exactly which target they own.
     *
     * Default duration is 4 hours — the arrival auto-stop is the real
     * lifecycle owner. Duration is just a fail-safe ceiling; long enough
     * to cover most real meetups (across-town drives, restaurant
     * reservations, errands) without bleeding battery if the user
     * forgets the meetup is still active.
     */
    private fun autoStartShareForMeetup(groupId: String) {
        val state = _uiState.value
        if (groupId in state.sharingTargetIds) {
            // Already sharing with this group — don't reset their existing
            // timer or claim ownership of a share they started manually.
            return
        }
        val durationMinutes = MEETUP_AUTO_SHARE_DURATION_MINUTES
        if (state.isSharing) {
            addSharingTarget(groupId, durationMinutes)
        } else {
            startSharing(listOf(groupId), durationMinutes)
        }
        _uiState.value = _uiState.value.copy(
            meetupOwnedShareGroupIds = _uiState.value.meetupOwnedShareGroupIds + groupId
        )
    }

    /**
     * Stops the meetup-owned share for [groupId], if any. Called when the
     * user arrives at that group's destination or when the destination is
     * cleared. Manual shares the user started themselves are not touched.
     */
    private fun stopMeetupAutoShareForGroup(groupId: String) {
        val state = _uiState.value
        if (groupId !in state.meetupOwnedShareGroupIds) return
        _uiState.value = state.copy(
            meetupOwnedShareGroupIds = state.meetupOwnedShareGroupIds - groupId
        )
        if (groupId in state.sharingTargetIds) {
            removeSharingTarget(groupId)
        }
    }

    /**
     * Hard-stops any active share targeting [groupId] regardless of who
     * started it.
     *
     * Used when the meetup itself ends the share's reason for existing —
     * the user opted out (`CANT_MAKE_IT`), arrived at the destination, or
     * the destination was cleared / auto-cleared. The user's intent to
     * share with this group is over, so we drop the target whether we
     * (the meetup) or the user originally added it.
     *
     * Manual shares to *other* groups are untouched — only the target
     * matching [groupId] is removed.
     */
    private fun stopAnyShareToGroup(groupId: String) {
        val state = _uiState.value
        if (groupId in state.meetupOwnedShareGroupIds) {
            _uiState.value = state.copy(
                meetupOwnedShareGroupIds = state.meetupOwnedShareGroupIds - groupId
            )
        }
        if (groupId in _uiState.value.sharingTargetIds) {
            removeSharingTarget(groupId)
        }
    }

    fun onAutoZoomConsumed() {
        _uiState.value = _uiState.value.copy(hasAutoZoomed = true)
    }

    fun openOrCreateDm(userId: String) {
        viewModelScope.launch {
            when (val result = getOrCreateDirectConversationUseCase(userId)) {
                is Resource.Success -> {
                    result.data?.id?.let { conversationId ->
                        _navigateToChat.value = conversationId
                    }
                }
                else -> { /* Handle error if needed */ }
            }
        }
    }

    fun onChatNavigated() {
        _navigateToChat.value = null
    }

    /** Format distance for display. */
    fun formatDistance(meters: Float?): String {
        if (meters == null) return ""
        return when {
            meters < DISTANCE_KM_THRESHOLD_METERS.toFloat() -> "${meters.toInt()}m away"
            else -> String.format("%.1fkm away", meters / DISTANCE_KM_THRESHOLD_METERS.toFloat())
        }
    }

    /**
     * Computes a human-readable ETA string based on distance and speed.
     *
     * Strategy:
     * - Returns null if distance < 50m (already arrived) or distance is null.
     * - If the user is moving (speed > 1 m/s), use their actual speed —
     *   it's the most accurate signal for "are they driving / walking /
     *   stationary". Speeds are direct GPS readings so they already
     *   incorporate road-following.
     * - Otherwise we have to estimate. Crow-flies haversine
     *   underestimates real road distance by roughly 30% in cities
     *   (Google's own routing data shows 1.25–1.4× depending on grid
     *   density), and average urban driving speed is closer to ~30 km/h
     *   than the textbook 50 km/h. The combined factor — a 1.3 detour
     *   multiplier divided by an 8.3 m/s typical urban speed —
     *   collapses to a single ~10.8 s/m coefficient.
     *
     * The navigation screen has access to the Directions API and uses
     * the API's exact duration when it has a route in hand; this fallback
     * is for the place-card / list rows where we don't fetch a route
     * just to render an estimate.
     */
    private fun computeEta(distanceMeters: Float?, friendSpeed: Float): String? {
        if (distanceMeters == null || distanceMeters < 50f) return null
        val etaSeconds = if (friendSpeed > 1f) {
            // Live speed already reflects road-following, so use the raw
            // straight-line distance against it.
            (distanceMeters / friendSpeed.toDouble()).toLong()
        } else {
            // Stationary/idle — assume road detour + typical urban speed.
            // 1.3 × distance ÷ (30 km/h ≈ 8.33 m/s) = ~0.156 s/m.
            val roadDistance = distanceMeters * ROAD_DETOUR_FACTOR
            (roadDistance / TYPICAL_URBAN_DRIVING_MPS).toLong()
        }
        return when {
            etaSeconds < 60 -> "< 1 min"
            etaSeconds < 3600 -> "${etaSeconds / 60} min"
            else -> "${etaSeconds / 3600}h ${(etaSeconds % 3600) / 60}m"
        }
    }

    private companion object EtaConstants {
        /** Empirical road-vs-crow-flies multiplier for urban areas. */
        const val ROAD_DETOUR_FACTOR = 1.3f
        /** Average city driving speed in m/s (≈ 30 km/h). */
        const val TYPICAL_URBAN_DRIVING_MPS = 8.33
    }

    private fun startLocationService(durationMinutes: Long) {
        val ctx = getApplication<Application>()
        ctx.startForegroundService(LocationTrackingService.createStartIntent(ctx, durationMinutes))
    }

    private fun stopLocationService() {
        val ctx = getApplication<Application>()
        ctx.startService(LocationTrackingService.createStopIntent(ctx))
    }

    private fun formatTimeAgo(timestamp: Long): String {
        if (timestamp == 0L) return ""
        val ctx = getApplication<Application>()
        val diff = System.currentTimeMillis() - timestamp
        return when {
            diff < MILLIS_PER_MINUTE  -> ctx.getString(R.string.time_just_now)
            diff < MILLIS_PER_HOUR    -> ctx.getString(R.string.time_minutes_ago, diff / MILLIS_PER_MINUTE)
            diff < MILLIS_PER_DAY     -> ctx.getString(R.string.time_hours_ago, diff / MILLIS_PER_HOUR)
            else                      -> ctx.getString(R.string.time_days_ago, diff / MILLIS_PER_DAY)
        }
    }

    fun setLocationOffDialogShown() {
        viewModelScope.launch {
            userPreferences.setLocationOffDialogShown(true)
        }
    }

    override fun onCleared() {
        super.onCleared()
        locationStreamJob?.cancel()
        friendsJob?.cancel()
        timeAgoRefreshJob?.cancel()
        countdownJob?.cancel()
        meetupDestinationJob?.cancel()
        placementGeocodeJob?.cancel()
        unregisterLocationProviderReceiver()
    }
}

data class GlobalMapUiState(
    // Map data
    val friendLocations: List<FriendLocationUiModel> = emptyList(),
    val myLatitude: Double = 0.0,
    val myLongitude: Double = 0.0,
    val hasMyLocation: Boolean = false,
    val requestCameraMove: Boolean = false,
    /** Last-known camera position for instant map load (persisted across sessions). */
    val initialCameraLat: Double = 0.0,
    val initialCameraLng: Double = 0.0,
    val initialCameraZoom: Float = 2f,
    // Groups
    val groups: List<GroupFilter> = emptyList(),
    val directTargets: List<GroupFilter> = emptyList(),
    val activeGroupFilter: GroupFilter? = null,
    // Sharing
    val isSharing: Boolean = false,
    /** List of target ids the user is currently sharing with (group ids + "direct:{friendId}" entries). */
    val sharingTargetIds: List<String> = emptyList(),
    /** Per-target expiry map (epoch ms; Long.MAX_VALUE = "until you stop"). */
    val sharingTargetExpiries: Map<String, Long> = emptyMap(),
    /** Latest expiry across all targets (null = no session). */
    val sharingExpiresAt: Long? = null,
    /** Formatted countdown string for the latest-expiring target ("Xh Ym" or "Xm"). */
    val sharingCountdown: String? = null,
    // UI state
    val selectedFriend: FriendLocationUiModel? = null,
    val showFriendSheet: Boolean = false,
    val showGroupPicker: Boolean = false,
    val showShareSheet: Boolean = false,
    val showLocationOffDialog: Boolean = false,
    val myPhotoUrl: String? = null,
    val isLocationEnabled: Boolean = true,
    val isLoading: Boolean = true,
    val error: String? = null,
    /** Whether the initial auto-zoom to fit all friends has already fired. */
    val hasAutoZoomed: Boolean = false,
    /**
     * True once [restoreLastCameraPosition] has finished reading DataStore.
     * The map waits for this before composing GoogleMap so the saved camera
     * position is the very first frame (no jump from (0,0) when location is off).
     */
    val cameraRestored: Boolean = false,
    /** Monotonic counter to break StateFlow structural equality on timeAgo refresh. */
    val timeAgoTick: Long = 0L,
    // ── Meetup destination ──────────────────────────────────────────────────
    /** Active destination for the currently-filtered group, if any. */
    val meetupDestination: MeetupDestination? = null,
    /** Pre-formatted distance from local user to destination ("0.4km away" / "120m away"). */
    val meetupDestinationDistanceText: String? = null,
    /** Pre-formatted ETA from local user to destination ("5 min" / "1h 12m"). */
    val meetupDestinationEtaText: String? = null,
    /** Long-press pick that hasn't been confirmed yet. Drives the bottom sheet. */
    val pendingDestinationPick: PendingDestinationPick? = null,
    /** Whether the "Set meetup point" sheet is visible. */
    val showSetDestinationSheet: Boolean = false,
    /**
     * Discoverable placement mode: when true the map shows a centered
     * crosshair and a bottom action bar instead of the regular FABs. Triggered
     * by the dedicated Meetup FAB; long-press picks a spot directly without
     * entering placement mode (preserves the power-user shortcut).
     */
    val isMeetupPlacementMode: Boolean = false,
    /** Resolved address for the spot the camera is currently centered on while in placement mode. */
    val placementAddress: String? = null,
    /** True while the geocoder is resolving [placementAddress]. */
    val isResolvingPlacementAddress: Boolean = false,
    /**
     * One-shot flag — set by [GlobalMapViewModel.requestDestinationFocus] so the
     * screen animates the camera onto the active destination pin. Cleared via
     * [onDestinationFocusConsumed] once the screen has handled it.
     */
    val requestDestinationFocus: Boolean = false,
    /** Whether the place-card sheet for the active destination is visible. */
    val showMeetupPlaceCard: Boolean = false,
    /**
     * One-shot prompt — set after a successful destination set when the user
     * is not already sharing live location with that group. Drives an
     * actionable snackbar that lets them start sharing in a single tap.
     * Null when no prompt is pending.
     *
     * Deprecated by the auto-share-on-set behavior — kept for compatibility
     * with the existing screen-side LaunchedEffect, but no longer set.
     */
    val promptShareForGroupId: String? = null,
    /**
     * Group id whose live-location share was auto-started by a meetup
     * destination set. We track this so we can auto-stop *only* the meetup
     * share when the user arrives or clears the destination, without
     * touching unrelated shares the user started manually.
     *
     * Deprecated single-value field — kept for source compat but no longer
     * read or written. See [meetupOwnedShareGroupIds] for the multi-group
     * truth.
     */
    val meetupAutoSharedGroupId: String? = null,
    /**
     * Groups whose live shares were auto-started by the meetup flow. Each
     * entry's share auto-stops on arrival at that group's destination or
     * when the destination is cleared. A user can have multiple
     * concurrent meetups across different groups; ownership is per-group.
     */
    val meetupOwnedShareGroupIds: Set<String> = emptySet(),

    /**
     * Snapshot of group ids that had an active meetup *for this user* on
     * the previous reconcile pass. Used to detect "the meetup just ended"
     * (group disappeared from the active map) and unconditionally stop
     * the share to that group on the user's device — even if the share
     * was originally started by the user manually rather than by the
     * meetup itself.
     */
    val previousMeetupGroupIds: Set<String> = emptySet(),
    /**
     * Per-group active meetup destinations. Multi-group state so the chip
     * and pin remain visible regardless of which filter the user has on.
     * The single-value [meetupDestination] below is the most-recently-set
     * active one, derived from this map.
     */
    val meetupDestinationsByGroup: Map<String, MeetupDestination> = emptyMap(),
    /**
     * The group id of [meetupDestination], or null when no meetup is
     * active. Used by the place-card sheet to label the destination
     * ("MEETUP POINT FOR {GroupName}") and by the clear/arrival flows so
     * they target the right group.
     */
    val meetupDestinationGroupId: String? = null,
    /**
     * Per-(group, setAt) keys for which we've already authored
     * `MEETUP_ARRIVED`. Multi-group support means a single string is no
     * longer enough — each meetup needs its own arrival latch.
     */
    val arrivedDestinationKeys: Set<String> = emptySet(),
    /**
     * Resolved participant rows for the canonical [meetupDestination].
     * Built from the destination's participants map + the user cache +
     * the live-share heartbeat. Empty when no destination is active.
     */
    val meetupParticipants: List<com.ovi.where.presentation.map.components.MeetupParticipantUiModel> = emptyList(),
    /** True when the local user is the creator of the canonical meetup. */
    val isMeetupCreator: Boolean = false,
    /** Local user's status on the canonical meetup, defaulting to ON_THE_WAY. */
    val selfMeetupStatus: com.ovi.where.domain.model.MeetupParticipantStatus =
        com.ovi.where.domain.model.MeetupParticipantStatus.ON_THE_WAY,
    /** Local user's free-form note on the canonical meetup, empty when none. */
    val selfMeetupNote: String = "",
    /**
     * Stable key (`"${groupId}_${destination.setAt}"`) of the destination the
     * local user has already authored a `MEETUP_ARRIVED` system message for.
     * Reset whenever the destination changes so a re-set destination triggers
     * a new arrival announcement.
     */
    val arrivedDestinationKey: String? = null
)

/**
 * A long-press pick on the global map that hasn't been confirmed yet.
 * Address may be null while the geocoder is still resolving (or if it failed).
 */
data class PendingDestinationPick(
    val latitude: Double,
    val longitude: Double,
    val address: String? = null,
    val isResolvingAddress: Boolean = false
)
