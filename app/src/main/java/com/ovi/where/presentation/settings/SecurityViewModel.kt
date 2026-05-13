package com.ovi.where.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class SecurityViewModel @Inject constructor(
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(SecurityUiState())
    val uiState: StateFlow<SecurityUiState> = _uiState.asStateFlow()

    val userEmail: String
        get() = firebaseAuth.currentUser?.email ?: ""

    fun sendPasswordResetEmail() {
        val email = firebaseAuth.currentUser?.email
        if (email.isNullOrBlank()) {
            _uiState.update {
                it.copy(errorMessage = "No email address associated with this account")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isPasswordResetLoading = true, errorMessage = null) }
            try {
                firebaseAuth.sendPasswordResetEmail(email).await()
                _uiState.update {
                    it.copy(
                        isPasswordResetLoading = false,
                        passwordResetSent = true
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isPasswordResetLoading = false,
                        errorMessage = mapErrorMessage(e)
                    )
                }
            }
        }
    }

    fun deleteAccount(password: String) {
        val user = firebaseAuth.currentUser
        if (user == null) {
            _uiState.update { it.copy(errorMessage = "Not authenticated") }
            return
        }

        val email = user.email
        if (email.isNullOrBlank()) {
            _uiState.update { it.copy(errorMessage = "No email address associated with this account") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isDeleteLoading = true, errorMessage = null) }
            try {
                // Re-authenticate the user before deletion
                val credential = EmailAuthProvider.getCredential(email, password)
                user.reauthenticate(credential).await()

                // Permanently delete the account
                user.delete().await()

                _uiState.update {
                    it.copy(
                        isDeleteLoading = false,
                        accountDeleted = true
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isDeleteLoading = false,
                        errorMessage = mapErrorMessage(e)
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearPasswordResetSent() {
        _uiState.update { it.copy(passwordResetSent = false) }
    }

    private fun mapErrorMessage(e: Exception): String {
        return when (e) {
            is FirebaseNetworkException -> "Network error. Please check your connection and try again."
            is FirebaseAuthInvalidCredentialsException -> "Invalid password. Please try again."
            is FirebaseAuthRecentLoginRequiredException -> "Please sign in again to perform this action."
            else -> e.message ?: "An unexpected error occurred. Please try again."
        }
    }
}

data class SecurityUiState(
    val isPasswordResetLoading: Boolean = false,
    val isDeleteLoading: Boolean = false,
    val passwordResetSent: Boolean = false,
    val accountDeleted: Boolean = false,
    val errorMessage: String? = null
)
