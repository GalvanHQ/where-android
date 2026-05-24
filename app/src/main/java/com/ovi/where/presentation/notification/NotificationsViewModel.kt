package com.ovi.where.presentation.notification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ovi.where.core.constants.AppConstants.STATE_FLOW_SUBSCRIBE_TIMEOUT_MS
import com.ovi.where.core.notification.NotificationType
import com.ovi.where.data.local.entity.NotificationEntity
import com.ovi.where.data.repository.NotificationRepository
import com.ovi.where.domain.repository.FriendshipRepository
import com.ovi.where.domain.repository.UserRepository
import com.ovi.where.core.common.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Backs [NotificationsScreen]. Reads from [NotificationRepository] for the
 * inbox stream, exposes a one-shot navigation event when the user taps a
 * notification, and provides actions to mark/clear/delete entries.
 *
 * Tap behaviour: a tap marks the row read AND emits the deep-link route
 * already resolved at receive time. The screen forwards that route to
 * [com.ovi.where.DeepLinkManager], which hands it to [AppNavGraph] via
 * the same path FCM uses when the app is killed — so taps from the inbox
 * and taps from the system tray converge on the same code path.
 *
 * Filter chips: the screen lets users narrow to Requests / Meetups.
 * The chip state is held here (not in screen-local state) so it survives
 * config changes and so we can drive section bucketing off the same
 * filtered list.
 *
 * Avatar resolution: friend-related rows show the actor's profile photo
 * when we can resolve it. We lazily fetch each unique `userId` once and
 * cache the result in [_avatarCache]. The cache lives for the VM
 * lifecycle — no back-pressure issue since notification volume is low
 * (the inbox is the curated "important only" surface).
 */
@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val repository: NotificationRepository,
    private val friendshipRepository: FriendshipRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val filter = MutableStateFlow(NotificationFilter.ALL)

    // userId -> photoUrl (nullable). null means "tried & none / failed".
    // We mark "in flight" by absence from the map.
    private val _avatarCache = MutableStateFlow<Map<String, String?>>(emptyMap())
    private val resolvingAvatars = mutableSetOf<String>()

    /**
     * Per-row inline action state — keyed by entry id. Used to disable the
     * Accept/Decline buttons on a friend-request row while the callable is
     * in flight, and to drop the row optimistically once accepted/declined.
     */
    private val _actionState = MutableStateFlow<Map<String, RequestActionState>>(emptyMap())

    val uiState: StateFlow<NotificationsUiState> = combine(
        repository.observeAll(),
        filter,
        _avatarCache,
        _actionState,
    ) { entries, currentFilter, avatars, actions ->
        val visible = entries
            .asSequence()
            .filter { entry ->
                val type = runCatching { NotificationType.valueOf(entry.type) }
                    .getOrDefault(NotificationType.GENERAL)
                currentFilter.matches(type)
            }
            .map { it.toUiModel(avatars[it.userId], actions[it.id]) }
            .toList()

        NotificationsUiState(
            sections = bucketByTime(visible),
            isLoading = false,
            filter = currentFilter,
            totalCount = entries.size,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_FLOW_SUBSCRIBE_TIMEOUT_MS),
        initialValue = NotificationsUiState(isLoading = true)
    )

    /** Reactive unread count — surfaced by the bell chip on the map. */
    val unreadCount: StateFlow<Int> = repository.observeUnreadCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STATE_FLOW_SUBSCRIBE_TIMEOUT_MS), 0)

    private val _pendingNavigation = MutableStateFlow<String?>(null)
    val pendingNavigation: StateFlow<String?> = _pendingNavigation.asStateFlow()

    init {
        // Kick off avatar resolution as new entries arrive. We don't wait
        // on this Flow — the StateFlow above already includes the cache.
        viewModelScope.launch {
            repository.observeAll().collect { entries ->
                val needed = entries
                    .mapNotNull { it.userId?.takeIf { uid -> uid.isNotBlank() } }
                    .toSet()
                needed.forEach { resolveAvatar(it) }
            }
        }
    }

    fun onFilterSelected(next: NotificationFilter) {
        filter.value = next
    }

    /**
     * Marks a notification read and emits a deep-link route for the screen
     * to consume. If the persisted entry has no route (legacy data, or the
     * producer didn't have enough context to compute one), we re-resolve it
     * from the type + ids the entry carries — this keeps the in-app inbox
     * tap-target source-of-truth in one place. Worst case (truly nothing to
     * navigate to) we fall back to opening the inbox itself, which is still
     * better than the previous silent no-op behavior.
     */
    fun onNotificationClick(item: NotificationUiModel) {
        viewModelScope.launch { repository.markAsRead(item.id) }
        val route = item.deepLinkRoute?.takeIf { it.isNotBlank() }
            ?: resolveFallbackRoute(item)
        _pendingNavigation.value = route
    }

    /**
     * Recomputes a deep-link route from the entry's type + denormalized ids
     * when the persisted `deepLinkRoute` is missing. Mirrors the resolver
     * in [com.ovi.where.core.notification.NotificationHelper.resolveDeepLinkRoute]
     * — same canonical mapping, just keyed off the [NotificationUiModel].
     */
    private fun resolveFallbackRoute(item: NotificationUiModel): String =
        when (item.type) {
            NotificationType.NEW_MESSAGE,
            NotificationType.MENTION ->
                item.conversationId?.let { "chat/$it" } ?: "notifications"

            NotificationType.FRIEND_REQUEST -> "friend_requests"
            NotificationType.FRIEND_ACCEPTED ->
                item.userId?.let { "user_profile/$it" } ?: "notifications"

            NotificationType.MEMBER_JOINED,
            NotificationType.MEMBER_LEFT ->
                item.groupId?.let { "group_info/$it" } ?: "notifications"

            NotificationType.LOCATION_UPDATE,
            NotificationType.LIVE_LOCATION_STARTED,
            NotificationType.LIVE_LOCATION_STOPPED ->
                item.groupId?.let { "group_map/$it" }
                    ?: item.conversationId?.let { "chat/$it" }
                    ?: "tab_map"

            NotificationType.MEETUP_DESTINATION_SET,
            NotificationType.MEETUP_DESTINATION_CLEARED,
            NotificationType.MEETUP_MEMBER_ARRIVED ->
                item.groupId?.let { "group_map/$it" } ?: "notifications"

            NotificationType.GENERAL -> "notifications"
        }

    fun onNavigationConsumed() {
        _pendingNavigation.value = null
    }

    fun onMarkAllRead() {
        viewModelScope.launch { repository.markAllAsRead() }
    }

    fun onDelete(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }

    fun onClearAll() {
        viewModelScope.launch { repository.clearAll() }
    }

    /**
     * Inline accept on a FRIEND_REQUEST row. Calls the callable, marks
     * the entry read, and removes it from the inbox doc so the row
     * disappears. If the call fails the row stays so the user can retry.
     */
    fun onAcceptFriendRequest(item: NotificationUiModel) {
        val requesterId = item.userId ?: return
        if (_actionState.value[item.id] == RequestActionState.InFlight) return
        setAction(item.id, RequestActionState.InFlight)
        viewModelScope.launch {
            val result = friendshipRepository.acceptFriendRequest(requesterId)
            if (result is Resource.Success) {
                repository.markAsRead(item.id)
                repository.delete(item.id)
                clearAction(item.id)
            } else {
                setAction(item.id, RequestActionState.Idle)
            }
        }
    }

    /**
     * Inline decline. Same flow as accept — once the callable resolves
     * we drop the entry from the inbox so the row disappears.
     */
    fun onDeclineFriendRequest(item: NotificationUiModel) {
        val requesterId = item.userId ?: return
        if (_actionState.value[item.id] == RequestActionState.InFlight) return
        setAction(item.id, RequestActionState.InFlight)
        viewModelScope.launch {
            val result = friendshipRepository.declineFriendRequest(requesterId)
            if (result is Resource.Success) {
                repository.markAsRead(item.id)
                repository.delete(item.id)
                clearAction(item.id)
            } else {
                setAction(item.id, RequestActionState.Idle)
            }
        }
    }

    private fun setAction(id: String, state: RequestActionState) {
        _actionState.update { it + (id to state) }
    }

    private fun clearAction(id: String) {
        _actionState.update { it - id }
    }

    /**
     * Lazy avatar fetch. Only one in-flight request per uid; on success we
     * publish the photoUrl (may be null) so [uiState] re-emits with it.
     */
    private fun resolveAvatar(uid: String) {
        if (_avatarCache.value.containsKey(uid)) return
        if (!resolvingAvatars.add(uid)) return
        viewModelScope.launch {
            val photo = withContext(Dispatchers.IO) {
                runCatching {
                    when (val r = userRepository.getUser(uid)) {
                        is Resource.Success -> r.data?.photoUrl
                        else -> null
                    }
                }.getOrNull()
            }
            _avatarCache.update { it + (uid to photo) }
        }
    }

    /**
     * Time bucketing: today / this week / earlier. Buckets are cheap to
     * compute (we just need ms thresholds), and rebuilding on every emit
     * is fine because the inbox is small (≤ 200 entries by FIFO cap).
     */
    private fun bucketByTime(items: List<NotificationUiModel>): List<NotificationSection> {
        if (items.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        val today = now - DAY_MS
        val thisWeek = now - 7 * DAY_MS

        val sortedNewest = items.sortedByDescending { it.timestamp }
        val todayItems = mutableListOf<NotificationUiModel>()
        val weekItems = mutableListOf<NotificationUiModel>()
        val earlierItems = mutableListOf<NotificationUiModel>()
        for (item in sortedNewest) {
            when {
                item.timestamp >= today -> todayItems += item
                item.timestamp >= thisWeek -> weekItems += item
                else -> earlierItems += item
            }
        }
        val sections = mutableListOf<NotificationSection>()
        if (todayItems.isNotEmpty()) sections += NotificationSection(SectionId.TODAY, todayItems)
        if (weekItems.isNotEmpty()) sections += NotificationSection(SectionId.THIS_WEEK, weekItems)
        if (earlierItems.isNotEmpty()) sections += NotificationSection(SectionId.EARLIER, earlierItems)
        return sections
    }

    companion object {
        private const val DAY_MS = 24L * 60 * 60 * 1000
    }
}

/** Filter chips displayed at the top of the inbox screen. */
enum class NotificationFilter {
    ALL,
    REQUESTS,
    MEETUPS;

    fun matches(type: NotificationType): Boolean = when (this) {
        ALL -> true
        REQUESTS -> type == NotificationType.FRIEND_REQUEST || type == NotificationType.FRIEND_ACCEPTED
        MEETUPS -> type == NotificationType.MEETUP_DESTINATION_SET ||
            type == NotificationType.MEETUP_DESTINATION_CLEARED ||
            type == NotificationType.MEETUP_MEMBER_ARRIVED
    }
}

/** Time-bucket id used by section headers. */
enum class SectionId { TODAY, THIS_WEEK, EARLIER }

data class NotificationSection(
    val id: SectionId,
    val items: List<NotificationUiModel>,
)

/** Inline-action state for FRIEND_REQUEST rows. */
enum class RequestActionState { Idle, InFlight }

data class NotificationsUiState(
    val sections: List<NotificationSection> = emptyList(),
    val isLoading: Boolean = false,
    val filter: NotificationFilter = NotificationFilter.ALL,
    val totalCount: Int = 0,
) {
    val items: List<NotificationUiModel> get() = sections.flatMap { it.items }
    val isEmpty: Boolean get() = !isLoading && sections.isEmpty()
    /** True when the inbox itself has rows but the filter hides them all. */
    val isFilteredEmpty: Boolean get() = !isLoading && sections.isEmpty() && totalCount > 0
    val unreadCount: Int get() = items.count { !it.isRead }
}

data class NotificationUiModel(
    val id: String,
    val type: NotificationType,
    val title: String,
    val body: String,
    val timestamp: Long,
    val isRead: Boolean,
    val deepLinkRoute: String?,
    /** Denormalized ids from the inbox doc — used as a fallback when
     *  [deepLinkRoute] is missing so a tap still navigates somewhere. */
    val conversationId: String? = null,
    val groupId: String? = null,
    val userId: String? = null,
    /** Avatar url for [userId] (when resolvable). null = no avatar / unresolved. */
    val avatarUrl: String? = null,
    /** In-flight state for inline Accept/Decline on FRIEND_REQUEST. */
    val actionState: RequestActionState = RequestActionState.Idle,
)

/** Maps the persisted entity to the UI model, defaulting unknown types to GENERAL. */
internal fun NotificationEntity.toUiModel(
    avatarUrl: String? = null,
    actionState: RequestActionState? = null,
): NotificationUiModel {
    val type = runCatching { NotificationType.valueOf(this.type) }
        .getOrDefault(NotificationType.GENERAL)
    return NotificationUiModel(
        id = id,
        type = type,
        title = title,
        body = body,
        timestamp = timestamp,
        isRead = isRead,
        deepLinkRoute = deepLinkRoute,
        conversationId = conversationId,
        groupId = groupId,
        userId = userId,
        avatarUrl = avatarUrl,
        actionState = actionState ?: RequestActionState.Idle,
    )
}
