package com.ovi.where.presentation.profile

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.storage.FirebaseStorage
import com.ovi.where.core.common.Resource
import com.ovi.where.core.common.UiEvent
import com.ovi.where.core.common.UiText
import com.ovi.where.domain.usecase.auth.ObserveCurrentUserUseCase
import com.ovi.where.domain.usecase.auth.SignOutUseCase
import com.ovi.where.domain.usecase.auth.UpdateProfileUseCase
import com.ovi.where.presentation.model.UserProfileUiModel
import com.ovi.where.presentation.model.toProfileUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import com.ovi.where.R

@HiltViewModel
class ProfileViewModel @Inject constructor(
    application: Application,
    private val signOutUseCase: SignOutUseCase,
    private val observeCurrentUserUseCase: ObserveCurrentUserUseCase,
    private val updateProfileUseCase: UpdateProfileUseCase,
    private val firebaseStorage: FirebaseStorage
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val _uiEvent = Channel<UiEvent>()
    val uiEvent = _uiEvent.receiveAsFlow()

    init { observeUser() }

    private fun observeUser() {
        viewModelScope.launch {
            observeCurrentUserUseCase().collect { user ->
                _uiState.value = _uiState.value.copy(
                    profile = user?.toProfileUiModel(),
                    displayNameInput = user?.displayName ?: ""
                )
            }
        }
    }

    fun onDisplayNameChange(name: String) {
        _uiState.value = _uiState.value.copy(displayNameInput = name, displayNameError = null)
    }

    fun onSaveProfile() {
        val name = _uiState.value.displayNameInput.trim()
        if (name.isBlank()) {
            _uiState.value = _uiState.value.copy(displayNameError = "Name is required")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            when (updateProfileUseCase(name, _uiState.value.profile?.photoUrl)) {
                is Resource.Success -> {
                    _uiState.value = _uiState.value.copy(isSaving = false, isEditingName = false)
                    _uiEvent.send(UiEvent.ShowSnackbar(
                        UiText.StringResource(R.string.msg_profile_updated)
                    ))
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(isSaving = false)
                    _uiEvent.send(UiEvent.ShowSnackbar(
                        UiText.StringResource(R.string.error_profile_update_failed)
                    ))
                }
                else -> {}
            }
        }
    }

    fun onPhotoSelected(uri: Uri) {
        viewModelScope.launch {
            val userId = _uiState.value.profile?.let {
                // Use Firebase Auth UID from observed user
                it.displayName  // placeholder - we need actual UID
            } ?: return@launch

            _uiState.value = _uiState.value.copy(isUploadingPhoto = true)
            try {
                val storageRef = firebaseStorage.reference
                    .child("profile_photos/${System.currentTimeMillis()}.jpg")
                storageRef.putFile(uri).await()
                val downloadUrl = storageRef.downloadUrl.await().toString()

                when (updateProfileUseCase(
                    _uiState.value.displayNameInput,
                    downloadUrl
                )) {
                    is Resource.Success -> {
                        _uiState.value = _uiState.value.copy(isUploadingPhoto = false)
                        _uiEvent.send(UiEvent.ShowSnackbar(
                            UiText.StringResource(R.string.msg_profile_updated)
                        ))
                    }
                    else -> {
                        _uiState.value = _uiState.value.copy(isUploadingPhoto = false)
                        _uiEvent.send(UiEvent.ShowSnackbar(
                            UiText.StringResource(R.string.error_photo_upload_failed)
                        ))
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isUploadingPhoto = false)
                _uiEvent.send(UiEvent.ShowSnackbar(
                    UiText.StringResource(R.string.error_photo_upload_failed)
                ))
            }
        }
    }

    fun setEditingName(editing: Boolean) {
        _uiState.value = _uiState.value.copy(isEditingName = editing)
        if (!editing) {
            _uiState.value = _uiState.value.copy(
                displayNameInput = _uiState.value.profile?.displayName ?: ""
            )
        }
    }

    fun onLogout() {
        viewModelScope.launch {
            signOutUseCase()
            _uiEvent.send(UiEvent.Navigate("login"))
        }
    }
}

data class ProfileUiState(
    val profile: UserProfileUiModel? = null,
    val displayNameInput: String = "",
    val displayNameError: String? = null,
    val isEditingName: Boolean = false,
    val isSaving: Boolean = false,
    val isUploadingPhoto: Boolean = false
)
