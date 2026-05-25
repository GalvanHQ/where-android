package com.ovi.where.presentation.notification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ovi.where.core.common.Resource
import com.ovi.where.core.constants.AppConstants.STATE_FLOW_SUBSCRIBE_TIMEOUT_MS
import com.ovi.where.core.notification.NotificationType
import com.ovi.where.data.local.dao.UserCacheDao
import com.ovi.where.data.local.entity.NotificationEntity
import com.ovi.where.data.repository.NotificationRepository
import com.ovi.where.domain.repository.FriendshipRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Backs [NotificationsScreen].
 *
 * Scope is intentionally tiny — the inbox now persists only friend
 * events (request received / accepted), the Facebook model. Notifications
 * are NOT clickable rows: the only interactions are inline Accept /
 * Decline on a request and swipe-to-dismiss. No deep-link plumbing, no
 * pendingNavigation event, no markAsRead-on-tap write.
 *
 * Read-state tracking still exists at the data layer so the bell badge
 * can drop to zero when the user opens the screen (we issue a single
 * `markAllAsRead` once on first composition — see screen). One write per
 * inbox visit, regardless of how many rows.
 *
 * Avatars are lazily fetched per unique `userId` and cached in
 * [_avatarCache]. The cache lives for the VM lifecycle — fine because
 * notification volume is low (curated surface) and the same user often
 * appears across multiple rows (request, then accepted later).
 */
@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val repository: NotificationRepository,
    private val friendshipRepository: FriendshipRepository,
    private val userCacheDao: UserCacheDao,
) : ViewModel() {

    // userId -> photoUrl (nullable). null means "tried & none / failed".
    private val _avatarCache = MutableStateFlow<Map<String, String?>>(emptyMap())
    private val resolvingAvatars = mutableSetOf<String>()

    /**
     * Per-row inline action state — keyed by entry id. Drives the
     * Accept/Decline button enabled/spinner state on a request row.
     */
    private val _actionState = MutableStateFlow<Map<String, RequestActionState>>(emptyMap())

    val uiState: StateFlow<NotificationsUiState> = combine(
        repository.observeAll(),
        _avatarCache,
        _actionState,
    ) { entries, avatars, actions ->
        val visible = entries
            .asSequence()
            // Defensive client-side filter for legacy entries — the server
            // already stops persisting non-friend types (see notify.ts),
            // and the repository listener strips them too. This is the
            // last line of defence so a stray row never reaches the UI.
            .filter { entry ->
                val type = runCatching { NotificationType.valueOf(entry.type) }
                    .getOrDefault(NotificationType.GENERAL)
                type.isInboxImportant
            }
            .map { it.toUiModel(avatars[it.userId], actions[it.id]) }
            .toList()

        NotificationsUiState(
            sections = bucketByTime(visible),
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_FLOW_SUBSCRIBE_TIMEOUT_MS),
        initialValue = NotificationsUiState(isLoading = true)
    )

    /** Reactive unread count — surfaced by the bell chip on the map. */
    val unreadCount: StateFlow<Int> = repository.observeUnreadCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STATE_FLOW_SUBSCRIBE_TIMEOUT_MS), 0)

    init {
        // Kick off avatar resolution as new entries arrive.
        viewModelScope.launch {
            repository.observeAll().collect { entries ->
                val needed = entries
                    .mapNotNull { it.userId?.takeIf { uid -> uid.isNotBlank() } }
                    .toSet()
                needed.forEach { resolveAvatar(it) }
            }
        }
    }

    /**
     * Marks every unread row read in a single Firestore write. Called
     * once per screen-open from the screen's [androidx.compose.runtime.LaunchedEffect].
     * One write covers any number of rows (dotted-path map update under
     * the hood). No per-row writes anywhere else.
     */
    fun onScreenOpened() {
        viewModelScope.launch { repository.markAllAsRead() }
    }

    fun onDelete(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }

    fun onClearAll() {
        viewModelScope.launch { repository.clearAll() }
    }

    /**
     * Inline accept on a FRIEND_REQUEST row. Calls the friendship callable,
     * then drops the entry locally so the row vanishes. The accepted-side
     * notification (FRIEND_ACCEPTED) flows through the normal FCM path
     * and lands in the *requester's* inbox a moment later.
     */
    fun onAcceptFriendRequest(item: NotificationUiModel) {
        val requesterId = item.userId ?: return
        if (_actionState.value[item.id] == RequestActionState.InFlight) return
        setAction(item.id, RequestActionState.InFlight)
        viewModelScope.launch {
            val result = friendshipRepository.acceptFriendRequest(requesterId)
            if (result is Resource.Success) {
                repository.delete(item.id)
                clearAction(item.id)
            } else {
                setAction(item.id, RequestActionState.Idle)
            }
        }
    }

    fun onDeclineFriendRequest(item: NotificationUiModel) {
        val requesterId = item.userId ?: return
        if (_actionState.value[item.id] == RequestActionState.InFlight) return
        setAction(item.id, RequestActionState.InFlight)
        viewModelScope.launch {
            val result = friendshipRepository.declineFriendRequest(requesterId)
            if (result is Resource.Success) {
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
     * Lazy avatar fetch — Room-only. We read from [userCacheDao] which is
     * warmed by the rest of the app (friends list listener, conversation
     * listener, profile reads, etc.). If the row is missing we just give
     * up and let the type-icon fallback render — far cheaper than firing
     * a Firestore read per inbox row.
     *
     * Cost: one Room SELECT per unique uid the first time we see it,
     * cached in [_avatarCache] for the VM lifetime. Zero network calls,
     * zero Firestore reads, zero callable invocations.
     */
    private fun resolveAvatar(uid: String) {
        if (_avatarCache.value.containsKey(uid)) return
        if (!resolvingAvatars.add(uid)) return
        viewModelScope.launch {
            val photo = withContext(Dispatchers.IO) {
                runCatching { userCacheDao.get(uid)?.photoUrl }.getOrNull()
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
) {
    val items: List<NotificationUiModel> get() = sections.flatMap { it.items }
    val isEmpty: Boolean get() = !isLoading && sections.isEmpty()
}

data class NotificationUiModel(
    val id: String,
    val type: NotificationType,
    val title: String,
    val body: String,
    val timestamp: Long,
    val isRead: Boolean,
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
        userId = userId,
        avatarUrl = avatarUrl,
        actionState = actionState ?: RequestActionState.Idle,
    )
}
