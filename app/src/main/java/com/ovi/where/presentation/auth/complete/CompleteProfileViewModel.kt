package com.ovi.where.presentation.auth.complete

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ovi.where.core.common.Resource
import com.ovi.where.domain.usecase.auth.CheckUsernameAvailableUseCase
import com.ovi.where.domain.usecase.auth.CompleteProfileUseCase
import com.ovi.where.domain.usecase.auth.ObserveCurrentUserUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CompleteProfileViewModel @Inject constructor(
    private val completeProfileUseCase: CompleteProfileUseCase,
    private val checkUsernameAvailableUseCase: CheckUsernameAvailableUseCase,
    private val observeCurrentUserUseCase: ObserveCurrentUserUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(CompleteProfileUiState())
    val uiState: StateFlow<CompleteProfileUiState> = _uiState.asStateFlow()

    private var usernameCheckJob: Job? = null

    init {
        // Pre-fill display name from Google account
        viewModelScope.launch {
            observeCurrentUserUseCase().collect { user ->
                if (user != null && _uiState.value.displayName.isBlank()) {
                    _uiState.update {
                        it.copy(
                            displayName = user.displayName,
                            photoUrl = user.photoUrl
                        )
                    }
                }
            }
        }
    }

    fun onDisplayNameChanged(name: String) {
        _uiState.update { it.copy(displayName = name) }
    }

    fun onUsernameChanged(username: String) {
        val cleaned = username.lowercase().filter { it.isLetterOrDigit() || it == '_' || it == '.' }
        _uiState.update {
            it.copy(
                username = cleaned,
                usernameError = null,
                isUsernameAvailable = null
            )
        }
        // Debounced availability check
        usernameCheckJob?.cancel()
        if (cleaned.length >= 3) {
            usernameCheckJob = viewModelScope.launch {
                delay(500L)
                val available = checkUsernameAvailableUseCase(cleaned)
                _uiState.update {
                    it.copy(
                        isUsernameAvailable = available,
                        usernameError = if (!available) "Username is already taken" else null
                    )
                }
            }
        }
    }

    fun completeProfile() {
        val state = _uiState.value
        if (state.displayName.isBlank()) {
            _uiState.update { it.copy(error = "Display name is required") }
            return
        }
        if (state.username.length < 3) {
            _uiState.update { it.copy(usernameError = "Username must be at least 3 characters") }
            return
        }
        if (state.isUsernameAvailable == false) {
            _uiState.update { it.copy(usernameError = "Username is already taken") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = completeProfileUseCase(state.displayName, state.username)) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isLoading = false, isComplete = true) }
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = result.message) }
                }
                else -> Unit
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

data class CompleteProfileUiState(
    val displayName: String = "",
    val username: String = "",
    val photoUrl: String? = null,
    val isLoading: Boolean = false,
    val isComplete: Boolean = false,
    val isUsernameAvailable: Boolean? = null,
    val usernameError: String? = null,
    val error: String? = null
)
