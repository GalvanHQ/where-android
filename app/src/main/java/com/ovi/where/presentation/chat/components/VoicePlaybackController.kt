package com.ovi.where.presentation.chat.components

import android.content.Context
import android.media.MediaPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Playback state for a single voice message.
 */
data class VoicePlaybackState(
    /** ID of the message currently loaded/playing (null if idle). */
    val activeMessageId: String? = null,
    /** Whether audio is currently playing. */
    val isPlaying: Boolean = false,
    /** Playback progress from 0.0 to 1.0. */
    val progress: Float = 0f,
    /** Current position in milliseconds. */
    val currentPositionMs: Long = 0L
)

/**
 * Manages voice message audio playback across the chat screen.
 *
 * Ensures only one voice message plays at a time (Requirement 11.10):
 * - If another voice message is tapped while one is playing, the current one stops and resets.
 *
 * Updates progress every 100ms during playback (Requirement 11.9).
 * Resets to beginning on playback complete (Requirement 11.9).
 *
 * Requirements: 11.8, 11.9, 11.10
 */
@Singleton
class VoicePlaybackController @Inject constructor() {

    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null

    private val _playbackState = MutableStateFlow(VoicePlaybackState())
    val playbackState: StateFlow<VoicePlaybackState> = _playbackState.asStateFlow()

    /**
     * Toggles play/pause for the given voice message.
     *
     * - If the same message is playing: pause it.
     * - If the same message is paused: resume it.
     * - If a different message is playing: stop current, reset, play new (Requirement 11.10).
     * - If nothing is playing: start playback.
     *
     * @param messageId Unique ID of the voice message.
     * @param audioUrl URL or local file path of the audio.
     * @param durationMs Total duration of the voice message in milliseconds.
     * @param context Android context for MediaPlayer initialization.
     * @param scope CoroutineScope for progress updates.
     */
    fun togglePlayPause(
        messageId: String,
        audioUrl: String,
        durationMs: Long,
        context: Context,
        scope: CoroutineScope
    ) {
        val currentState = _playbackState.value

        when {
            // Same message is currently playing → pause
            currentState.activeMessageId == messageId && currentState.isPlaying -> {
                pause()
            }
            // Same message is paused → resume
            currentState.activeMessageId == messageId && !currentState.isPlaying -> {
                resume(scope)
            }
            // Different message or no message → stop current and play new
            else -> {
                stopAndReset()
                play(messageId, audioUrl, durationMs, scope)
            }
        }
    }

    /**
     * Seeks to a specific position in the currently loaded voice message.
     *
     * @param messageId The message ID to seek (must match active message).
     * @param progress Target progress from 0.0 to 1.0.
     * @param durationMs Total duration for calculating seek position.
     */
    fun seekTo(messageId: String, progress: Float, durationMs: Long) {
        val currentState = _playbackState.value
        if (currentState.activeMessageId != messageId) return

        val targetMs = (progress * durationMs).toLong().coerceIn(0L, durationMs)
        try {
            mediaPlayer?.seekTo(targetMs.toInt())
            _playbackState.value = currentState.copy(
                progress = progress.coerceIn(0f, 1f),
                currentPositionMs = targetMs
            )
        } catch (_: IllegalStateException) {
            // MediaPlayer not in valid state for seeking
        }
    }

    /**
     * Pauses playback when the user navigates away from ChatScreen (Requirement 11.9).
     */
    fun pauseIfPlaying() {
        if (_playbackState.value.isPlaying) {
            pause()
        }
    }

    /**
     * Releases all resources. Call when the ChatScreen is disposed.
     */
    fun release() {
        stopAndReset()
    }

    private fun play(
        messageId: String,
        audioUrl: String,
        durationMs: Long,
        scope: CoroutineScope
    ) {
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(audioUrl)
                prepareAsync()
                setOnPreparedListener { mp ->
                    mp.start()
                    _playbackState.value = VoicePlaybackState(
                        activeMessageId = messageId,
                        isPlaying = true,
                        progress = 0f,
                        currentPositionMs = 0L
                    )
                    startProgressUpdates(durationMs, scope)
                }
                setOnCompletionListener {
                    // Requirement 11.9: On playback complete, reset to beginning and show play button
                    _playbackState.value = VoicePlaybackState(
                        activeMessageId = messageId,
                        isPlaying = false,
                        progress = 0f,
                        currentPositionMs = 0L
                    )
                    progressJob?.cancel()
                    progressJob = null
                    try {
                        seekTo(0)
                    } catch (_: IllegalStateException) {
                        // Ignore if player is in invalid state
                    }
                }
                setOnErrorListener { _, _, _ ->
                    stopAndReset()
                    true
                }
            }
        } catch (_: Exception) {
            stopAndReset()
        }
    }

    private fun pause() {
        progressJob?.cancel()
        progressJob = null
        try {
            mediaPlayer?.pause()
        } catch (_: IllegalStateException) {
            // Ignore
        }
        _playbackState.value = _playbackState.value.copy(isPlaying = false)
    }

    private fun resume(scope: CoroutineScope) {
        try {
            mediaPlayer?.start()
            _playbackState.value = _playbackState.value.copy(isPlaying = true)
            val durationMs = mediaPlayer?.duration?.toLong() ?: 0L
            startProgressUpdates(durationMs, scope)
        } catch (_: IllegalStateException) {
            stopAndReset()
        }
    }

    /**
     * Stops current playback and resets all state.
     */
    private fun stopAndReset() {
        progressJob?.cancel()
        progressJob = null
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: IllegalStateException) {
            // Ignore
        }
        mediaPlayer = null
        _playbackState.value = VoicePlaybackState()
    }

    /**
     * Updates progress every 100ms during playback (Requirement 11.9).
     */
    private fun startProgressUpdates(durationMs: Long, scope: CoroutineScope) {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                try {
                    val player = mediaPlayer ?: break
                    if (!player.isPlaying) break
                    val currentPos = player.currentPosition.toLong()
                    val progress = if (durationMs > 0) {
                        (currentPos.toFloat() / durationMs).coerceIn(0f, 1f)
                    } else 0f
                    _playbackState.value = _playbackState.value.copy(
                        progress = progress,
                        currentPositionMs = currentPos
                    )
                } catch (_: IllegalStateException) {
                    break
                }
                delay(100L) // Update every 100ms (Requirement 11.9)
            }
        }
    }
}
