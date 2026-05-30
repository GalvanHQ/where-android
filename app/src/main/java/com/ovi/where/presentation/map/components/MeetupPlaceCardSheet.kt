package com.ovi.where.presentation.map.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SignalWifiOff
import androidx.compose.material.icons.rounded.SocialDistance
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.ComposeMapColorScheme
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.ovi.where.R
import com.ovi.where.core.theme.Dimens
import com.ovi.where.domain.model.MeetupDestination
import com.ovi.where.domain.model.MeetupParticipantStatus

/**
 * Per-participant view model for the place-card list. Resolved by the
 * screen via the existing user cache + the live-share heartbeat to
 * compute "inactive" without persisting an extra field.
 */
data class MeetupParticipantUiModel(
    val userId: String,
    val displayName: String,
    val photoUrl: String?,
    val status: MeetupParticipantStatus,
    val isYou: Boolean,
    val isCreator: Boolean,
    /** True when this user has no recent location heartbeat (>5min stale). */
    val isInactive: Boolean,
    /** Pre-formatted distance label for ON_THE_WAY users. Null for terminal/inactive. */
    val distanceLabel: String?,
    /** Free-form note ("custom status") set by this participant. Empty when none. */
    val note: String = ""
)

/**
 * Place-card bottom sheet for the active meetup destination.
 *
 * Adds:
 *  • Participant list with status chips (on the way / arrived / can't make it / inactive)
 *  • "Get directions" handoff to Google Maps for everyone
 *  • "Can't make it" action for non-creators
 *  • "Clear" action restricted to the creator
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetupPlaceCardSheet(
    destination: MeetupDestination,
    groupName: String?,
    distanceText: String?,
    etaText: String?,
    participants: List<MeetupParticipantUiModel>,
    isCreator: Boolean,
    selfStatus: MeetupParticipantStatus,
    selfNote: String,
    onShowOnMap: () -> Unit,
    onGetDirections: () -> Unit,
    onCantMakeIt: () -> Unit,
    onClear: () -> Unit,
    onEditStatus: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 38.dp, topEnd = 38.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // ── Title row ────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.spaceLarge),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(Dimens.iconSizeXLarge)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Flag,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(Dimens.spaceMedium))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (!groupName.isNullOrBlank()) {
                            "MEETUP POINT FOR ${groupName.uppercase()}"
                        } else {
                            "MEETUP POINT"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = destination.name.ifBlank { "Meetup point" },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (destination.address.isNotBlank()) {
                        Text(
                            text = destination.address,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(Modifier.height(Dimens.spaceLarge))

            // ── Hero map preview ─────────────────────────────────────────
            HeroMap(
                destination = destination,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.spaceLarge)
                    .height(160.dp)
            )

            // ── Metric strip ─────────────────────────────────────────────
            if (distanceText != null || etaText != null) {
                Spacer(Modifier.height(Dimens.spaceMedium))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.spaceLarge),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMedium)
                ) {
                    if (distanceText != null) {
                        MetricCard(
                            icon = Icons.Rounded.SocialDistance,
                            label = "Distance",
                            value = distanceText,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (etaText != null) {
                        MetricCard(
                            icon = Icons.Rounded.Schedule,
                            label = "ETA",
                            value = etaText,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // ── Your-status composer (only while you can still update it) ─
            if (selfStatus == MeetupParticipantStatus.ON_THE_WAY) {
                Spacer(Modifier.height(Dimens.spaceLarge))
                YourStatusCard(
                    note = selfNote,
                    onClick = onEditStatus
                )
            }

            // ── Participants section ─────────────────────────────────────
            if (participants.isNotEmpty()) {
                Spacer(Modifier.height(Dimens.spaceLarge))
                ParticipantsSection(participants = participants)
            }

            Spacer(Modifier.height(Dimens.spaceLarge))

            // ── Primary action: Get directions (handoff to Google Maps) ─
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.spaceLarge),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMedium)
            ) {
                Button(
                    onClick = onShowOnMap,
                    modifier = Modifier
                        .weight(1f)
                        .height(Dimens.buttonHeight),
                    shape = RoundedCornerShape(Dimens.cornerMedium),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Icon(imageVector = ImageVector.vectorResource(id = R.drawable.map), null, Modifier.size(Dimens.iconSizeSmall))
                    Spacer(Modifier.width(Dimens.spaceMedium))
                    Text(
                        "Show on map",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Button(
                    onClick = onGetDirections,
                    modifier = Modifier
                        .weight(1f)
                        .height(Dimens.buttonHeight),
                    shape = RoundedCornerShape(Dimens.cornerMedium),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Rounded.DirectionsCar, null, Modifier.size(Dimens.iconSizeSmall))
                    Spacer(Modifier.width(Dimens.spaceMedium))
                    Text(
                        "Directions",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            Spacer(Modifier.height(Dimens.spaceMedium))

            // ── Role-aware secondary action ──────────────────────────────
            // Creator sees Clear (red). Non-creators see Can't make it,
            // unless they've already arrived or already opted out (in
            // which case the action is hidden — they can't undo here).
            val showSecondary = when {
                isCreator -> true
                selfStatus == MeetupParticipantStatus.ON_THE_WAY -> true
                else -> false
            }
            if (showSecondary) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.spaceLarge, vertical = Dimens.spaceSmall)
                ) {
                    Button(
                        onClick = if (isCreator) onClear else onCantMakeIt,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(Dimens.buttonHeightSmall),
                        shape = RoundedCornerShape(Dimens.cornerMedium),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            imageVector = if (isCreator) Icons.Rounded.Delete else Icons.Rounded.Block,
                            contentDescription = null,
                            modifier = Modifier.size(Dimens.iconSizeSmall)
                        )
                        Spacer(Modifier.width(Dimens.spaceMedium))
                        Text(
                            if (isCreator) "Clear meetup" else "I can't make it",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(Modifier.height(Dimens.spaceMedium))
        }
    }
}

/**
 * Vertical list of participant rows. Caps the visible height so the rest
 * of the sheet (metrics, actions) stays reachable; users can scroll the
 * list internally on long member lists.
 */
@Composable
private fun ParticipantsSection(participants: List<MeetupParticipantUiModel>) {
    Column {
        Text(
            text = "PARTICIPANTS · ${participants.size}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(horizontal = Dimens.spaceLarge + 4.dp)
        )
        Spacer(Modifier.height(Dimens.spaceMedium))
        // Cap the list height so it doesn't dominate the sheet on big groups.
        val listHeight = (participants.size.coerceAtMost(5) * 64).dp
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 0.dp, max = listHeight)
                .padding(horizontal = Dimens.spaceLarge),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceMedium)
        ) {
            items(participants, key = { it.userId }) { participant ->
                ParticipantRow(participant = participant)
            }
        }
    }
}

@Composable
private fun ParticipantRow(participant: MeetupParticipantUiModel) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(Dimens.cornerLarge),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Dimens.spaceMedium, vertical = Dimens.spaceMedium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar with subtle de-saturation when inactive
            Box(
                modifier = Modifier
                    .size(Dimens.iconSizeXLarge)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (!participant.photoUrl.isNullOrBlank()) {
                    val request = remember(participant.photoUrl) {
                        ImageRequest.Builder(context)
                            .data(participant.photoUrl)
                            .size(160)
                            .crossfade(true)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .memoryCacheKey(participant.photoUrl)
                            .diskCacheKey(participant.photoUrl)
                            .build()
                    }
                    AsyncImage(
                        model = request,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(Modifier.width(Dimens.spaceMedium))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (participant.isYou) "You" else participant.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (participant.isCreator) {
                        Spacer(Modifier.width(Dimens.spaceMedium))
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = "Host",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.tertiary,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = Dimens.spaceMedium, vertical = 2.dp)
                            )
                        }
                    }
                }
                ParticipantStatusLine(participant = participant)
            }
            ParticipantStatusBadge(participant = participant)
        }
    }
}

@Composable
private fun ParticipantStatusLine(participant: MeetupParticipantUiModel) {
    // If the participant has a custom note set, prefer that — it's their
    // own words. Otherwise fall back to the status-derived label.
    if (participant.note.isNotBlank()) {
        Text(
            text = "“${participant.note}”",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        return
    }
    val (label, color) = when {
        participant.isInactive && participant.status == MeetupParticipantStatus.ON_THE_WAY ->
            "Inactive — last seen recently" to MaterialTheme.colorScheme.onSurfaceVariant
        participant.status == MeetupParticipantStatus.ARRIVED ->
            "Arrived" to MaterialTheme.colorScheme.tertiary
        participant.status == MeetupParticipantStatus.CANT_MAKE_IT ->
            "Can't make it" to MaterialTheme.colorScheme.error
        participant.distanceLabel != null ->
            "${participant.distanceLabel} away" to MaterialTheme.colorScheme.onSurfaceVariant
        else ->
            "On the way" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun ParticipantStatusBadge(participant: MeetupParticipantUiModel) {
    val (icon, tint) = when {
        participant.status == MeetupParticipantStatus.ARRIVED ->
            Icons.Rounded.Check to MaterialTheme.colorScheme.tertiary
        participant.status == MeetupParticipantStatus.CANT_MAKE_IT ->
            Icons.Rounded.Block to MaterialTheme.colorScheme.error
        participant.isInactive ->
            Icons.Rounded.SignalWifiOff to MaterialTheme.colorScheme.onSurfaceVariant
        else -> return  // ON_THE_WAY + active → no badge, distance line carries the info
    }
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun HeroMap(
    destination: MeetupDestination,
    modifier: Modifier = Modifier
) {
    val target = remember(destination.latitude, destination.longitude) {
        LatLng(destination.latitude, destination.longitude)
    }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(target, 16f)
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(Dimens.cornerLarge),
        tonalElevation = 1.dp,
        border = BorderStroke(
            width = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        GoogleMap(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(Dimens.cornerLarge)),
            cameraPositionState = cameraPositionState,
            mapColorScheme = ComposeMapColorScheme.FOLLOW_SYSTEM,
            properties = MapProperties(mapType = MapType.NORMAL, isMyLocationEnabled = false),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                scrollGesturesEnabled = false,
                zoomGesturesEnabled = false,
                tiltGesturesEnabled = false,
                rotationGesturesEnabled = false,
                myLocationButtonEnabled = false,
                mapToolbarEnabled = false,
                compassEnabled = false
            )
        ) {
            val markerState = remember(target) { MarkerState(position = target) }
            MarkerComposable(
                state = markerState,
                title = destination.name.ifBlank { "Meetup point" },
                zIndex = 4f
            ) {
                MeetupPin(size = 50.dp)
            }
        }
    }
}

@Composable
private fun MetricCard(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(Dimens.cornerLarge),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Dimens.spaceMedium, vertical = Dimens.spaceMedium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.width(Dimens.spaceMedium))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/**
 * Compact "your status" card that lives between the metric strip and the
 * participants list when the user is still on the way. Shows their
 * current note (or a "Set custom status" prompt when empty) and opens
 * the editor sheet on tap.
 *
 * Idle: 56dp tall, surfaceContainerHigh background, primary leading
 * icon, chevron trailing — matches the "tap to edit" affordance used
 * elsewhere in the app.
 */
@Composable
private fun YourStatusCard(
    note: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spaceLarge)
            .clip(RoundedCornerShape(Dimens.cornerLarge))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(Dimens.cornerLarge),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Dimens.spaceMedium, vertical = Dimens.spaceMedium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(Dimens.spaceMedium))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "YOUR STATUS",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = if (note.isBlank()) "Tap to set a custom status" else note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (note.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (note.isBlank()) FontWeight.Normal else FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
