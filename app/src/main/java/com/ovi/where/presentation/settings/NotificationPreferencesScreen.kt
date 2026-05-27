package com.ovi.where.presentation.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ovi.where.core.notification.NotificationHelper
import com.ovi.where.core.notification.NotificationSound
import com.ovi.where.core.theme.Dimens
import com.ovi.where.presentation.common.WhereTopAppBar

/**
 * Notification Preferences screen.
 *
 * Designed to match the rest of the Where settings surface:
 *  • A subtle indigo-tinted hero card at the top introduces the screen
 *    in the same way SettingsScreen.kt opens with a profile card.
 *  • Each preference group is wrapped in a `surfaceContainerLow` card and
 *    headed with a brand-tinted label (the `MaterialTheme.colorScheme
 *    .primary` titleSmall pattern shared with AppearanceScreen).
 *  • Rows use the standard 56-dp icon-tile + title/subtitle layout from
 *    SettingsScreen.SettingItemRow, so toggles, sound pickers, and time
 *    rows all sit on the same horizontal grid.
 *  • Sound picker is a single dialog with leading thumbnails, an inline
 *    "Now Playing" indicator, and a "Save" affordance — auditioning is a
 *    one-tap action and never commits accidentally.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationPreferencesScreen(
    onNavigateBack: () -> Unit,
    viewModel: NotificationPrefsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val quiet by viewModel.quietHours.collectAsState()
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    // (channelId, currentSound) — null = picker hidden.
    var pickerTarget by remember { mutableStateOf<Pair<String, NotificationSound>?>(null) }

    Scaffold(
        topBar = {
            WhereTopAppBar(
                title = "Notifications",
                onNavigateBack = onNavigateBack
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.spaceLarge)
        ) {
            Spacer(modifier = Modifier.height(Dimens.spaceLarge))

            // ── Categories ──────────────────────────────────────────────
            SectionLabel(
                title = "Categories",
                helper = "Choose which notifications you'd like to receive."
            )
            SectionCard {
                NotificationToggleRow(
                    icon = Icons.AutoMirrored.Outlined.Chat,
                    title = "Chat Messages",
                    subtitle = "New messages and replies",
                    checked = uiState.chatMessagesEnabled,
                    onCheckedChange = viewModel::setChatMessagesEnabled
                )
                RowDivider()
                NotificationToggleRow(
                    icon = Icons.Outlined.PersonAdd,
                    title = "Friend Requests",
                    subtitle = "New requests and acceptances",
                    checked = uiState.friendRequestsEnabled,
                    onCheckedChange = viewModel::setFriendRequestsEnabled
                )
                RowDivider()
                NotificationToggleRow(
                    icon = Icons.Outlined.LocationOn,
                    title = "Location Updates",
                    subtitle = "When friends share their location",
                    checked = uiState.locationUpdatesEnabled,
                    onCheckedChange = viewModel::setLocationUpdatesEnabled
                )
                RowDivider()
                NotificationToggleRow(
                    icon = Icons.Outlined.Group,
                    title = "Group Activity",
                    subtitle = "Members joining or leaving",
                    checked = uiState.groupActivityEnabled,
                    onCheckedChange = viewModel::setGroupActivityEnabled
                )
                RowDivider()
                NotificationToggleRow(
                    icon = Icons.Outlined.Place,
                    title = "Meetup",
                    subtitle = "Destinations and arrival pings",
                    checked = uiState.meetupEnabled,
                    onCheckedChange = viewModel::setMeetupEnabled
                )
            }

            Spacer(modifier = Modifier.height(Dimens.spaceXLarge))

            // ── Sounds ──────────────────────────────────────────────────
            SectionLabel(
                title = "Ringtones",
                helper = "Choose a custom ringtone for each category. Tap an option to preview."
            )
            SectionCard {
                SoundRow(
                    icon = Icons.AutoMirrored.Outlined.Chat,
                    title = "Chat Messages",
                    sound = uiState.chatSound,
                    onClick = {
                        pickerTarget = NotificationHelper.CHANNEL_MESSAGES to uiState.chatSound
                    }
                )
                RowDivider()
                SoundRow(
                    icon = Icons.Outlined.PersonAdd,
                    title = "Friend Requests",
                    sound = uiState.friendSound,
                    onClick = {
                        pickerTarget = NotificationHelper.CHANNEL_SOCIAL to uiState.friendSound
                    }
                )
                RowDivider()
                SoundRow(
                    icon = Icons.Outlined.LocationOn,
                    title = "Location Updates",
                    sound = uiState.locationSound,
                    onClick = {
                        pickerTarget =
                            NotificationHelper.CHANNEL_LOCATION_UPDATES to uiState.locationSound
                    }
                )
                RowDivider()
                SoundRow(
                    icon = Icons.Outlined.Group,
                    title = "Group Activity",
                    sound = uiState.groupSound,
                    onClick = {
                        pickerTarget =
                            NotificationHelper.CHANNEL_GROUP_ACTIVITY to uiState.groupSound
                    }
                )
                RowDivider()
                SoundRow(
                    icon = Icons.Outlined.Place,
                    title = "Meetup",
                    sound = uiState.meetupSound,
                    onClick = {
                        pickerTarget = NotificationHelper.CHANNEL_MEETUP to uiState.meetupSound
                    }
                )
                RowDivider()
                SoundRow(
                    icon = Icons.Outlined.NotificationsActive,
                    title = "General",
                    sound = uiState.generalSound,
                    onClick = {
                        pickerTarget = NotificationHelper.CHANNEL_GENERAL to uiState.generalSound
                    }
                )
            }

            Spacer(modifier = Modifier.height(Dimens.spaceXLarge))

            // ── Quiet Hours ─────────────────────────────────────────────
            SectionLabel(
                title = "Quiet Hours",
                helper = "Silence non-essential notifications during a daily window. Close friends and meetup arrivals always come through."
            )
            SectionCard {
                NotificationToggleRow(
                    icon = Icons.Outlined.NightsStay,
                    title = "Enable Quiet Hours",
                    subtitle = if (quiet.enabled)
                        formatWindow(quiet.startMinuteOfDay, quiet.endMinuteOfDay)
                    else
                        "Tap to set a quiet window",
                    checked = quiet.enabled,
                    onCheckedChange = viewModel::setQuietHoursEnabled
                )

                AnimatedVisibility(
                    visible = quiet.enabled,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        RowDivider()
                        TimeRow(
                            icon = Icons.Outlined.Bedtime,
                            label = "Start",
                            minuteOfDay = quiet.startMinuteOfDay,
                            onClick = { showStartPicker = true }
                        )
                        RowDivider()
                        TimeRow(
                            icon = Icons.Outlined.Tune,
                            label = "End",
                            minuteOfDay = quiet.endMinuteOfDay,
                            onClick = { showEndPicker = true }
                        )
                        RowDivider()
                        NotificationToggleRow(
                            icon = Icons.Outlined.DoNotDisturbOn,
                            title = "Block instead of silence",
                            subtitle = "Drop quiet-hour pushes entirely instead of posting them silently",
                            checked = quiet.fullBlock,
                            onCheckedChange = viewModel::setQuietHoursFullBlock
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Dimens.space3XLarge))
        }
    }

    if (showStartPicker) {
        TimePickerDialog(
            initialMinuteOfDay = quiet.startMinuteOfDay,
            onConfirm = { newStart ->
                viewModel.setQuietHoursWindow(newStart, quiet.endMinuteOfDay)
                showStartPicker = false
            },
            onDismiss = { showStartPicker = false }
        )
    }
    if (showEndPicker) {
        TimePickerDialog(
            initialMinuteOfDay = quiet.endMinuteOfDay,
            onConfirm = { newEnd ->
                viewModel.setQuietHoursWindow(quiet.startMinuteOfDay, newEnd)
                showEndPicker = false
            },
            onDismiss = { showEndPicker = false }
        )
    }

    pickerTarget?.let { (channelId, current) ->
        val nowPlaying by viewModel.nowPlaying.collectAsState()
        SoundPickerDialog(
            channelDisplayName = channelDisplayName(channelId),
            currentSound = current,
            nowPlaying = nowPlaying,
            onDismiss = {
                viewModel.stopPreviewIfPlaying()
                pickerTarget = null
            },
            onPreview = viewModel::previewSound,
            onPick = { picked ->
                viewModel.stopPreviewIfPlaying()
                viewModel.setSoundFor(channelId, picked)
                pickerTarget = null
            }
        )
    }
}

// ─── Layout primitives ───────────────────────────────────────────────────

@Composable
private fun SectionLabel(title: String, helper: String? = null) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = Dimens.spaceSmall)
    )
    if (helper != null) {
        Spacer(Modifier.height(Dimens.spaceXSmall))
        Text(
            text = helper,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Dimens.spaceSmall)
        )
    }
    Spacer(Modifier.height(Dimens.spaceMedium))
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp
    ) {
        Column { content() }
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 56.dp + Dimens.spaceLarge),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
    )
}

// ─── Rows ────────────────────────────────────────────────────────────────

/**
 * Standard Where settings row layout — 40-dp brand-tinted icon tile,
 * title + subtitle column, trailing slot. Mirrors SettingsScreen.SettingItemRow
 * so the whole settings stack reads as one cohesive surface.
 */
@Composable
private fun PreferenceRowFrame(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: (() -> Unit)? = null,
    role: Role? = null,
    trailing: @Composable () -> Unit
) {
    val rowMod = if (onClick != null) {
        Modifier
            .fillMaxWidth()
            .clickable(role = role, onClick = onClick)
    } else {
        Modifier.fillMaxWidth()
    }

    Row(
        modifier = rowMod
            .padding(horizontal = Dimens.spaceLarge, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(Dimens.settingIconContainer)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(Dimens.cornerSmall)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(Dimens.iconSizeMedium)
            )
        }
        Spacer(Modifier.width(Dimens.spaceLarge))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(Dimens.spaceMedium))
        trailing()
    }
}

@Composable
private fun NotificationToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    PreferenceRowFrame(
        icon = icon,
        title = title,
        subtitle = subtitle,
        onClick = { onCheckedChange(!checked) },
        role = Role.Switch,
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    )
}

/**
 * Sound row — opens [SoundPickerDialog] on click. Trailing slot shows
 * a tiny "playback" pill so the user can see the chosen sound at a
 * glance even before tapping.
 */
@Composable
private fun SoundRow(
    icon: ImageVector,
    title: String,
    sound: NotificationSound,
    onClick: () -> Unit
) {
    PreferenceRowFrame(
        icon = icon,
        title = title,
        subtitle = null,
        onClick = onClick,
        role = Role.Button,
        trailing = {
            SoundPill(sound = sound)
        }
    )
}

@Composable
private fun SoundPill(sound: NotificationSound) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
            .padding(horizontal = Dimens.spaceMedium, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(Dimens.iconSizeSmall)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = sound.displayName,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * Tappable row showing a time-of-day. Opens a [TimePickerDialog] on click.
 */
@Composable
private fun TimeRow(
    icon: ImageVector,
    label: String,
    minuteOfDay: Int,
    onClick: () -> Unit
) {
    PreferenceRowFrame(
        icon = icon,
        title = label,
        subtitle = null,
        onClick = onClick,
        role = Role.Button,
        trailing = {
            Text(
                text = formatTime(minuteOfDay),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
    )
}

// ─── Dialogs ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialMinuteOfDay: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = initialMinuteOfDay / 60,
        initialMinute = initialMinuteOfDay % 60,
        is24Hour = false
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                TimePicker(state = state)
            }
        }
    )
}

/**
 * Sound picker dialog. Lists every [NotificationSound] with a leading
 * thumbnail, radio selection, description, and a preview button.
 *
 * Tapping a row selects + previews. The dedicated preview button on each
 * row re-auditions without changing the selection. "Save" commits.
 */
@Composable
private fun SoundPickerDialog(
    channelDisplayName: String,
    currentSound: NotificationSound,
    nowPlaying: NotificationSound?,
    onDismiss: () -> Unit,
    onPreview: (NotificationSound) -> Unit,
    onPick: (NotificationSound) -> Unit
) {
    var selected by remember(currentSound) { mutableStateOf(currentSound) }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            // Default AlertDialogs are capped at ~280 dp wide. We want a
            // roomier sheet so the option rows don't cramp the play /
            // preview affordance against the radio button.
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.spaceLarge),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = Dimens.spaceLarge,
                        end = Dimens.spaceLarge,
                        top = Dimens.spaceLarge,
                        bottom = Dimens.spaceMedium
                    )
            ) {
                // Header
                Text(
                    text = "Ringtones",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "for $channelDisplayName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(Dimens.spaceLarge))

                // Options
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    NotificationSound.entries.forEachIndexed { index, sound ->
                        SoundOption(
                            sound = sound,
                            selected = sound == selected,
                            isPlaying = sound == nowPlaying,
                            onSelect = {
                                selected = sound
                                onPreview(sound)
                            }
                        )
                        if (index < NotificationSound.entries.size - 1) {
                            Spacer(Modifier.height(2.dp))
                        }
                    }
                }

                Spacer(Modifier.height(Dimens.spaceMedium))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(Dimens.spaceSmall))
                    TextButton(onClick = { onPick(selected) }) {
                        Text("Save", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SoundOption(
    sound: NotificationSound,
    selected: Boolean,
    isPlaying: Boolean,
    onSelect: () -> Unit
) {
    val rowBg = if (selected)
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
    else
        Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(rowBg, MaterialTheme.shapes.medium)
            .clickable(role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Leading thumbnail — solid coloured tile so each option is
        // visually anchored next to its label. While the row is auditioning
        // we pulse the icon by overlaying the equalizer glyph in primary.
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(Dimens.cornerSmall))
                .background(
                    if (selected || isPlaying) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when {
                    sound == NotificationSound.Silent -> Icons.Outlined.DoNotDisturbOn
                    isPlaying -> Icons.Outlined.GraphicEq
                    else -> Icons.Outlined.MusicNote
                },
                contentDescription = null,
                tint = if (selected || isPlaying) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(Dimens.iconSizeMedium)
            )
        }
        Spacer(Modifier.width(Dimens.spaceLarge))
        Text(
            text = sound.displayName,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.weight(1f))
        RadioButton(
            selected = selected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────

private fun channelDisplayName(channelId: String): String = when (channelId) {
    NotificationHelper.CHANNEL_MESSAGES -> "Chat Messages"
    NotificationHelper.CHANNEL_SOCIAL -> "Friend Requests"
    NotificationHelper.CHANNEL_LOCATION_UPDATES -> "Location Updates"
    NotificationHelper.CHANNEL_GROUP_ACTIVITY -> "Group Activity"
    NotificationHelper.CHANNEL_MEETUP -> "Meetup"
    NotificationHelper.CHANNEL_GENERAL -> "General"
    else -> "Notifications"
}

/** Formats a 0..1439 minute value into "h:mm AM/PM". */
private fun formatTime(minuteOfDay: Int): String {
    val h24 = (minuteOfDay / 60).coerceIn(0, 23)
    val m = (minuteOfDay % 60).coerceIn(0, 59)
    val h12 = when {
        h24 == 0 -> 12
        h24 > 12 -> h24 - 12
        else -> h24
    }
    val period = if (h24 < 12) "AM" else "PM"
    return "%d:%02d %s".format(h12, m, period)
}

/** Formats a window into "10:00 PM – 7:00 AM". */
private fun formatWindow(startMin: Int, endMin: Int): String =
    "${formatTime(startMin)} \u2013 ${formatTime(endMin)}"
