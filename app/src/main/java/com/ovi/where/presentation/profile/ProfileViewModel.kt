package com.ovi.where.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ovi.where.domain.usecase.auth.ObserveCurrentUserUseCase
import com.ovi.where.domain.usecase.friend.ObserveFriendsUseCase
import com.ovi.where.domain.usecase.group.GetUserGroupsUseCase
import com.ovi.where.presentation.model.UserProfileUiModel
import com.ovi.where.presentation.model.toProfileUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val observeCurrentUserUseCase: ObserveCurrentUserUseCase,
    private val getUserGroupsUseCase: GetUserGroupsUseCase,
    private val observeFriendsUseCase: ObserveFriendsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        observeUser()
        loadStats()
    }

    private fun observeUser() {
        viewModelScope.launch {
            observeCurrentUserUseCase().collect { user ->
                _uiState.update {
                    it.copy(
                        profile = user?.toProfileUiModel(),
                        isLoading = false
                    )
                }
            }
        }
    }

    private fun loadStats() {
        viewModelScope.launch {
            // Groups count
            try {
                val result = getUserGroupsUseCase()
                if (result is com.ovi.where.core.common.Resource.Success) {
                    _uiState.update { it.copy(groupCount = result.data?.size ?: 0) }
                }
            } catch (_: Exception) { /* best effort */ }
        }
        viewModelScope.launch {
            // Friends count
            try {
                observeFriendsUseCase().collect { friends ->
                    _uiState.update { it.copy(friendCount = friends.size) }
                }
            } catch (_: Exception) { /* best effort */ }
        }
    }
}

data class ProfileUiState(
    val profile: UserProfileUiModel? = null,
    val isLoading: Boolean = true,
    val groupCount: Int = 0,
    val friendCount: Int = 0,
    val sharedLocations: Int = 0
)
