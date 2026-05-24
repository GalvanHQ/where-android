package com.ovi.where.presentation.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.imageLoader
import com.ovi.where.data.local.db.AppDatabase
import com.ovi.where.domain.usecase.auth.ObserveCurrentUserUseCase
import com.ovi.where.domain.usecase.auth.SignOutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val signOutUseCase: SignOutUseCase,
    private val observeCurrentUserUseCase: ObserveCurrentUserUseCase,
    private val appDatabase: AppDatabase,
    @ApplicationContext private val context: Context
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
        if (_uiState.value.isSigningOut) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSigningOut = true) }
            // Clear all local data on sign out
            clearAllCache()
            signOutUseCase()
            _uiState.update { it.copy(isSigningOut = false, isSignedOut = true) }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            _uiState.update { it.copy(isClearingCache = true) }
            clearAllCache()
            _uiState.update { it.copy(isClearingCache = false, cacheCleared = true) }
        }
    }

    private suspend fun clearAllCache() = withContext(Dispatchers.IO) {
        // Clear Room database (all tables)
        appDatabase.clearAllTables()
        // Clear Coil image cache (memory + disk)
        context.imageLoader.memoryCache?.clear()
        context.imageLoader.diskCache?.clear()
        // Clear internal cache directory
        context.cacheDir.listFiles()?.forEach { file ->
            if (file.isDirectory) file.deleteRecursively() else file.delete()
        }
    }
}

data class SettingsUiState(
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String? = null,
    /**
     * True while [SettingsViewModel.signOut] is running. Drives the
     * sign-out row's spinner so the user gets immediate feedback that
     * the action took (clearing Room + Coil caches + auth tokens can
     * take a few hundred ms on cold storage).
     */
    val isSigningOut: Boolean = false,
    val isSignedOut: Boolean = false,
    val isClearingCache: Boolean = false,
    val cacheCleared: Boolean = false
)
