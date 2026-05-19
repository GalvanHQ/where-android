package com.ovi.where.presentation.chat.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.ShareLocation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ovi.where.presentation.chat.LocationBottomSheetState

/**
 * Location sharing bottom sheet — professional WhatsApp/Telegram-style UX.
 *
 * Two states:
 * 1. OPTIONS: "Share Current Location" + "Share Live Location" (if group)
 * 2. DURATION_PICKER: Duration chips (15m, 1h, 2h, 4h, 8h, Until I stop) + Confirm button
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationShareSheet(
    state: LocationBottomSheetState,
    showLiveLocationOption: Boolean,
    selectedDurationMinutes: Long,
    onShareCurrentLocation: () -> Unit,
    onShareLiveLocationSelected: () -> Unit,
    onDurationSelected: (Long) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    if (state == LocationBottomSheetState.HIDDEN) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        AnimatedContent(
            targetState = state,
            transitionSpec = {
                (slideInHorizontally { it / 2 } + fadeIn())
                    .togetherWith(slideOutHorizontally { -it / 2 } + fadeOut())
            },
            label = "location_sheet_content"
        ) { currentState ->
            when (currentState) {
                LocationBottomSheetState.OPTIONS -> OptionsContent(
                    showLiveLocationOption = showLiveLocationOption,
                    onShareCurrentLocation = onShareCurrentLocation,
                    onShareLiveLocation = onShareLiveLocationSelected,
                    onDismiss = onDismiss
                )
                LocationBottomSheetState.DURATION_PICKER -> DurationPickerContent(
                    selectedDuration = selectedDurationMinutes,
                    onDurationSelected = onDurationSelected,
                    onConfirm = onConfirm,
                    onDismiss = onDismiss
                )
                LocationBottomSheetState.HIDDEN -> { /* won't reach here */ }
            }
        }
    }
}

@Composable
private fun OptionsContent(
    showLiveLocationOption: Boolean,
    onShareCurrentLocation: () -> Unit,
    onShareLiveLocation: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Share Location",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Close, "Close", modifier = Modifier.size(20.dp))
            }
        }

        Spacer(Modifier.height(20.dp))

        // Option: Share Current Location
        LocationOptionRow(
            icon = Icons.Filled.MyLocation,
            iconColor = MaterialTheme.colorScheme.primary,
            title = "Share Current Location",
            subtitle = "Send your current position as a pin",
            onClick = onShareCurrentLocation
        )

        Spacer(Modifier.height(12.dp))

        // Option: Share Live Location (only for groups)
        if (showLiveLocationOption) {
            LocationOptionRow(
                icon = Icons.Filled.ShareLocation,
                iconColor = Color(0xFF4CAF50),
                title = "Share Live Location",
                subtitle = "Others can see your real-time movement",
                onClick = onShareLiveLocation
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun LocationOptionRow(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = title },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DurationPickerContent(
    selectedDuration: Long,
    onDurationSelected: (Long) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val durations = listOf(
        15L to "15 min",
        60L to "1 hour",
        120L to "2 hours",
        240L to "4 hours",
        480L to "8 hours",
        0L to "Until I stop"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Share for how long?",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Close, "Close", modifier = Modifier.size(20.dp))
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Your live location will be visible to everyone in this group",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(20.dp))

        // Duration chips in a flow layout
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            durations.take(3).forEach { (minutes, label) ->
                DurationChip(
                    label = label,
                    isSelected = selectedDuration == minutes,
                    onClick = { onDurationSelected(minutes) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            durations.drop(3).forEach { (minutes, label) ->
                DurationChip(
                    label = label,
                    isSelected = selectedDuration == minutes,
                    onClick = { onDurationSelected(minutes) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // Confirm button
        Button(
            onClick = onConfirm,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4CAF50)
            )
        ) {
            Icon(
                Icons.Filled.ShareLocation,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (selectedDuration == 0L) "Start Sharing" else "Share for ${durations.first { it.first == selectedDuration }.second}",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            )
        }

        Spacer(Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DurationChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                ),
                maxLines = 1
            )
        },
        modifier = modifier.height(38.dp),
        shape = RoundedCornerShape(10.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Color(0xFF4CAF50).copy(alpha = 0.15f),
            selectedLabelColor = Color(0xFF4CAF50)
        )
    )
}

/**
 * Persistent banner shown below the chat header while live location sharing is active.
 * Shows "Sharing live location · Xh Ym" with a Stop button and pulsing indicator.
 */
@Composable
fun LiveLocationSharingBanner(
    timeRemaining: String?,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "banner_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "banner_pulse_alpha"
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xFF4CAF50).copy(alpha = 0.08f),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Pulsing green dot + location icon
            Box(contentAlignment = Alignment.Center) {
                // Pulsing ring
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4CAF50).copy(alpha = pulseAlpha * 0.2f))
                )
                Icon(
                    imageVector = Icons.Filled.ShareLocation,
                    contentDescription = null,
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Sharing live location",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = Color(0xFF4CAF50)
                )
                if (timeRemaining != null) {
                    Text(
                        text = "$timeRemaining remaining",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "Until you stop",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Surface(
                onClick = onStop,
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFE53935).copy(alpha = 0.1f)
            ) {
                Text(
                    text = "Stop",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color(0xFFE53935)
                )
            }
        }
    }
}
