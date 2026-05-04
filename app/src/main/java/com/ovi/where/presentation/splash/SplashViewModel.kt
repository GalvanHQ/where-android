package com.ovi.where.presentation.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ovi.where.data.local.prefs.UserPreferences
import com.ovi.where.domain.usecase.auth.ObserveCurrentUserUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val observeCurrentUserUseCase: ObserveCurrentUserUseCase,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(SplashUiState())
    val uiState: StateFlow<SplashUiState> = _uiState.asStateFlow()

    fun checkAuthStatus() {
        viewModelScope.launch {
            val onboardingComplete = userPreferences.isOnboardingComplete.first()
            observeCurrentUserUseCase().collect { user ->
                _uiState.value = SplashUiState(
                    isLoading = false,
                    isLoggedIn = user != null,
                    onboardingComplete = onboardingComplete
                )
            }
        }
    }
}

data class SplashUiState(
    val isLoading: Boolean = true,
    val isLoggedIn: Boolean = false,
    val onboardingComplete: Boolean = false
)
