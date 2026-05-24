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
    private val userPreferences: com.ovi.where.data.local.prefs.UserPreferences,
    private val conversationShortcutManager: com.ovi.where.core.notification.ConversationShortcutManager,
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
        // ── Room: wipe every table ────────────────────────────────────────
        // covers conversations, messages, users, locations, notifications,
        // online status, friend cache, link previews, voice cache.
        runCatching { appDatabase.clearAllTables() }

        // ── Coil image cache ──────────────────────────────────────────────
        runCatching {
            context.imageLoader.memoryCache?.clear()
            context.imageLoader.diskCache?.clear()
        }

        // ── Internal cache directory ─────────────────────────────────────
        runCatching {
            context.cacheDir.listFiles()?.forEach { file ->
                if (file.isDirectory) file.deleteRecursively() else file.delete()
            }
        }

        // ── DataStore (UserPreferences) ──────────────────────────────────
        // Holds user_id, last GPS, sharing session, last share target,
        // onboarding flags, etc. Without this the next account would
        // inherit the previous user's seeded camera position and
        // half-finished sharing session metadata.
        runCatching { userPreferences.clearAll() }

        // ── Legacy SharedPreferences ─────────────────────────────────────
        // notification_permission_prefs tracks "have we asked the user
        // about post-notification permission yet" — reset so the next
        // account gets a fresh prompt experience.
        runCatching {
            context
                .getSharedPreferences("notification_permission_prefs", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply()
        }

        // ── Launcher conversation shortcuts ──────────────────────────────
        // Per-conversation long-lived shortcuts surface in the system
        // share sheet and notification bubbles. Leaving them after sign
        // out leaks the previous user's chat list to whoever signs in
        // next.
        runCatching { conversationShortcutManager.clearAll() }

        // ── Active notifications ─────────────────────────────────────────
        // Pull anything we've already posted to the system tray so the
        // next user doesn't see the previous user's chat / friend / meetup
        // alerts. Channels themselves stay (they're per-app system
        // settings, not user-scoped).
        runCatching {
            androidx.core.app.NotificationManagerCompat
                .from(context)
                .cancelAll()
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
