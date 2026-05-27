package com.ovi.where.presentation.settings

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ovi.where.core.constants.AppConstants.STATE_FLOW_SUBSCRIBE_TIMEOUT_MS
import com.ovi.where.core.notification.NotificationHelper
import com.ovi.where.core.notification.NotificationSound
import com.ovi.where.data.repository.NotificationPreferencesRepository
import com.ovi.where.data.repository.NotificationSoundPreferencesRepository
import com.ovi.where.data.repository.QuietHoursRepository
import com.ovi.where.data.repository.QuietHoursSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for [NotificationPreferencesScreen].
 *
 * Owns three independent surfaces:
 *  • Per-channel toggles (chat, social, location, etc.) — backed by
 *    [NotificationPreferencesRepository]. Channel ids come from
 *    [NotificationHelper] which is the canonical owner.
 *  • Quiet hours — backed by [QuietHoursRepository], a separate DataStore
 *    namespace because the schema (window minutes + full-block flag) is
 *    different and we want to evolve them independently.
 *  • Per-channel ringtones — backed by
 *    [NotificationSoundPreferencesRepository]. Each change triggers
 *    [NotificationHelper.rebuildChannels] so the new sound is applied to
 *    the very next notification (Android caches channel sound at create
 *    time, so we have to rebuild under a versioned channel id).
 *
 * Optimistic UI: we update the local state immediately on tap, then write
 * to DataStore. The flow combine reads the canonical state back so any
 * conflict resolves to the persisted value within ~one frame.
 */
@HiltViewModel
class NotificationPrefsViewModel @Inject constructor(
    private val repository: NotificationPreferencesRepository,
    private val soundRepository: NotificationSoundPreferencesRepository,
    private val notificationHelper: NotificationHelper,
    private val quietHoursRepository: QuietHoursRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationPrefsUiState())
    val uiState: StateFlow<NotificationPrefsUiState> = _uiState.asStateFlow()

    /** Reactive view of the user's quiet-hours config. */
    val quietHours: StateFlow<QuietHoursSettings> = quietHoursRepository.observe()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STATE_FLOW_SUBSCRIBE_TIMEOUT_MS),
            QuietHoursSettings(
                enabled = false,
                startMinuteOfDay = QuietHoursRepository.DEFAULT_START_MIN,
                endMinuteOfDay = QuietHoursRepository.DEFAULT_END_MIN,
                fullBlock = false
            )
        )

    /**
     * Currently-playing preview MediaPlayer. Keeping a reference lets us
     * stop the previous preview when a new sound is tapped or the
     * settings screen is dismissed.
     */
    private var previewPlayer: MediaPlayer? = null

    /**
     * The sound currently being auditioned, or null when nothing is
     * playing. Drives the play/stop affordance in the picker so users
     * can see which row is active.
     */
    private val _nowPlaying = MutableStateFlow<NotificationSound?>(null)
    val nowPlaying: StateFlow<NotificationSound?> = _nowPlaying.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.isChannelEnabled(NotificationHelper.CHANNEL_SOCIAL),
                repository.isChannelEnabled(NotificationHelper.CHANNEL_LOCATION_UPDATES),
                repository.isChannelEnabled(NotificationHelper.CHANNEL_GROUP_ACTIVITY),
                repository.isChannelEnabled(NotificationHelper.CHANNEL_MESSAGES),
                repository.isChannelEnabled(NotificationHelper.CHANNEL_MEETUP)
            ) { values ->
                NotificationPrefsUiState(
                    friendRequestsEnabled = values[0],
                    locationUpdatesEnabled = values[1],
                    groupActivityEnabled = values[2],
                    chatMessagesEnabled = values[3],
                    meetupEnabled = values[4],
                    isLoading = false
                )
            }.collect { partial ->
                _uiState.update { current ->
                    partial.copy(
                        chatSound = current.chatSound,
                        friendSound = current.friendSound,
                        locationSound = current.locationSound,
                        groupSound = current.groupSound,
                        meetupSound = current.meetupSound,
                        generalSound = current.generalSound
                    )
                }
            }
        }

        viewModelScope.launch {
            combine(
                soundRepository.observeSound(NotificationHelper.CHANNEL_MESSAGES),
                soundRepository.observeSound(NotificationHelper.CHANNEL_SOCIAL),
                soundRepository.observeSound(NotificationHelper.CHANNEL_LOCATION_UPDATES),
                soundRepository.observeSound(NotificationHelper.CHANNEL_GROUP_ACTIVITY),
                soundRepository.observeSound(NotificationHelper.CHANNEL_MEETUP),
                soundRepository.observeSound(NotificationHelper.CHANNEL_GENERAL),
            ) { sounds ->
                sounds
            }.collect { sounds ->
                _uiState.update {
                    it.copy(
                        chatSound = sounds[0],
                        friendSound = sounds[1],
                        locationSound = sounds[2],
                        groupSound = sounds[3],
                        meetupSound = sounds[4],
                        generalSound = sounds[5]
                    )
                }
            }
        }
    }

    fun setFriendRequestsEnabled(enabled: Boolean) = persist(
        NotificationHelper.CHANNEL_SOCIAL, enabled
    ) { it.copy(friendRequestsEnabled = enabled) }

    fun setLocationUpdatesEnabled(enabled: Boolean) = persist(
        NotificationHelper.CHANNEL_LOCATION_UPDATES, enabled
    ) { it.copy(locationUpdatesEnabled = enabled) }

    fun setGroupActivityEnabled(enabled: Boolean) = persist(
        NotificationHelper.CHANNEL_GROUP_ACTIVITY, enabled
    ) { it.copy(groupActivityEnabled = enabled) }

    fun setChatMessagesEnabled(enabled: Boolean) = persist(
        NotificationHelper.CHANNEL_MESSAGES, enabled
    ) { it.copy(chatMessagesEnabled = enabled) }

    fun setMeetupEnabled(enabled: Boolean) = persist(
        NotificationHelper.CHANNEL_MEETUP, enabled
    ) { it.copy(meetupEnabled = enabled) }

    // ── Quiet hours ────────────────────────────────────────────────────

    fun setQuietHoursEnabled(enabled: Boolean) {
        viewModelScope.launch { quietHoursRepository.setEnabled(enabled) }
    }

    fun setQuietHoursWindow(startMinuteOfDay: Int, endMinuteOfDay: Int) {
        viewModelScope.launch {
            quietHoursRepository.setWindow(startMinuteOfDay, endMinuteOfDay)
        }
    }

    fun setQuietHoursFullBlock(full: Boolean) {
        viewModelScope.launch { quietHoursRepository.setFullBlock(full) }
    }

    // ── Ringtones ──────────────────────────────────────────────────────

    /**
     * Persists the user's chosen sound for [baseChannelId] and triggers
     * a channel rebuild so the new ringtone takes effect on the very
     * next notification.
     */
    fun setSoundFor(baseChannelId: String, sound: NotificationSound) {
        viewModelScope.launch {
            soundRepository.setSound(baseChannelId, sound)
            // Rebuild *after* the write so the snapshot read sees the
            // new id. createChannels() handles the cleanup of the
            // previous versioned channel.
            runCatching { notificationHelper.rebuildChannels() }
        }
    }

    /**
     * Plays a short preview of the chosen sound so the user can audition
     * it from the picker without firing a real notification. Stops any
     * previous preview first so rapid taps don't overlap.
     *
     * Audio routing notes:
     *  • Previews play through `USAGE_MEDIA` / `STREAM_MUSIC` so the user
     *    actually hears them. Using `USAGE_NOTIFICATION` here was wrong —
     *    when the phone is on Vibrate / Silent / DND (which is exactly
     *    when people open this screen), the notification stream is muted
     *    and the preview was inaudible.
     *  • For our bundled raw-resource sounds we use
     *    `MediaPlayer.create(context, rawRes)` which is the most reliable
     *    raw-resource code path. For [NotificationSound.Default] we fall
     *    back to the device's default notification ringtone via Uri.
     *  • If media volume is 0 we surface a Timber warning — it'll at
     *    least show up in `adb logcat` for debugging "the preview is
     *    silent" reports.
     *
     * Failure is silent at the UI layer — the picker still updates even
     * if the device can't play the file.
     */
    fun previewSound(sound: NotificationSound) {
        // Tapping the currently-playing row stops it — turns the play
        // button into a stop button.
        if (_nowPlaying.value == sound) {
            stopPreview()
            return
        }

        stopPreview()
        if (sound == NotificationSound.Silent) return

        // Hint if media volume is muted — the user will still see the
        // selection update but won't hear anything.
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audio != null && audio.getStreamVolume(AudioManager.STREAM_MUSIC) == 0) {
            Timber.i("Media volume is 0 — sound preview will be inaudible")
        }

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        try {
            val player = if (sound.rawRes != null) {
                // Most reliable path for bundled raw resources. Prepares
                // synchronously and returns a started-ready MediaPlayer.
                MediaPlayer.create(context, sound.rawRes)?.apply {
                    setAudioAttributes(attrs)
                }
            } else {
                // Default = device default notification ringtone — only
                // path that needs a Uri.
                val uri = sound.resolveUri(context) ?: return
                MediaPlayer().apply {
                    setAudioAttributes(attrs)
                    setDataSource(context, uri)
                    prepare()
                }
            }

            if (player == null) {
                Timber.w("MediaPlayer.create returned null for ${sound.id}")
                return
            }

            player.setOnCompletionListener {
                runCatching { it.release() }
                if (previewPlayer === it) {
                    previewPlayer = null
                    _nowPlaying.value = null
                }
            }
            player.setOnErrorListener { mp, what, extra ->
                Timber.w("Preview error for ${sound.id} (what=$what extra=$extra)")
                runCatching { mp.release() }
                if (previewPlayer === mp) {
                    previewPlayer = null
                    _nowPlaying.value = null
                }
                true
            }
            previewPlayer = player
            _nowPlaying.value = sound
            player.start()
        } catch (e: Exception) {
            Timber.w(e, "Failed to preview ${sound.id}")
            previewPlayer = null
            _nowPlaying.value = null
        }
    }

    private fun stopPreview() {
        previewPlayer?.let { mp ->
            runCatching {
                if (mp.isPlaying) mp.stop()
                mp.release()
            }
        }
        previewPlayer = null
        _nowPlaying.value = null
    }

    /** Public entry point for the UI to stop a preview (e.g. dialog dismiss). */
    fun stopPreviewIfPlaying() {
        stopPreview()
    }

    override fun onCleared() {
        stopPreview()
        super.onCleared()
    }

    private fun persist(
        channelId: String,
        enabled: Boolean,
        optimistic: (NotificationPrefsUiState) -> NotificationPrefsUiState
    ) {
        _uiState.update(optimistic)
        viewModelScope.launch { repository.setChannelEnabled(channelId, enabled) }
    }
}

data class NotificationPrefsUiState(
    val friendRequestsEnabled: Boolean = true,
    val locationUpdatesEnabled: Boolean = true,
    val groupActivityEnabled: Boolean = true,
    val chatMessagesEnabled: Boolean = true,
    val meetupEnabled: Boolean = true,
    // Per-channel ringtones (initially Default; replaced as DataStore loads).
    val chatSound: NotificationSound = NotificationSound.defaultFor(NotificationHelper.CHANNEL_MESSAGES),
    val friendSound: NotificationSound = NotificationSound.defaultFor(NotificationHelper.CHANNEL_SOCIAL),
    val locationSound: NotificationSound = NotificationSound.defaultFor(NotificationHelper.CHANNEL_LOCATION_UPDATES),
    val groupSound: NotificationSound = NotificationSound.defaultFor(NotificationHelper.CHANNEL_GROUP_ACTIVITY),
    val meetupSound: NotificationSound = NotificationSound.defaultFor(NotificationHelper.CHANNEL_MEETUP),
    val generalSound: NotificationSound = NotificationSound.defaultFor(NotificationHelper.CHANNEL_GENERAL),
    val isLoading: Boolean = true
)
