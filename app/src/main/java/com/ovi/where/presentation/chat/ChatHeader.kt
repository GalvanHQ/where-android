package com.ovi.where.presentation.chat

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.ovi.where.domain.model.SharedLocation
import com.ovi.where.presentation.chat.components.ConversationAvatar
import com.ovi.where.presentation.model.ConversationUiModel
import com.ovi.where.presentation.model.LastActiveFormatter
import kotlinx.coroutines.delay

/**
 * Messenger-style compact chat header for ChatScreen.
 *
 * Layout: back arrow → avatar (with online dot) → title column (name + status) → info icon.
 *
 * Subtitle is Messenger-style:
 *   - 1:1 online             → green dot + "Active now"
 *   - 1:1 offline + lastSeen → "Active 5m ago" (no dot)
 *   - 1:1 offline (unknown)  → "Offline"
 *   - Group, n online        → green dot + "{n} of {total} active"
 *   - Group, none online     → "{total} members"
 *
 * Subtitle text re-renders every minute so "Active 5m ago" ticks forward without a
 * full state push from the ViewModel.
 */
@Composable
fun ChatHeader(
    conversation: ConversationUiModel?,
    onNavigateBack: () -> Unit,
    onNavigateToGroupInfo: (String) -> Unit,
    onNavigateToConversationInfo: (String) -> Unit = {},
    onlineMemberCount: Int = 0,
    isOtherUserFriend: Boolean = true,
    groupDescription: String = "",
    sharingLocations: List<SharedLocation> = emptyList(),
    onSharingAvatarsTap: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(start = 4.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back arrow (24dp icon inside IconButton)
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (conversation != null) {
                // Tappable avatar + title area → navigates to info screen
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            if (conversation.isGroup) {
                                conversation.groupId?.let { onNavigateToGroupInfo(it) }
                            } else {
                                onNavigateToConversationInfo(conversation.id)
                            }
                        }
                        .semantics { contentDescription = "View ${conversation.title} info" },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Avatar (36dp) with 10dp online indicator
                    ConversationAvatar(
                        name = conversation.title,
                        photoUrl = conversation.photoUrl,
                        isOnline = !conversation.isGroup
                            && isOtherUserFriend
                            && conversation.isOtherUserOnline,
                        size = 36.dp,
                        indicatorSize = 10.dp,
                        indicatorBorderWidth = 1.5.dp
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Title column: title + subtitle
                    Column(
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Title: titleSmall + FontWeight.SemiBold
                        Text(
                            text = conversation.title,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        ActiveStatusSubtitle(
                            conversation = conversation,
                            onlineMemberCount = onlineMemberCount,
                            isOtherUserFriend = isOtherUserFriend,
                            groupDescription = groupDescription
                        )
                    }
                }

                // Trailing: sharing avatars (if any) + info
                val validSharers = sharingLocations.filter { it.latitude != 0.0 && it.longitude != 0.0 }
                if (validSharers.isNotEmpty()) {
                    SharingAvatarsRow(
                        sharers = validSharers,
                        onClick = onSharingAvatarsTap
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }

                IconButton(
                    onClick = {
                        if (conversation.isGroup) {
                            conversation.groupId?.let { onNavigateToGroupInfo(it) }
                        } else {
                            onNavigateToConversationInfo(conversation.id)
                        }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Conversation info",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // Fallback when conversation is null (loading state)
                Text(
                    text = "Chat",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Renders the active-status subtitle line (with the live green dot when applicable).
 *
 * The text is recomputed every 60s so "Active 5m ago" advances over time without
 * waiting for a state push. Skips re-rendering entirely when the user is online or
 * has no known last-seen timestamp.
 */
@Composable
private fun ActiveStatusSubtitle(
    conversation: ConversationUiModel,
    onlineMemberCount: Int,
    isOtherUserFriend: Boolean,
    groupDescription: String = ""
) {
    // Tick every minute so the relative-time subtitle stays current.
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(conversation.id) {
        while (true) {
            delay(60_000L)
            nowMs = System.currentTimeMillis()
        }
    }

    when {
        conversation.isGroup -> {
            // When someone is online we show the live "n of N active"
            // line so the green-dot signal isn't lost. Otherwise prefer
            // the group description — it's far more useful as a header
            // subtitle than a flat member count, which the user already
            // sees on the info screen and the group's avatar count.
            if (onlineMemberCount > 0) {
                StatusLine(
                    showDot = true,
                    text = "$onlineMemberCount of ${conversation.memberCount} active",
                    color = MaterialTheme.colorScheme.tertiary
                )
            } else if (groupDescription.isNotBlank()) {
                StatusLine(
                    showDot = false,
                    text = groupDescription,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        // 1:1 — only show presence info if the other user is a friend.
        !isOtherUserFriend -> Unit
        conversation.isOtherUserOnline -> {
            StatusLine(
                showDot = true,
                text = "Active now",
                color = MaterialTheme.colorScheme.tertiary
            )
        }
        else -> {
            val text = LastActiveFormatter.format(
                isOnline = false,
                lastSeen = conversation.otherUserLastSeen,
                now = nowMs
            )
            StatusLine(
                showDot = false,
                text = text,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatusLine(showDot: Boolean, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (showDot) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiary)
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Sender colour palette — mirrors the canonical
 *  [com.ovi.where.core.theme.AvatarColors] so the chat live-share row,
 *  map pins, and group avatars all draw from the same hue set. */
private val SharerColors = com.ovi.where.core.theme.AvatarColors

/**
 * Stacked, pulsing row of avatar chips for the people currently sharing
 * their live location in this conversation. Tapping it opens the live
 * meetup map sheet.
 *
 * Up to 3 avatars are shown overlapping; if there are more, a "+N" pill
 * is rendered at the end.
 */
@Composable
private fun SharingAvatarsRow(
    sharers: List<SharedLocation>,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val avatarPixelSize = remember(density) {
        with(density) { 24.dp.roundToPx() }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "sharers_pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sharers_pulse_scale"
    )

    val visible = sharers.take(3)
    val overflow = (sharers.size - visible.size).coerceAtLeast(0)

    Row(
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .semantics {
                contentDescription = "${sharers.size} sharing live location"
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        visible.forEachIndexed { index, loc ->
            val color = SharerColors[index % SharerColors.size]

            val avatarRequest = remember(loc.photoUrl, avatarPixelSize) {
                if (loc.photoUrl.isNullOrBlank()) {
                    null
                } else {
                    ImageRequest.Builder(context)
                        .data(loc.photoUrl)
                        .crossfade(true)
                        .size(avatarPixelSize)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .networkCachePolicy(CachePolicy.ENABLED)
                        .memoryCacheKey(loc.photoUrl)
                        .diskCacheKey(loc.photoUrl)
                        .build()
                }
            }

            Box(
                modifier = Modifier
                    .let { if (index > 0) it.offset(x = (-8).dp) else it }
                    .size(28.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .scale(pulse)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.25f))
                )

                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (avatarRequest != null) {
                        AsyncImage(
                            model = avatarRequest,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Text(
                            text = loc.displayName
                                .trim()
                                .take(1)
                                .uppercase()
                                .ifEmpty { "?" },
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            ),
                            color = Color.White
                        )
                    }
                }
            }
        }

        if (overflow > 0) {
            Box(
                modifier = Modifier
                    .offset(x = (-8).dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+$overflow",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    ),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}
