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
import android.content.Context
import android.net.Uri
import com.ovi.where.core.utils.ImageUploadUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CompleteProfileViewModel @Inject constructor(
    private val completeProfileUseCase: CompleteProfileUseCase,
    private val checkUsernameAvailableUseCase: CheckUsernameAvailableUseCase,
    private val observeCurrentUserUseCase: ObserveCurrentUserUseCase,
    @ApplicationContext private val context: Context
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

    fun onPhotoSelected(uri: Uri?) {
        uri?.let {
            _uiState.update { it.copy(newPhotoUri = uri) }
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
                usernameError = if (cleaned.isNotEmpty() && cleaned.length < 3) "Username must be at least 3 characters" else null,
                isUsernameAvailable = null,
                isCheckingUsername = cleaned.length >= 3
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
                        isCheckingUsername = false,
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
        if (state.isUsernameAvailable == null && !state.isCheckingUsername) {
            _uiState.update { it.copy(usernameError = "Please wait for username validation") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            var finalPhotoUrl: String? = state.photoUrl
            
            // Upload photo if newly selected
            if (state.newPhotoUri != null && ImageUploadUtil.isLocalUri(state.newPhotoUri.toString())) {
                val uploadResult = ImageUploadUtil.uploadProfilePicture(context, state.newPhotoUri)
                if (uploadResult.isSuccess) {
                    finalPhotoUrl = uploadResult.getOrNull()
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "Failed to upload photo") }
                    return@launch
                }
            }
            
            when (val result = completeProfileUseCase(state.displayName, state.username, finalPhotoUrl)) {
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
    val newPhotoUri: Uri? = null,
    val isLoading: Boolean = false,
    val isComplete: Boolean = false,
    val isCheckingUsername: Boolean = false,
    val isUsernameAvailable: Boolean? = null,
    val usernameError: String? = null,
    val error: String? = null
)
