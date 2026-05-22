package com.ovi.where.presentation.group.join

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.ovi.where.core.common.Resource
import com.ovi.where.core.common.UiEvent
import com.ovi.where.data.repository.SystemMessageWriter
import com.ovi.where.domain.model.SystemEventType
import com.ovi.where.domain.repository.ConversationRepository
import com.ovi.where.domain.usecase.group.JoinGroupUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class JoinGroupViewModel @Inject constructor(
    private val joinGroupUseCase: JoinGroupUseCase,
    private val conversationRepository: ConversationRepository,
    private val systemMessageWriter: SystemMessageWriter,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(JoinGroupUiState())
    val uiState: StateFlow<JoinGroupUiState> = _uiState.asStateFlow()

    private val _uiEvent = Channel<UiEvent>()
    val uiEvent = _uiEvent.receiveAsFlow()

    fun joinGroup(inviteCode: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            when (val result = joinGroupUseCase(inviteCode)) {
                is Resource.Success -> {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    result.data?.let { group ->
                        // Drop a MEMBER_JOINED line into the group's chat so other
                        // members see "Ovi joined the group". This is the
                        // distinguishing path from MEMBER_ADDED — admins adding
                        // someone goes through addMember (handled in GroupInfoVM),
                        // self-joining via invite code arrives here.
                        val convId = conversationRepository.getConversationIdByGroupId(group.id)
                        val actorId = firebaseAuth.currentUser?.uid
                        val actorName = firebaseAuth.currentUser?.displayName ?: "Someone"
                        if (convId != null && actorId != null) {
                            systemMessageWriter.writeSystemMessage(
                                conversationId = convId,
                                eventType = SystemEventType.MEMBER_JOINED,
                                targetUserId = actorId,
                                fallbackText = "$actorName joined the group"
                            )
                        }
                        _uiEvent.send(UiEvent.Navigate("map/${group.id}"))
                    }
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.message
                    )
                }
                is Resource.Loading -> {}
            }
        }
    }
}

data class JoinGroupUiState(
    val isLoading: Boolean = false,
    val error: String? = null
)
