package com.ovi.where.presentation.notification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = NotificationsUiState(isLoading = true)
        )

    /** Reactive unread count — surfaced by the bell chip on the map. */
    val unreadCount: StateFlow<Int> = repository.observeUnreadCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _pendingNavigation = MutableStateFlow<String?>(null)
    val pendingNavigation: StateFlow<String?> = _pendingNavigation.asStateFlow()

    /**
     * Marks a notification read and emits its deep-link route for the
     * screen to consume. Routes resolve through [com.ovi.where.DeepLinkManager].
     */
    fun onNotificationClick(item: NotificationUiModel) {
        viewModelScope.launch { repository.markAsRead(item.id) }
        _pendingNavigation.value = item.deepLinkRoute
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
    val deepLinkRoute: String?
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
        deepLinkRoute = deepLinkRoute
    )
}
