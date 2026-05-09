package com.ovi.where.presentation.auth.verification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ovi.where.core.common.Resource
import com.ovi.where.domain.usecase.auth.ReloadUserUseCase
import com.ovi.where.domain.usecase.auth.SendEmailVerificationUseCase
import com.ovi.where.domain.usecase.auth.SignOutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EmailVerificationViewModel @Inject constructor(
    private val sendEmailVerificationUseCase: SendEmailVerificationUseCase,
    private val reloadUserUseCase: ReloadUserUseCase,
    private val signOutUseCase: SignOutUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(EmailVerificationUiState())
    val uiState: StateFlow<EmailVerificationUiState> = _uiState.asStateFlow()

    fun resendVerificationEmail() {
        viewModelScope.launch {
            _uiState.update { it.copy(isResending = true, message = null) }
            when (val result = sendEmailVerificationUseCase()) {
                is Resource.Success -> _uiState.update {
                    it.copy(isResending = false, message = "Verification email sent!")
                }
                is Resource.Error -> _uiState.update {
                    it.copy(isResending = false, message = result.message)
                }
                else -> Unit
            }
        }
    }

    fun checkVerificationStatus() {
        viewModelScope.launch {
            _uiState.update { it.copy(isChecking = true, message = null) }
            when (val result = reloadUserUseCase()) {
                is Resource.Success -> {
                    val user = result.data
                    if (user != null && user.isEmailVerified) {
                        _uiState.update { it.copy(isChecking = false, isVerified = true) }
                    } else {
                        _uiState.update {
                            it.copy(
                                isChecking = false,
                                message = "Email not yet verified. Please check your inbox."
                            )
                        }
                    }
                }
                is Resource.Error -> _uiState.update {
                    it.copy(isChecking = false, message = result.message)
                }
                else -> Unit
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            signOutUseCase()
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}

data class EmailVerificationUiState(
    val isResending: Boolean = false,
    val isChecking: Boolean = false,
    val isVerified: Boolean = false,
    val message: String? = null
)
