package com.ovi.where.presentation.map.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import com.ovi.where.core.theme.AvatarColors
import com.ovi.where.core.theme.Dimens
import com.ovi.where.presentation.map.GroupFilter
import com.ovi.where.presentation.map.PendingDestinationPick
import kotlin.math.absoluteValue

/**
 * Design tokens shared with the existing map-screen sheets (group filter,
 * map type, my-profile, share-target). Listed here so a single place
 * documents the agreement between this file and the rest of the screen.
 *
 *  • Sheet shape  : RoundedCornerShape(topStart = 38.dp, topEnd = 38.dp)
 *  • Primary CTA  : 52.dp height, RoundedCornerShape(16.dp), colorScheme.primary
 *  • Secondary CTA: 52.dp height, RoundedCornerShape(16.dp), surfaceContainerHigh
 *  • Inline pills : RoundedCornerShape(14.dp), surfaceContainerHigh
 *  • Selection    : primaryContainer container with primary-tinted check
 */

private val SheetShape = RoundedCornerShape(topStart = 38.dp, topEnd = 38.dp)
private val ButtonShape = RoundedCornerShape(16.dp)
private val CardShape = RoundedCornerShape(20.dp)
private val ChipShape = RoundedCornerShape(14.dp)

/**
 * "Set meetup point" bottom sheet — the dialog opened after the user picks a
 * spot via long-press or placement mode. Adopts the existing share-target
 * sheet's vocabulary so it reads as a sibling surface, not a one-off.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetMeetupDestinationSheet(
    pick: PendingDestinationPick,
    groups: List<GroupFilter>,
    preferredGroupId: String?,
    onConfirm: (groupId: String, name: String) -> Unit,
    onCreateGroup: () -> Unit,
    onJoinGroup: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val defaultName = remember(pick.address) {
        pick.address?.substringBefore(',')?.trim()?.takeIf { it.isNotBlank() } ?: "Meetup point"
    }
    var name by remember(pick.latitude, pick.longitude) { mutableStateOf(defaultName) }
    var selectedGroupId by remember(preferredGroupId, groups) {
        mutableStateOf(
            preferredGroupId
                ?: groups.firstOrNull()?.id
                ?: ""
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = SheetShape,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // ── Title row ────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.spaceLarge),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Set meetup point",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = pick.address?.takeIf { it.isNotBlank() }
                            ?: "Lat ${"%.5f".format(pick.latitude)}, Lng ${"%.5f".format(pick.longitude)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (pick.isResolvingAddress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(Dimens.spaceLarge))

            // ── Hero map preview ─────────────────────────────────────────
            HeroPickPreview(
                pick = pick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.spaceLarge)
                    .height(180.dp)
            )

            Spacer(Modifier.height(Dimens.spaceLarge))

            // ── Name field ───────────────────────────────────────────────
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(80) },
                singleLine = true,
                label = { Text("Name") },
                placeholder = { Text("Meetup point") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.spaceLarge),
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    cursorColor = MaterialTheme.colorScheme.primary
                )
            )

            Spacer(Modifier.height(Dimens.spaceLarge))

            // ── "Share with" section ─────────────────────────────────────
            ShareSectionLabel(text = "SHARE WITH")
            Spacer(Modifier.height(Dimens.spaceSmall))

            if (groups.isEmpty()) {
                NoGroupsEmptyState(
                    onCreateGroup = onCreateGroup,
                    onJoinGroup = onJoinGroup
                )
            } else {
                val pickerHeight = if (groups.size > 3) 240.dp else (groups.size * 76).dp
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 0.dp, max = pickerHeight)
                        .padding(horizontal = Dimens.spaceLarge),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(groups, key = { it.id }) { group ->
                        GroupPickerRow(
                            group = group,
                            selected = selectedGroupId == group.id,
                            onSelect = { selectedGroupId = group.id }
                        )
                    }
                }
            }

            Spacer(Modifier.height(Dimens.spaceLarge))

            // ── Sticky CTA bar — same vocabulary as ShareTargetSheet ─────
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = Dimens.spaceLarge, vertical = Dimens.spaceMedium),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMedium)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = ButtonShape
                    ) {
                        Text(
                            "Cancel",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Button(
                        onClick = {
                            if (selectedGroupId.isNotBlank()) {
                                onConfirm(selectedGroupId, name)
                            }
                        },
                        modifier = Modifier
                            .weight(2f)
                            .height(52.dp),
                        enabled = selectedGroupId.isNotBlank() && groups.isNotEmpty(),
                        shape = ButtonShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Filled.Flag, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Set meetup point",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShareSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(horizontal = Dimens.spaceLarge)
    )
}

@Composable
private fun HeroPickPreview(
    pick: PendingDestinationPick,
    modifier: Modifier = Modifier
) {
    val target = remember(pick.latitude, pick.longitude) {
        LatLng(pick.latitude, pick.longitude)
    }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(target, 16f)
    }
    Surface(
        modifier = modifier,
        shape = CardShape,
        tonalElevation = 1.dp,
        border = BorderStroke(
            width = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        GoogleMap(
            modifier = Modifier
                .fillMaxSize()
                .clip(CardShape),
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
                title = "Meetup point",
                zIndex = 4f
            ) {
                MeetupPin(size = 52.dp)
            }
        }
    }
}

/**
 * Group selection row — same chip vocabulary as `SelectableTargetRow` in
 * `ShareTargetSheet`. Selected state uses `primaryContainer`; idle uses
 * `surfaceContainerHigh`. Trailing primary-filled check on selected.
 */
@Composable
private fun GroupPickerRow(
    group: GroupFilter,
    selected: Boolean,
    onSelect: () -> Unit
) {
    val context = LocalContext.current
    Surface(
        onClick = onSelect,
        shape = ChipShape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                if (!group.photoUrl.isNullOrBlank()) {
                    val request = remember(group.photoUrl) {
                        ImageRequest.Builder(context)
                            .data(group.photoUrl)
                            .size(160)
                            .crossfade(true)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .memoryCacheKey(group.photoUrl)
                            .diskCacheKey(group.photoUrl)
                            .build()
                    }
                    AsyncImage(
                        model = request,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Group,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                StackedMemberAvatars(
                    photos = group.memberPhotos,
                    fallbackSeed = group.id,
                    onTintedSurface = selected
                )
            }
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else Color.Transparent
                    )
                    .border(
                        width = if (selected) 0.dp else 1.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Stacked overlapping member avatars used in the group picker rows.
 *
 * Each entry shows a Coil-cached profile photo when one is available; falls
 * back to a color circle (deterministic from the user's `id` if known, or
 * from the photo URL hash) when no photo URL is provided. We only render
 * the leading 3 entries to keep the strip compact.
 *
 * Coil cache keys derive directly from the photo URL so the same image
 * shared across surfaces (group info, conversation header, this picker)
 * loads from disk + memory after the first fetch.
 */
@Composable
private fun StackedMemberAvatars(
    photos: List<String?>,
    fallbackSeed: String,
    onTintedSurface: Boolean
) {
    val context = LocalContext.current
    val displayPhotos = remember(photos) { photos.take(MAX_PREVIEW_AVATARS) }
    val fallbackBaseHash = remember(fallbackSeed) {
        fallbackSeed.hashCode().absoluteValue
    }
    val borderColor = if (onTintedSurface) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceContainerHigh

    if (displayPhotos.isEmpty()) {
        // No member info loaded yet — render placeholder dots so the row
        // doesn't reflow when photos arrive a beat later.
        StackedFallbackDots(seed = fallbackSeed, borderColor = borderColor)
        return
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(
                width = (14 + (displayPhotos.size - 1) * 10).coerceAtLeast(14).dp,
                height = 18.dp
            )
        ) {
            displayPhotos.forEachIndexed { idx, url ->
                if (!url.isNullOrBlank()) {
                    val request = remember(url) {
                        ImageRequest.Builder(context)
                            .data(url)
                            .crossfade(true)
                            .size(64)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .networkCachePolicy(CachePolicy.ENABLED)
                            .memoryCacheKey(url)
                            .diskCacheKey(url)
                            .build()
                    }
                    AsyncImage(
                        model = request,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .offset(x = (idx * 10).dp)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .border(width = 1.5.dp, color = borderColor, shape = CircleShape)
                    )
                } else {
                    val color = AvatarColors[(fallbackBaseHash + idx) % AvatarColors.size]
                    Box(
                        modifier = Modifier
                            .offset(x = (idx * 10).dp)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(width = 1.5.dp, color = borderColor, shape = CircleShape)
                    )
                }
            }
        }
    }
}

/**
 * Three deterministic color dots used when a group's member photos haven't
 * been hydrated yet. Same render footprint as the photo variant so the row
 * doesn't reflow once real avatars arrive.
 */
@Composable
private fun StackedFallbackDots(seed: String, borderColor: Color) {
    val baseHash = remember(seed) { seed.hashCode().absoluteValue }
    val colors = remember(seed) {
        List(MAX_PREVIEW_AVATARS) { idx ->
            AvatarColors[(baseHash + idx) % AvatarColors.size]
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(width = 38.dp, height = 18.dp)) {
            colors.forEachIndexed { idx, color ->
                Box(
                    modifier = Modifier
                        .offset(x = (idx * 10).dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(width = 1.5.dp, color = borderColor, shape = CircleShape)
                )
            }
        }
    }
}

private const val MAX_PREVIEW_AVATARS = 3

/**
 * Compact placement action bar — single horizontal row.
 *
 * Pin icon + dynamic address label on the left, Cancel + Set here buttons
 * on the right. Sized to leave the map maximally visible (~72dp tall).
 */
@Composable
fun MeetupPlacementActionBar(
    address: String?,
    isResolving: Boolean,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Place,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "MEETUP POINT",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                if (isResolving && address.isNullOrBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(11.dp),
                            strokeWidth = 1.5.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Finding address",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text(
                        text = address?.takeIf { it.isNotBlank() } ?: "Drop pin anywhere",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            TextButton(
                onClick = onCancel,
                shape = ButtonShape
            ) {
                Text(
                    "Cancel",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.width(4.dp))

            Button(
                onClick = onConfirm,
                modifier = Modifier.height(44.dp),
                shape = ButtonShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)
            ) {
                Icon(Icons.Filled.Flag, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "Set here",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Map marker for the meetup destination — the canonical [MeetupPin].
 */
@Composable
fun DestinationPinMarker() {
    MeetupPin(size = 60.dp)
}

/**
 * Animated radar crosshair shown while the user is in placement mode.
 *
 * Three concentric primary pulse rings phased over time + the canonical
 * [MeetupPin] in the center. No bobbing animation — keeps the pin's
 * "ground point" fixed exactly where the user expects to drop it.
 */
@Composable
fun PlacementCrosshair(
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    Box(modifier = modifier.size(120.dp), contentAlignment = Alignment.Center) {
        RadarRing(color = primary, phase = 0f)
        RadarRing(color = primary, phase = 0.33f)
        RadarRing(color = primary, phase = 0.66f)
        MeetupPin(size = 48.dp)
    }
}

@Composable
private fun RadarRing(color: Color, phase: Float) {
    val transition = rememberInfiniteTransition(label = "radar-ring-$phase")
    val raw by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar-ring-progress-$phase"
    )
    val progress = ((raw + phase) % 1f)
    Canvas(modifier = Modifier.size(120.dp)) {
        val maxRadius = size.minDimension / 2f
        val radius = maxRadius * progress
        val alpha = (1f - progress).coerceIn(0f, 1f) * 0.45f
        drawCircle(
            color = color.copy(alpha = alpha),
            radius = radius,
            center = Offset(size.width / 2f, size.height / 2f),
            style = Stroke(width = 3f)
        )
        drawCircle(
            color = color.copy(alpha = alpha * 0.18f),
            radius = radius * 0.95f,
            center = Offset(size.width / 2f, size.height / 2f)
        )
    }
}


/**
 * Empty state shown inside [SetMeetupDestinationSheet] when the current user
 * has no groups. A meetup destination is group-scoped, so the user has two
 * paths forward: create a new group or join one with an invite code.
 *
 * Layout: small "Groups" eyebrow + headline + subline, followed by two CTAs.
 * Buttons reuse the same 52dp height / 16dp shape as the sticky CTA bar.
 */
@Composable
private fun NoGroupsEmptyState(
    onCreateGroup: () -> Unit,
    onJoinGroup: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spaceLarge)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = ChipShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 0.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Group,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "No groups yet",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Meetup points are shared with a group. Create or join one to continue.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Secondary — "Join group"
                    Button(
                        onClick = onJoinGroup,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = ButtonShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Text(
                            "Join group",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    // Primary — "Create group"
                    Button(
                        onClick = onCreateGroup,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = ButtonShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            "Create group",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
