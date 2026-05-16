package com.ovi.where.presentation.chat.components

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Unit tests for VoiceMessageBubble logic.
 *
 * Validates: Requirements 11.8, 11.9, 11.10
 *
 * Tests the formatVoiceDuration utility function and VoicePlaybackState behavior.
 * Composable rendering is validated via manual/instrumented tests.
 */
class VoiceMessageBubbleTest : StringSpec({

    "formatVoiceDuration formats 0ms as 0:00" {
        formatVoiceDuration(0L) shouldBe "0:00"
    }

    "formatVoiceDuration formats 5000ms as 0:05" {
        formatVoiceDuration(5000L) shouldBe "0:05"
    }

    "formatVoiceDuration formats 59000ms as 0:59" {
        formatVoiceDuration(59000L) shouldBe "0:59"
    }

    "formatVoiceDuration formats 60000ms as 1:00" {
        formatVoiceDuration(60000L) shouldBe "1:00"
    }

    "formatVoiceDuration formats 65000ms as 1:05" {
        formatVoiceDuration(65000L) shouldBe "1:05"
    }

    "formatVoiceDuration formats 300000ms (5 minutes) as 5:00" {
        formatVoiceDuration(300000L) shouldBe "5:00"
    }

    "formatVoiceDuration formats 125000ms as 2:05" {
        formatVoiceDuration(125000L) shouldBe "2:05"
    }

    "formatVoiceDuration handles negative values by clamping to 0:00" {
        formatVoiceDuration(-1000L) shouldBe "0:00"
    }

    "formatVoiceDuration formats sub-second values as 0:00" {
        formatVoiceDuration(999L) shouldBe "0:00"
    }

    "formatVoiceDuration formats 1000ms as 0:01" {
        formatVoiceDuration(1000L) shouldBe "0:01"
    }

    // ─── VoicePlaybackState tests ─────────────────────────────────────────────

    "VoicePlaybackState defaults to idle state" {
        val state = VoicePlaybackState()
        state.activeMessageId shouldBe null
        state.isPlaying shouldBe false
        state.progress shouldBe 0f
        state.currentPositionMs shouldBe 0L
    }

    "VoicePlaybackState can represent playing state" {
        val state = VoicePlaybackState(
            activeMessageId = "msg-123",
            isPlaying = true,
            progress = 0.5f,
            currentPositionMs = 30000L
        )
        state.activeMessageId shouldBe "msg-123"
        state.isPlaying shouldBe true
        state.progress shouldBe 0.5f
        state.currentPositionMs shouldBe 30000L
    }

    "VoicePlaybackState can represent paused state" {
        val state = VoicePlaybackState(
            activeMessageId = "msg-456",
            isPlaying = false,
            progress = 0.75f,
            currentPositionMs = 45000L
        )
        state.activeMessageId shouldBe "msg-456"
        state.isPlaying shouldBe false
        state.progress shouldBe 0.75f
        state.currentPositionMs shouldBe 45000L
    }

    "VoicePlaybackState reset represents playback complete state" {
        // Requirement 11.9: On playback complete, reset to beginning and show play button
        val completedState = VoicePlaybackState(
            activeMessageId = "msg-789",
            isPlaying = false,
            progress = 0f,
            currentPositionMs = 0L
        )
        completedState.isPlaying shouldBe false
        completedState.progress shouldBe 0f
        completedState.currentPositionMs shouldBe 0L
    }

    // ─── VoicePlaybackController state tests ──────────────────────────────────

    "VoicePlaybackController initial state is idle" {
        val controller = VoicePlaybackController()
        val state = controller.playbackState.value
        state.activeMessageId shouldBe null
        state.isPlaying shouldBe false
        state.progress shouldBe 0f
        state.currentPositionMs shouldBe 0L
    }

    "VoicePlaybackController pauseIfPlaying does nothing when not playing" {
        val controller = VoicePlaybackController()
        controller.pauseIfPlaying()
        val state = controller.playbackState.value
        state.isPlaying shouldBe false
    }

    "VoicePlaybackController release resets to idle state" {
        val controller = VoicePlaybackController()
        controller.release()
        val state = controller.playbackState.value
        state.activeMessageId shouldBe null
        state.isPlaying shouldBe false
        state.progress shouldBe 0f
    }

    "VoicePlaybackController seekTo does nothing when no active message" {
        val controller = VoicePlaybackController()
        controller.seekTo("msg-123", 0.5f, 60000L)
        val state = controller.playbackState.value
        // Should remain idle since no message is active
        state.activeMessageId shouldBe null
        state.progress shouldBe 0f
    }
})
