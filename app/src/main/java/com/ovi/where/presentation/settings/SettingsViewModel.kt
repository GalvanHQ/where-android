package com.ovi.where.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ovi.where.domain.usecase.auth.ObserveCurrentUserUseCase
import com.ovi.where.domain.usecase.auth.SignOutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val signOutUseCase: SignOutUseCase,
    private val observeCurrentUserUseCase: ObserveCurrentUserUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            observeCurrentUserUseCase().collect { user ->
                _uiState.update {
                    it.copy(
                        displayName = user?.displayName ?: "",
                        email = user?.email ?: "",
                        photoUrl = user?.photoUrl
                    )
                }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            signOutUseCase()
            _uiState.update { it.copy(isSignedOut = true) }
        }
    }
}

data class SettingsUiState(
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String? = null,
    val isSignedOut: Boolean = false
)
