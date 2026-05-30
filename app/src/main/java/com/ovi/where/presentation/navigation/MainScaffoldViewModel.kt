package com.ovi.where.presentation.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ovi.where.core.constants.AppConstants.STATE_FLOW_SUBSCRIBE_TIMEOUT_MS
import com.ovi.where.data.repository.NotificationRepository
import com.ovi.where.domain.repository.ConversationRepository
import com.ovi.where.domain.usecase.auth.ObserveCurrentUserUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * View model for [WhereBottomBar]. Holds:
 *  • the current user's avatar URL (so the Profile tab icon can render it),
 *  • the unread-notification count (Notifications tab dot),
 *  • whether any unread, non-muted conversation exists (Chats tab dot).
 */
@HiltViewModel
class MainScaffoldViewModel @Inject constructor(
    private val observeCurrentUserUseCase: ObserveCurrentUserUseCase,
    notificationRepository: NotificationRepository,
    conversationRepository: ConversationRepository
) : ViewModel() {

    private val _profilePhotoUrl = MutableStateFlow<String?>(null)
    val profilePhotoUrl: StateFlow<String?> = _profilePhotoUrl.asStateFlow()

    /** Reactive unread-notification count — drives the dot on the Notifications tab. */
    val unreadNotificationCount: StateFlow<Int> = notificationRepository.observeUnreadCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STATE_FLOW_SUBSCRIBE_TIMEOUT_MS), 0)

    /**
     * True when at least one non-muted conversation has unread messages for
     * the current user. Drives the dot on the Chats tab. We surface a boolean
     * (not a count) because the bottom-bar badge is a glanceable dot, matching
     * the Notifications tab.
     */
    val hasUnreadChats: StateFlow<Boolean> =
        combine(
            observeCurrentUserUseCase(),
            conversationRepository.observeConversations()
        ) { user, conversations ->
            val uid = user?.id ?: return@combine false
            conversations.any { convo ->
                uid !in convo.mutedBy && (convo.unreadCounts[uid] ?: 0) > 0
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STATE_FLOW_SUBSCRIBE_TIMEOUT_MS), false)

    init {
        viewModelScope.launch {
            observeCurrentUserUseCase().collect { user ->
                _profilePhotoUrl.value = user?.photoUrl
            }
        }
    }
}
