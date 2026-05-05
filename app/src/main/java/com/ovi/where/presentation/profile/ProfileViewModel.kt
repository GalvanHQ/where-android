package com.ovi.where.presentation.profile

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.ovi.where.R
import com.ovi.where.core.common.Resource
import com.ovi.where.core.common.UiEvent
import com.ovi.where.core.common.UiText
import com.ovi.where.domain.usecase.auth.CheckUsernameAvailableUseCase
import com.ovi.where.domain.usecase.auth.ObserveCurrentUserUseCase
import com.ovi.where.domain.usecase.auth.SignOutUseCase
import com.ovi.where.domain.usecase.auth.UpdateBioUseCase
import com.ovi.where.domain.usecase.auth.UpdateProfileUseCase
import com.ovi.where.domain.usecase.auth.UpdateUsernameUseCase
import com.ovi.where.domain.usecase.friend.ObserveFriendsUseCase
import com.ovi.where.domain.usecase.group.GetUserGroupsUseCase
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

@HiltViewModel
class ProfileViewModel @Inject constructor(
    application: Application,
    private val signOutUseCase: SignOutUseCase,
    private val observeCurrentUserUseCase: ObserveCurrentUserUseCase,
    private val updateProfileUseCase: UpdateProfileUseCase,
    private val updateBioUseCase: UpdateBioUseCase,
    private val updateUsernameUseCase: UpdateUsernameUseCase,
    private val checkUsernameAvailableUseCase: CheckUsernameAvailableUseCase,
    private val observeFriendsUseCase: ObserveFriendsUseCase,
    private val getUserGroupsUseCase: GetUserGroupsUseCase,
    private val firebaseStorage: FirebaseStorage,
    private val firebaseAuth: FirebaseAuth
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val _uiEvent = Channel<UiEvent>()
    val uiEvent = _uiEvent.receiveAsFlow()

    init {
        observeUser()
        observeFriendCount()
        loadGroupCount()
    }

    private fun observeUser() {
        viewModelScope.launch {
            observeCurrentUserUseCase().collect { user ->
                _uiState.value = _uiState.value.copy(
                    profile          = user?.toProfileUiModel(),
                    displayNameInput = user?.displayName ?: "",
                    bioInput         = user?.bio ?: "",
                    usernameInput    = user?.username ?: ""
                )
            }
        }
    }

    private fun observeFriendCount() {
        viewModelScope.launch {
            observeFriendsUseCase().collect { friends ->
                _uiState.value = _uiState.value.copy(friendCount = friends.size)
            }
        }
    }

    private fun loadGroupCount() {
        viewModelScope.launch {
            when (val result = getUserGroupsUseCase()) {
                is Resource.Success -> _uiState.value = _uiState.value.copy(
                    groupCount = result.data?.size ?: 0
                )
                else -> {}
            }
        }
    }

    // ── Display name ──────────────────────────────────────────────────────────

    fun onDisplayNameChange(name: String) {
        _uiState.value = _uiState.value.copy(displayNameInput = name, displayNameError = null)
    }

    fun setEditingName(editing: Boolean) {
        _uiState.value = _uiState.value.copy(isEditingName = editing)
        if (!editing) {
            _uiState.value = _uiState.value.copy(
                displayNameInput = _uiState.value.profile?.displayName ?: ""
            )
        }
    }

    fun onSaveDisplayName() {
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
                    _uiEvent.send(UiEvent.ShowSnackbar(UiText.StringResource(R.string.msg_profile_updated)))
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(isSaving = false)
                    _uiEvent.send(UiEvent.ShowSnackbar(UiText.StringResource(R.string.error_profile_update_failed)))
                }
                else -> {}
            }
        }
    }

    // ── Bio ───────────────────────────────────────────────────────────────────

    fun onBioChange(bio: String) {
        _uiState.value = _uiState.value.copy(bioInput = bio)
    }

    fun setEditingBio(editing: Boolean) {
        _uiState.value = _uiState.value.copy(isEditingBio = editing)
        if (!editing) {
            _uiState.value = _uiState.value.copy(bioInput = _uiState.value.profile?.bio ?: "")
        }
    }

    fun onSaveBio() {
        val bio = _uiState.value.bioInput.trim()
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            when (updateBioUseCase(bio)) {
                is Resource.Success -> {
                    _uiState.value = _uiState.value.copy(isSaving = false, isEditingBio = false)
                    _uiEvent.send(UiEvent.ShowSnackbar(UiText.StringResource(R.string.msg_profile_updated)))
                }
                else -> {
                    _uiState.value = _uiState.value.copy(isSaving = false)
                    _uiEvent.send(UiEvent.ShowSnackbar(UiText.StringResource(R.string.error_profile_update_failed)))
                }
            }
        }
    }

    // ── Username ──────────────────────────────────────────────────────────────

    fun onUsernameChange(username: String) {
        _uiState.value = _uiState.value.copy(usernameInput = username, usernameError = null)
    }

    fun setEditingUsername(editing: Boolean) {
        _uiState.value = _uiState.value.copy(isEditingUsername = editing)
        if (!editing) {
            _uiState.value = _uiState.value.copy(usernameInput = _uiState.value.profile?.username ?: "")
        }
    }

    fun onSaveUsername() {
        val username = _uiState.value.usernameInput.trim().lowercase()
        if (username.isBlank()) {
            _uiState.value = _uiState.value.copy(usernameError = "Username is required")
            return
        }
        if (username.length < 3) {
            _uiState.value = _uiState.value.copy(usernameError = "Username must be at least 3 characters")
            return
        }
        if (!username.matches(Regex("^[a-z0-9_]+$"))) {
            _uiState.value = _uiState.value.copy(usernameError = "Only letters, numbers and underscores allowed")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, usernameError = null)
            when (val result = updateUsernameUseCase(username)) {
                is Resource.Success -> {
                    _uiState.value = _uiState.value.copy(isSaving = false, isEditingUsername = false)
                    _uiEvent.send(UiEvent.ShowSnackbar(UiText.StringResource(R.string.msg_profile_updated)))
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(isSaving = false, usernameError = result.message)
                }
                else -> {}
            }
        }
    }

    // ── Photo ─────────────────────────────────────────────────────────────────

    fun onPhotoSelected(uri: Uri) {
        // BUG FIX: use actual Firebase Auth UID (not displayName)
        val uid = firebaseAuth.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploadingPhoto = true)
            try {
                val storageRef = firebaseStorage.reference
                    .child("profile_photos/$uid/${System.currentTimeMillis()}.jpg")
                storageRef.putFile(uri).await()
                val downloadUrl = storageRef.downloadUrl.await().toString()

                when (updateProfileUseCase(_uiState.value.displayNameInput, downloadUrl)) {
                    is Resource.Success -> {
                        _uiState.value = _uiState.value.copy(isUploadingPhoto = false)
                        _uiEvent.send(UiEvent.ShowSnackbar(UiText.StringResource(R.string.msg_profile_updated)))
                    }
                    else -> {
                        _uiState.value = _uiState.value.copy(isUploadingPhoto = false)
                        _uiEvent.send(UiEvent.ShowSnackbar(UiText.StringResource(R.string.error_photo_upload_failed)))
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isUploadingPhoto = false)
                _uiEvent.send(UiEvent.ShowSnackbar(UiText.StringResource(R.string.error_photo_upload_failed)))
            }
        }
    }

    // ── Sign out ──────────────────────────────────────────────────────────────

    fun onLogout() {
        viewModelScope.launch {
            signOutUseCase()
            _uiEvent.send(UiEvent.Navigate("login"))
        }
    }
}

data class ProfileUiState(
    val profile: UserProfileUiModel? = null,
    // Editable inputs
    val displayNameInput: String = "",
    val bioInput: String = "",
    val usernameInput: String = "",
    // Error states
    val displayNameError: String? = null,
    val usernameError: String? = null,
    // Edit mode flags
    val isEditingName: Boolean = false,
    val isEditingBio: Boolean = false,
    val isEditingUsername: Boolean = false,
    // Loading / saving
    val isSaving: Boolean = false,
    val isUploadingPhoto: Boolean = false,
    // Counts
    val friendCount: Int = 0,
    val groupCount: Int = 0
)
