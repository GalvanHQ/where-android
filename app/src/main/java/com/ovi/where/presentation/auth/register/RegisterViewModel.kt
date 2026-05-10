package com.ovi.where.presentation.auth.register

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ovi.where.R
import com.ovi.where.core.common.Resource
import com.ovi.where.core.common.UiEvent
import com.ovi.where.core.common.UiText

import com.ovi.where.domain.usecase.auth.CheckUsernameAvailableUseCase
import com.ovi.where.domain.usecase.auth.RegisterUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

import kotlinx.coroutines.channels.Channel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RegisterViewModel @Inject constructor(
    application: Application,
    private val registerUseCase: RegisterUseCase,
    private val checkUsernameAvailableUseCase: CheckUsernameAvailableUseCase
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    private val _uiEvent = Channel<UiEvent>()
    val uiEvent = _uiEvent.receiveAsFlow()

    private var usernameCheckJob: Job? = null

    fun onNameChange(name: String) {
        _uiState.value = _uiState.value.copy(name = name, nameError = null)
    }

    fun onUsernameChange(username: String) {
        val cleaned = username.lowercase().filter { it.isLetterOrDigit() || it == '_' }
        _uiState.value = _uiState.value.copy(
            username = cleaned,
            usernameError = null,
            isCheckingUsername = false
        )
        // Debounce uniqueness check
        usernameCheckJob?.cancel()
        if (cleaned.length >= 3) {
            usernameCheckJob = viewModelScope.launch {
                delay(600)
                _uiState.value = _uiState.value.copy(isCheckingUsername = true)
                val available = checkUsernameAvailableUseCase(cleaned)
                _uiState.value = _uiState.value.copy(
                    isCheckingUsername = false,
                    usernameError = if (available) null else "Username already taken"
                )
            }
        }
    }

    fun onEmailChange(email: String) {
        _uiState.value = _uiState.value.copy(email = email, emailError = null)
    }

    fun onPasswordChange(password: String) {
        _uiState.value = _uiState.value.copy(password = password, passwordError = null)
    }

    fun onConfirmPasswordChange(confirmPassword: String) {
        _uiState.value = _uiState.value.copy(confirmPassword = confirmPassword, confirmPasswordError = null)
    }

    fun onRegister() {
        val state = _uiState.value
        if (!validateInput(state)) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            when (val result = registerUseCase(state.name, state.username, state.email, state.password)) {
                is Resource.Success -> {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    _uiEvent.send(UiEvent.Navigate("email_verification"))
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = result.message)
                    _uiEvent.send(UiEvent.ShowSnackbar(UiText.DynamicString(
                        result.message ?: getApplication<Application>().getString(R.string.error_unknown)
                    )))
                }
                is Resource.Loading -> _uiState.value = _uiState.value.copy(isLoading = true)
            }
        }
    }

    private fun validateInput(state: RegisterUiState): Boolean {
        var isValid = true
        val ctx = getApplication<Application>()

        if (state.name.isBlank()) {
            _uiState.value = _uiState.value.copy(nameError = ctx.getString(R.string.error_name_required))
            isValid = false
        } else if (state.name.length < 2) {
            _uiState.value = _uiState.value.copy(nameError = ctx.getString(R.string.error_name_too_short))
            isValid = false
        }



        if (state.username.isBlank()) {
            _uiState.value = _uiState.value.copy(usernameError = "Username is required")
            isValid = false
        } else if (state.username.length < 3) {
            _uiState.value = _uiState.value.copy(usernameError = "Username must be at least 3 characters")
            isValid = false
        } else if (state.usernameError != null) {
            isValid = false
        }

        if (state.email.isBlank()) {
            _uiState.value = _uiState.value.copy(emailError = ctx.getString(R.string.error_email_required))
            isValid = false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(state.email).matches()) {
            _uiState.value = _uiState.value.copy(emailError = ctx.getString(R.string.error_invalid_email))
            isValid = false
        }

        if (state.password.isBlank()) {
            _uiState.value = _uiState.value.copy(passwordError = ctx.getString(R.string.error_password_required))
            isValid = false
        } else if (state.password.length < 6) {
            _uiState.value = _uiState.value.copy(passwordError = ctx.getString(R.string.error_password_too_short))
            isValid = false
        }

        if (state.confirmPassword.isBlank()) {
            _uiState.value = _uiState.value.copy(confirmPasswordError = ctx.getString(R.string.error_confirm_password_required))
            isValid = false
        } else if (state.password != state.confirmPassword) {
            _uiState.value = _uiState.value.copy(confirmPasswordError = ctx.getString(R.string.error_passwords_mismatch))
            isValid = false
        }

        return isValid
    }
}

data class RegisterUiState(
    val name: String = "",
    val username: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val nameError: String? = null,
    val usernameError: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null,
    val confirmPasswordError: String? = null,
    val isCheckingUsername: Boolean = false,
    val isUsernameAvailable: Boolean? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)
