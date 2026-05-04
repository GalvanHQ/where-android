package com.ovi.where.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ovi.where.data.local.prefs.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val userPreferences: UserPreferences
) : ViewModel() {
    fun completeOnboarding() {
        viewModelScope.launch {
            userPreferences.setOnboardingComplete(true)
        }
    }
}
