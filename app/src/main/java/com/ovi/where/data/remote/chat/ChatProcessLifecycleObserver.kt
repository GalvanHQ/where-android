package com.ovi.where.data.remote.chat

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.ovi.where.data.repository.ConversationRepositoryImpl
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Observes the process lifecycle (app foreground/background transitions) and manages
 * the ChatSocketIoClient and ConversationRepository Firestore listener accordingly.
 *
 * Both dependencies are injected as [dagger.Lazy] so they are NOT instantiated at app
 * startup. They are only resolved on the first foreground/background transition that
 * actually needs them (i.e., after the user has navigated to a chat screen and the
 * socket/listener have been initialized by the chat feature).
 *
 * Requirement 20.1: Chat singletons not instantiated until first method invoked by chat feature.
 * Requirement 20.4: While user hasn't navigated to chat screens, no chat singleton instantiation.
 * Requirement 20.5: App reaches first rendered frame within 500ms.
 * Requirement 21.4: On background (onStop, !isChangingConfigurations), disconnect socket
 * within 5s and remove Firestore listener.
 * Requirement 21.5: On foreground (onStart), reconnect socket and re-register listener within 2s.
 * Requirement 21.6: If reconnection doesn't reach CONNECTED within 2s, continue with
 * exponential backoff.
 *
 * Note: ProcessLifecycleOwner's onStop is only called when ALL activities are stopped
 * and isChangingConfigurations is inherently false (process lifecycle doesn't fire for
 * configuration changes).
 */
@Singleton
class ChatProcessLifecycleObserver @Inject constructor(
    private val lazyChatSocketIoClient: Lazy<ChatSocketIoClient>,
    private val lazyConversationRepository: Lazy<ConversationRepositoryImpl>
) : DefaultLifecycleObserver {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var backgroundDisconnectJob: Job? = null

    /**
     * Tracks whether the chat feature has been activated (i.e., the socket has been
     * connected at least once). Until this is true, lifecycle callbacks are no-ops
     * to avoid eagerly instantiating chat singletons before the user navigates to chat.
     *
     * Requirement 20.4: While user hasn't navigated to chat screens, no chat singleton instantiation.
     */
    @Volatile
    private var chatFeatureActivated = false

    /**
     * Marks the chat feature as activated. Called when the user first navigates to
     * a chat screen and the socket is connected. After this, lifecycle callbacks
     * will manage the socket and Firestore listener.
     */
    fun markChatFeatureActivated() {
        chatFeatureActivated = true
    }

    companion object {
        /** Delay before disconnecting socket when app goes to background (Requirement 21.4). */
        private const val BACKGROUND_DISCONNECT_DELAY_MS = 5000L
    }

    /**
     * Called when the app returns to foreground. Reconnects the socket and
     * re-registers the Firestore listener within 2s.
     *
     * No-op if the chat feature hasn't been activated yet (user hasn't navigated to chat).
     *
     * Requirement 21.5: On foreground (onStart), reconnect socket and re-register
     * listener within 2s.
     */
    override fun onStart(owner: LifecycleOwner) {
        if (!chatFeatureActivated) return

        Timber.i("ChatProcessLifecycleObserver onStart: app returning to foreground")

        // Cancel any pending background disconnect that hasn't fired yet
        backgroundDisconnectJob?.cancel()
        backgroundDisconnectJob = null

        // Reconnect socket (handles 2s timeout + exponential backoff internally)
        lazyChatSocketIoClient.get().reconnectFromForeground()

        // Re-register Firestore listener
        lazyConversationRepository.get().restartFirestoreListener()
    }

    /**
     * Called when the app goes to background (all activities stopped).
     * ProcessLifecycleOwner only fires onStop when isChangingConfigurations is false.
     *
     * No-op if the chat feature hasn't been activated yet (user hasn't navigated to chat).
     *
     * Disconnects the socket within 5s and removes the Firestore listener.
     *
     * Requirement 21.4: On background (onStop, !isChangingConfigurations),
     * disconnect socket within 5s, remove Firestore listener.
     */
    override fun onStop(owner: LifecycleOwner) {
        if (!chatFeatureActivated) return

        Timber.i("ChatProcessLifecycleObserver onStop: app going to background")

        // Remove Firestore listener immediately to stop incoming network callbacks
        lazyConversationRepository.get().stopFirestoreListener()

        // Disconnect socket within 5s (allows any in-flight operations to complete)
        backgroundDisconnectJob = scope.launch {
            delay(BACKGROUND_DISCONNECT_DELAY_MS)
            lazyChatSocketIoClient.get().disconnectForBackground()
        }
    }
}
