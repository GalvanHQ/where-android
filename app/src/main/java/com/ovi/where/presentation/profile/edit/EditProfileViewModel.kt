package com.ovi.where.presentation.profile.edit

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ovi.where.core.common.Resource
import com.ovi.where.domain.usecase.auth.CheckUsernameAvailableUseCase
import com.ovi.where.domain.usecase.auth.ObserveCurrentUserUseCase
import com.ovi.where.domain.usecase.auth.UpdateBioUseCase
import com.ovi.where.domain.usecase.auth.UpdateProfileUseCase
import com.ovi.where.domain.usecase.auth.UpdateUsernameUseCase
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
class EditProfileViewModel @Inject constructor(
    private val observeCurrentUserUseCase: ObserveCurrentUserUseCase,
    private val updateProfileUseCase: UpdateProfileUseCase,
    private val updateBioUseCase: UpdateBioUseCase,
    private val updateUsernameUseCase: UpdateUsernameUseCase,
    private val checkUsernameAvailableUseCase: CheckUsernameAvailableUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditProfileUiState())
    val uiState: StateFlow<EditProfileUiState> = _uiState.asStateFlow()

    private var originalUsername = ""
    private var usernameCheckJob: Job? = null

    init {
        viewModelScope.launch {
            observeCurrentUserUseCase().collect { user ->
                if (user != null && !_uiState.value.isInitialized) {
                    originalUsername = user.username
                    _uiState.update {
                        it.copy(
                            displayName = user.displayName,
                            username = user.username,
                            bio = user.bio,
                            photoUrl = user.photoUrl,
                            isInitialized = true
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
        // Only check if changed from original
        if (cleaned != originalUsername && cleaned.length >= 3) {
            usernameCheckJob?.cancel()
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

    fun onBioChanged(bio: String) {
        if (bio.length <= 150) {
            _uiState.update { it.copy(bio = bio) }
        }
    }

    fun onPhotoSelected(uri: Uri?) {
        uri?.let {
            _uiState.update { it.copy(newPhotoUri = uri) }
        }
    }

    fun saveProfile() {
        val state = _uiState.value
        if (state.displayName.isBlank()) {
            _uiState.update { it.copy(error = "Display name cannot be empty") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }

            // Update display name & photo
            val photoUrl = state.newPhotoUri?.toString() ?: state.photoUrl
            val profileResult = updateProfileUseCase(state.displayName, photoUrl)
            if (profileResult is Resource.Error) {
                _uiState.update { it.copy(isSaving = false, error = profileResult.message) }
                return@launch
            }

            // Update bio
            val bioResult = updateBioUseCase(state.bio)
            if (bioResult is Resource.Error) {
                _uiState.update { it.copy(isSaving = false, error = bioResult.message) }
                return@launch
            }

            // Update username if changed
            if (state.username != originalUsername && state.username.length >= 3) {
                val usernameResult = updateUsernameUseCase(state.username)
                if (usernameResult is Resource.Error) {
                    _uiState.update { it.copy(isSaving = false, error = usernameResult.message) }
                    return@launch
                }
            }

            _uiState.update { it.copy(isSaving = false, isSaved = true) }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

data class EditProfileUiState(
    val displayName: String = "",
    val username: String = "",
    val bio: String = "",
    val photoUrl: String? = null,
    val newPhotoUri: Uri? = null,
    val isInitialized: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val isUsernameAvailable: Boolean? = null,
    val usernameError: String? = null,
    val error: String? = null
)
