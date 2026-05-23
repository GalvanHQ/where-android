package com.ovi.where.presentation.notification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ovi.where.core.constants.AppConstants.STATE_FLOW_SUBSCRIBE_TIMEOUT_MS
import com.ovi.where.core.notification.NotificationType
import com.ovi.where.data.local.entity.NotificationEntity
import com.ovi.where.data.repository.NotificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
 */
@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val repository: NotificationRepository
) : ViewModel() {

    val uiState: StateFlow<NotificationsUiState> = repository.observeAll()
        .map { entries ->
            NotificationsUiState(
                items = entries.map { it.toUiModel() },
                isLoading = false
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_FLOW_SUBSCRIBE_TIMEOUT_MS),
            initialValue = NotificationsUiState(isLoading = true)
        )

    /** Reactive unread count — surfaced by the bell chip on the map. */
    val unreadCount: StateFlow<Int> = repository.observeUnreadCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STATE_FLOW_SUBSCRIBE_TIMEOUT_MS), 0)

    private val _pendingNavigation = MutableStateFlow<String?>(null)
    val pendingNavigation: StateFlow<String?> = _pendingNavigation.asStateFlow()

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
}

data class NotificationsUiState(
    val items: List<NotificationUiModel> = emptyList(),
    val isLoading: Boolean = false
) {
    val isEmpty: Boolean get() = !isLoading && items.isEmpty()
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
)

/** Maps the persisted entity to the UI model, defaulting unknown types to GENERAL. */
internal fun NotificationEntity.toUiModel(): NotificationUiModel {
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
    )
}
