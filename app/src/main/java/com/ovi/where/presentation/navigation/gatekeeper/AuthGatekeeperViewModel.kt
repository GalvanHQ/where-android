package com.ovi.where.presentation.navigation.gatekeeper

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ovi.where.data.local.prefs.UserPreferences
import com.ovi.where.domain.usecase.auth.ObserveCurrentUserUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Determines initial navigation destination based on auth state.
 * Replaces the legacy SplashViewModel.
 */
@HiltViewModel
class AuthGatekeeperViewModel @Inject constructor(
    private val observeCurrentUserUseCase: ObserveCurrentUserUseCase,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthGatekeeperState())
    val uiState: StateFlow<AuthGatekeeperState> = _uiState.asStateFlow()

    fun resolve() {
        viewModelScope.launch {
            val onboardingDone = userPreferences.isOnboardingComplete.first()
            observeCurrentUserUseCase().collect { user ->
                _uiState.value = AuthGatekeeperState(
                    isLoading = false,
                    isLoggedIn = user != null,
                    onboardingComplete = onboardingDone,
                    isEmailVerified = user?.isEmailVerified ?: false,
                    isProfileComplete = user?.isProfileComplete ?: false
                )
            }
        }
    }
}

data class AuthGatekeeperState(
    val isLoading: Boolean = true,
    val isLoggedIn: Boolean = false,
    val onboardingComplete: Boolean = false,
    val isEmailVerified: Boolean = false,
    val isProfileComplete: Boolean = false
)
