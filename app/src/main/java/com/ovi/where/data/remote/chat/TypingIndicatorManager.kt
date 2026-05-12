package com.ovi.where.data.remote.chat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Manages typing indicator debounce logic for the chat system.
 *
 * - Throttles typing(true) emissions to at most one per [throttleWindowMs] (300ms).
 * - Emits stop-typing after [stopTypingDelayMs] (3000ms) of no keystrokes.
 * - Emits stop-typing immediately on message send or input clear.
 *
 * Validates: Requirements 7.1, 7.3, 7.4
 */
class TypingIndicatorManager(
    private val scope: CoroutineScope,
    private val sendTyping: suspend (Boolean) -> Unit,
    private val throttleWindowMs: Long = THROTTLE_WINDOW_MS,
    private val stopTypingDelayMs: Long = STOP_TYPING_DELAY_MS,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    companion object {
        const val THROTTLE_WINDOW_MS = 300L
        const val STOP_TYPING_DELAY_MS = 3000L
    }

    /** Timestamp of the last typing(true) emission. */
    private var lastTypingEmitTime: Long = 0L

    /** Whether we have emitted typing(true) and not yet emitted typing(false). */
    private var isCurrentlyTyping: Boolean = false

    /** Job for the stop-typing timer (3s after last keystroke). */
    private var stopTypingJob: Job? = null

    /**
     * Called on every keystroke that results in a non-empty input value.
     * Throttles typing(true) to at most one emission per [throttleWindowMs].
     * Resets the stop-typing timer on each call.
     */
    fun onKeystroke() {
        val now = clock()

        // Emit typing(true) only if throttle window has elapsed
        if (now - lastTypingEmitTime >= throttleWindowMs) {
            lastTypingEmitTime = now
            isCurrentlyTyping = true
            scope.launch { sendTyping(true) }
        }

        // Reset the stop-typing timer
        stopTypingJob?.cancel()
        stopTypingJob = scope.launch {
            delay(stopTypingDelayMs)
            emitStopTyping()
        }
    }

    /**
     * Called when the user sends a message or clears the input field.
     * Immediately emits stop-typing regardless of the debounce window.
     */
    fun onMessageSentOrInputCleared() {
        stopTypingJob?.cancel()
        stopTypingJob = null
        if (isCurrentlyTyping) {
            scope.launch { emitStopTyping() }
        }
    }

    /**
     * Resets internal state. Call when disconnecting or leaving the chat.
     */
    fun reset() {
        stopTypingJob?.cancel()
        stopTypingJob = null
        lastTypingEmitTime = 0L
        isCurrentlyTyping = false
    }

    private suspend fun emitStopTyping() {
        isCurrentlyTyping = false
        lastTypingEmitTime = 0L
        sendTyping(false)
    }
}
