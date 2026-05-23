package com.ovi.where.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import com.ovi.where.presentation.common.WhereTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ovi.where.core.theme.Dimens

/**
 * Notification Preferences screen.
 *
 * Two sections:
 *  • **Categories** — per-channel toggles for the message types the app
 *    can send (chat, friend requests, location updates, group activity,
 *    meetup).
 *  • **Quiet Hours** — a daily window during which non-essential pushes
 *    are silenced or fully suppressed. Close-friends + meetup arrival
 *    pushes bypass the window automatically (see NotificationHelper).
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

            Text(
                text = "Choose which notifications you'd like to receive.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(Dimens.spaceXLarge))

            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 1.dp
            ) {
                Column {
                    NotificationToggleRow(
                        icon = Icons.Outlined.PersonAdd,
                        title = "Friend Requests",
                        subtitle = "New friend requests and acceptances",
                        checked = uiState.friendRequestsEnabled,
                        onCheckedChange = viewModel::setFriendRequestsEnabled
                    )

                    NotificationToggleRow(
                        icon = Icons.Outlined.LocationOn,
                        title = "Location Updates",
                        subtitle = "Friends sharing their location",
                        checked = uiState.locationUpdatesEnabled,
                        onCheckedChange = viewModel::setLocationUpdatesEnabled
                    )

                    NotificationToggleRow(
                        icon = Icons.Outlined.Group,
                        title = "Group Activity",
                        subtitle = "Members joining or leaving groups",
                        checked = uiState.groupActivityEnabled,
                        onCheckedChange = viewModel::setGroupActivityEnabled
                    )

                    NotificationToggleRow(
                        icon = Icons.Outlined.Chat,
                        title = "Chat Messages",
                        subtitle = "New messages in conversations",
                        checked = uiState.chatMessagesEnabled,
                        onCheckedChange = viewModel::setChatMessagesEnabled
                    )

                    NotificationToggleRow(
                        icon = Icons.Outlined.Place,
                        title = "Meetup",
                        subtitle = "Destination changes and arrival pings",
                        checked = uiState.meetupEnabled,
                        onCheckedChange = viewModel::setMeetupEnabled
                    )
                }
            }

            Spacer(modifier = Modifier.height(Dimens.spaceXLarge))

            // ── Quiet Hours ─────────────────────────────────────────────
            Text(
                text = "Quiet Hours",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(Dimens.spaceSmall))

            Text(
                text = "Silence non-essential pushes during a daily window. Close friends and meetup arrivals always come through.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(Dimens.spaceMedium))

            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 1.dp
            ) {
                Column {
                    NotificationToggleRow(
                        icon = Icons.Outlined.Bedtime,
                        title = "Enable Quiet Hours",
                        subtitle = formatWindow(quiet.startMinuteOfDay, quiet.endMinuteOfDay),
                        checked = quiet.enabled,
                        onCheckedChange = viewModel::setQuietHoursEnabled
                    )

                    if (quiet.enabled) {
                        TimeRow(
                            label = "Start",
                            minuteOfDay = quiet.startMinuteOfDay,
                            onClick = { showStartPicker = true }
                        )
                        TimeRow(
                            label = "End",
                            minuteOfDay = quiet.endMinuteOfDay,
                            onClick = { showEndPicker = true }
                        )
                        NotificationToggleRow(
                            icon = Icons.Outlined.Bedtime,
                            title = "Block instead of silence",
                            subtitle = "Drop non-essential notifications entirely instead of posting them silently",
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
}

@Composable
private fun NotificationToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spaceLarge, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(Dimens.spaceLarge))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(Dimens.spaceMedium))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

/**
 * Tappable row showing a time-of-day. Opens a [TimePickerDialog] on click.
 */
@Composable
private fun TimeRow(
    label: String,
    minuteOfDay: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.spaceLarge, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = formatTime(minuteOfDay),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium
        )
    }
}

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
