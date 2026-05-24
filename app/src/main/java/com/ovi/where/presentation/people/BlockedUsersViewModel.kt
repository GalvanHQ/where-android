package com.ovi.where.presentation.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ovi.where.core.common.Resource
import com.ovi.where.data.cache.UserCache
import com.ovi.where.domain.model.BlockEntry
import com.ovi.where.domain.model.User
import com.ovi.where.domain.repository.FriendshipRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI row for a single blocked user. Resolved by [BlockedUsersViewModel] —
 * it joins the [BlockEntry] (the canonical block record) with cached
 * [User] data so the row can show a real name + avatar instead of a uid.
 */
data class BlockedUserUiModel(
    val userId: String,
    val displayName: String,
    val username: String,
    val photoUrl: String?,
    val blockedAt: Long
)

/**
 * ViewModel for the Blocked Users screen.
 *
 * **Source of truth**: [FriendshipRepository.observeBlockedUsers] — a
 * Flow of every block document under `users/{me}/blocks`. We resolve
 * each entry's display name + photo through the persistent [UserCache]
 * (warm-write on every successful Firestore read) so the list paints
 * instantly even before any new network call lands.
 *
 * Unblock is optimistic: the row vanishes immediately, the callable
 * fires in the background, and on failure we revert and surface a
 * snackbar via [_snackbarEvent]. The Flow listener will reconcile any
 * remaining drift on the next snapshot.
 */
@HiltViewModel
class BlockedUsersViewModel @Inject constructor(
    private val friendshipRepository: FriendshipRepository,
    private val userCache: UserCache
) : ViewModel() {

    private val _uiState = MutableStateFlow(BlockedUsersUiState())
    val uiState: StateFlow<BlockedUsersUiState> = _uiState.asStateFlow()

    private val _snackbarEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val snackbarEvent: SharedFlow<String> = _snackbarEvent.asSharedFlow()

    init {
        observeBlockedUsers()
    }

    private fun observeBlockedUsers() {
        viewModelScope.launch {
            friendshipRepository.observeBlockedUsers().collect { entries ->
                resolveRows(entries)
            }
        }
    }

    /**
     * Joins each [BlockEntry] with cached profile data. Missing entries
     * trigger a background warm-up and the next snapshot lights them up
     * with the resolved metadata. Always renders something — the row
     * falls back to the uid if cache and warm-up both fail.
     */
    private suspend fun resolveRows(entries: List<BlockEntry>) {
        if (entries.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                blockedUsers = emptyList(),
                isLoading = false
            )
            return
        }

        val uids = entries.map { it.blockedUid }
        val cached = userCache.getCachedMany(uids)

        // Build initial rows from whatever's cached + a uid fallback for the
        // rest, so the screen paints fast.
        val rows = entries.map { entry ->
            val user: User? = cached[entry.blockedUid]
            BlockedUserUiModel(
                userId = entry.blockedUid,
                displayName = user?.displayName?.takeIf { it.isNotBlank() }
                    ?: entry.blockedUid.take(8),
                username = user?.username.orEmpty(),
                photoUrl = user?.photoUrl,
                blockedAt = entry.blockedAt
            )
        }
        _uiState.value = _uiState.value.copy(
            blockedUsers = rows.sortedByDescending { it.blockedAt },
            isLoading = false
        )

        // Background warm-up for any uids we didn't have cached. The
        // observe-cached Flow inside other VMs will pick up the writes;
        // ours simply re-resolves on the next blocked-users snapshot.
        val missing = uids.filterNot { cached.containsKey(it) }
        if (missing.isNotEmpty()) {
            userCache.warmUpMany(missing)
            // Re-resolve once warm-up completes so the rows show the
            // freshly cached names without waiting for the next block
            // snapshot.
            val refreshed = userCache.getCachedMany(uids)
            val refreshedRows = entries.map { entry ->
                val user: User? = refreshed[entry.blockedUid]
                BlockedUserUiModel(
                    userId = entry.blockedUid,
                    displayName = user?.displayName?.takeIf { it.isNotBlank() }
                        ?: entry.blockedUid.take(8),
                    username = user?.username.orEmpty(),
                    photoUrl = user?.photoUrl,
                    blockedAt = entry.blockedAt
                )
            }
            _uiState.value = _uiState.value.copy(
                blockedUsers = refreshedRows.sortedByDescending { it.blockedAt }
            )
        }
    }

    /**
     * Unblock a user with optimistic removal. Reverts and shows a
     * snackbar on failure.
     */
    fun unblock(userId: String) {
        val current = _uiState.value.blockedUsers
        val removed = current.find { it.userId == userId } ?: return
        // Optimistic removal
        _uiState.value = _uiState.value.copy(
            blockedUsers = current.filter { it.userId != userId }
        )
        viewModelScope.launch {
            val result = friendshipRepository.unblockUser(userId)
            if (result is Resource.Error) {
                // Revert
                _uiState.value = _uiState.value.copy(
                    blockedUsers = (_uiState.value.blockedUsers + removed)
                        .sortedByDescending { it.blockedAt }
                )
                _snackbarEvent.tryEmit(result.message ?: "Couldn't unblock")
            } else {
                _snackbarEvent.tryEmit("${removed.displayName} unblocked")
            }
        }
    }
}

data class BlockedUsersUiState(
    val blockedUsers: List<BlockedUserUiModel> = emptyList(),
    val isLoading: Boolean = true
)
