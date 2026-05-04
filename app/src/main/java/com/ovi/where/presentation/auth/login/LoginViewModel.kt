package com.ovi.where.presentation.auth.login

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ovi.where.R
import com.ovi.where.core.common.Resource
import com.ovi.where.core.common.UiEvent
import com.ovi.where.core.common.UiText
import com.ovi.where.domain.usecase.auth.GoogleSignInUseCase
import com.ovi.where.domain.usecase.auth.SignInUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    application: Application,
    private val signInUseCase: SignInUseCase,
    private val googleSignInUseCase: GoogleSignInUseCase
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _uiEvent = Channel<UiEvent>()
    val uiEvent = _uiEvent.receiveAsFlow()

    fun onEmailChange(email: String) {
        _uiState.value = _uiState.value.copy(
            email = email,
            emailError = null
        )
    }

    fun onPasswordChange(password: String) {
        _uiState.value = _uiState.value.copy(
            password = password,
            passwordError = null
        )
    }
    
    fun onGoogleSignIn() {
        _uiEvent.trySend(UiEvent.LaunchGoogleSignIn)
    }
    
    fun onGoogleSignInResult(idToken: String?) {
        if (idToken == null) {
            viewModelScope.launch {
                _uiEvent.send(UiEvent.ShowSnackbar(
                    UiText.StringResource(R.string.error_google_sign_in_cancelled)
                ))
            }
            return
        }
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGoogleLoading = true)
            
            when (val result = googleSignInUseCase(idToken)) {
                is Resource.Success -> {
                    _uiState.value = _uiState.value.copy(isGoogleLoading = false)
                    _uiEvent.send(UiEvent.Navigate("home"))
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(isGoogleLoading = false)
                    _uiEvent.send(UiEvent.ShowSnackbar(
                        UiText.DynamicString(result.message ?: getApplication<Application>().getString(R.string.error_unknown))
                    ))
                }
                is Resource.Loading -> {
                    _uiState.value = _uiState.value.copy(isGoogleLoading = true)
                }
            }
        }
    }

    fun onSignIn() {
        val state = _uiState.value
        
        if (!validateInput(state)) return
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            when (val result = signInUseCase(state.email, state.password)) {
                is Resource.Success -> {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    _uiEvent.send(UiEvent.Navigate("home"))
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.message
                    )
                    _uiEvent.send(UiEvent.ShowSnackbar(UiText.DynamicString(result.message ?: getApplication<Application>().getString(R.string.error_unknown))))
                }
                is Resource.Loading -> {
                    _uiState.value = _uiState.value.copy(isLoading = true)
                }
            }
        }
    }
    
    private fun validateInput(state: LoginUiState): Boolean {
        var isValid = true
        
        if (state.email.isBlank()) {
            _uiState.value = _uiState.value.copy(emailError = getApplication<Application>().getString(R.string.error_email_required))
            isValid = false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(state.email).matches()) {
            _uiState.value = _uiState.value.copy(emailError = getApplication<Application>().getString(R.string.error_invalid_email))
            isValid = false
        }
        
        if (state.password.isBlank()) {
            _uiState.value = _uiState.value.copy(passwordError = getApplication<Application>().getString(R.string.error_password_required))
            isValid = false
        } else if (state.password.length < 6) {
            _uiState.value = _uiState.value.copy(passwordError = getApplication<Application>().getString(R.string.error_password_too_short))
            isValid = false
        }
        
        return isValid
    }
}

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val emailError: String? = null,
    val passwordError: String? = null,
    val isLoading: Boolean = false,
    val isGoogleLoading: Boolean = false,
    val error: String? = null
)
