package com.ovi.where.data.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * VoiceRecorder handles audio recording using Android's MediaRecorder API.
 *
 * Encodes audio in AAC format at 64kbps bitrate with 16kHz sample rate.
 * Maximum recording duration is 5 minutes (300 seconds).
 *
 * Requirements: 11.1, 11.2, 11.3, 11.4, 11.5, 11.6, 11.7, 11.11
 */
@Singleton
class VoiceRecorder @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        /** AAC encoding bitrate: 64kbps (Requirement 11.6) */
        private const val AUDIO_BITRATE = 64_000

        /** Sample rate: 16kHz (Requirement 11.6) */
        private const val AUDIO_SAMPLE_RATE = 16_000

        /** Maximum recording duration: 5 minutes in milliseconds (Requirement 11.7) */
        const val MAX_DURATION_MS = 5 * 60 * 1000L

        /** Minimum recording duration to send: 1 second (Requirement 11.4, 11.5) */
        const val MIN_DURATION_MS = 1_000L

        /** Waveform amplitude sampling interval */
        private const val AMPLITUDE_SAMPLE_INTERVAL_MS = 100L

        /** Maximum number of waveform samples to keep for visualization */
        private const val MAX_WAVEFORM_SAMPLES = 50
    }

    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var recordingStartTime: Long = 0L
    private var timerJob: Job? = null
    private var amplitudeJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _state = MutableStateFlow(VoiceRecorderState())
    val state: StateFlow<VoiceRecorderState> = _state.asStateFlow()

    /**
     * Starts audio recording.
     *
     * Creates a temporary AAC file and configures MediaRecorder with:
     * - AAC encoding at 64kbps
     * - 16kHz sample rate
     * - Max duration 5 minutes (auto-stops via callback)
     *
     * @return true if recording started successfully, false otherwise
     *
     * Requirements: 11.1, 11.6
     */
    fun startRecording(): Boolean {
        if (_state.value.isRecording) return false

        val file = createOutputFile() ?: return false
        outputFile = file

        try {
            val recorder = createMediaRecorder()
            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(AUDIO_BITRATE)
                setAudioSamplingRate(AUDIO_SAMPLE_RATE)
                setAudioChannels(1) // Mono
                setOutputFile(file.absolutePath)
                setMaxDuration((MAX_DURATION_MS).toInt())
                setOnInfoListener { _, what, _ ->
                    if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                        // Requirement 11.7: Auto-stop at max duration
                        onMaxDurationReached()
                    }
                }
                prepare()
                start()
            }

            mediaRecorder = recorder
            recordingStartTime = System.currentTimeMillis()

            _state.value = VoiceRecorderState(
                isRecording = true,
                durationMs = 0L,
                waveformAmplitudes = emptyList(),
                isLocked = false
            )

            startTimer()
            startAmplitudeSampling()

            return true
        } catch (e: Exception) {
            Timber.e(e, "Failed to start voice recording")
            cleanup()
            return false
        }
    }

    /**
     * Stops recording and returns the result.
     *
     * @return [VoiceRecordingResult] with the file path and duration,
     *         or null if recording was not active or duration < 1s
     *
     * Requirements: 11.4, 11.5
     */
    fun stopRecording(): VoiceRecordingResult? {
        if (!_state.value.isRecording) return null

        val duration = System.currentTimeMillis() - recordingStartTime

        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error stopping MediaRecorder")
        }

        mediaRecorder = null
        stopTimer()
        stopAmplitudeSampling()

        val file = outputFile
        outputFile = null

        _state.value = VoiceRecorderState()

        // Requirement 11.5: Discard if < 1 second
        if (duration < MIN_DURATION_MS) {
            file?.delete()
            return null
        }

        return if (file != null && file.exists()) {
            VoiceRecordingResult(
                filePath = file.absolutePath,
                durationMs = duration
            )
        } else {
            null
        }
    }

    /**
     * Cancels the current recording and discards the audio data.
     *
     * Requirement 11.2: Slide left > 100dp cancels recording and discards audio.
     */
    fun cancelRecording() {
        if (!_state.value.isRecording) return

        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error cancelling MediaRecorder")
        }

        mediaRecorder = null
        stopTimer()
        stopAmplitudeSampling()

        // Delete the recorded file
        outputFile?.delete()
        outputFile = null

        _state.value = VoiceRecorderState()
    }

    /**
     * Locks the recording into hands-free mode.
     * The user can release the button while recording continues.
     *
     * Requirement 11.3: Slide up > 48dp locks into hands-free mode.
     */
    fun lockRecording() {
        if (!_state.value.isRecording) return
        _state.value = _state.value.copy(isLocked = true)
    }

    /**
     * Returns the current recording duration in milliseconds.
     */
    fun getCurrentDurationMs(): Long {
        return if (_state.value.isRecording) {
            System.currentTimeMillis() - recordingStartTime
        } else {
            0L
        }
    }

    /**
     * Called when max duration (5 minutes) is reached.
     * Auto-stops recording — the ViewModel should send the message.
     *
     * Requirement 11.7: Auto-stop and send at max duration.
     */
    private fun onMaxDurationReached() {
        scope.launch {
            _state.value = _state.value.copy(maxDurationReached = true)
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive) {
                delay(100L)
                val elapsed = System.currentTimeMillis() - recordingStartTime
                _state.value = _state.value.copy(durationMs = elapsed)
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    private fun startAmplitudeSampling() {
        amplitudeJob?.cancel()
        amplitudeJob = scope.launch {
            while (isActive) {
                delay(AMPLITUDE_SAMPLE_INTERVAL_MS)
                val amplitude = try {
                    mediaRecorder?.maxAmplitude ?: 0
                } catch (e: Exception) {
                    0
                }
                // Normalize amplitude to 0.0-1.0 range (max amplitude is ~32767)
                val normalized = (amplitude / 32767f).coerceIn(0f, 1f)
                val currentAmplitudes = _state.value.waveformAmplitudes.toMutableList()
                currentAmplitudes.add(normalized)
                // Keep only the last MAX_WAVEFORM_SAMPLES
                if (currentAmplitudes.size > MAX_WAVEFORM_SAMPLES) {
                    currentAmplitudes.removeAt(0)
                }
                _state.value = _state.value.copy(waveformAmplitudes = currentAmplitudes)
            }
        }
    }

    private fun stopAmplitudeSampling() {
        amplitudeJob?.cancel()
        amplitudeJob = null
    }

    @Suppress("DEPRECATION")
    private fun createMediaRecorder(): MediaRecorder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }
    }

    private fun createOutputFile(): File? {
        return try {
            val dir = File(context.cacheDir, "voice_messages")
            if (!dir.exists()) dir.mkdirs()
            File.createTempFile("voice_", ".m4a", dir)
        } catch (e: Exception) {
            Timber.e(e, "Failed to create voice recording output file")
            null
        }
    }

    private fun cleanup() {
        try {
            mediaRecorder?.release()
        } catch (_: Exception) {}
        mediaRecorder = null
        outputFile?.delete()
        outputFile = null
        stopTimer()
        stopAmplitudeSampling()
        _state.value = VoiceRecorderState()
    }
}

/**
 * Represents the current state of the voice recorder.
 */
data class VoiceRecorderState(
    /** Whether recording is currently active. */
    val isRecording: Boolean = false,
    /** Current recording duration in milliseconds. */
    val durationMs: Long = 0L,
    /** Normalized waveform amplitudes (0.0 to 1.0) for visualization. */
    val waveformAmplitudes: List<Float> = emptyList(),
    /** Whether recording is locked in hands-free mode (Requirement 11.3). */
    val isLocked: Boolean = false,
    /** Whether max duration was reached (Requirement 11.7). */
    val maxDurationReached: Boolean = false
)

/**
 * Result of a completed voice recording.
 */
data class VoiceRecordingResult(
    /** Absolute path to the recorded audio file (AAC in .m4a container). */
    val filePath: String,
    /** Duration of the recording in milliseconds. */
    val durationMs: Long
)
