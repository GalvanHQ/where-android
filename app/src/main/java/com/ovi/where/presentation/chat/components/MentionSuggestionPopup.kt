package com.ovi.where.presentation.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.ovi.where.presentation.chat.MentionEngine

/**
 * Mention suggestion popup displayed above the input bar when "@" is typed
 * in a group conversation.
 *
 * Shows a list of matching group members that can be selected to insert
 * a mention token into the input field.
 *
 * Requirements: 14.1, 14.2, 14.5, 14.6
 */
@Composable
fun MentionSuggestionPopup(
    suggestions: List<MentionEngine.MentionMember>,
    onMemberSelected: (MentionEngine.MentionMember) -> Unit,
    modifier: Modifier = Modifier
) {
    if (suggestions.isEmpty()) return

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 280.dp),
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
        shadowElevation = 4.dp,
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        LazyColumn {
            itemsIndexed(
                items = suggestions,
                key = { _, member -> member.userId }
            ) { index, member ->
                MentionSuggestionItem(
                    member = member,
                    onClick = { onMemberSelected(member) }
                )

                if (index < suggestions.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

/**
 * Individual member row in the mention suggestion popup.
 */
@Composable
private fun MentionSuggestionItem(
    member: MentionEngine.MentionMember,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val avatarPixelSize = remember(density) {
        with(density) { 36.dp.roundToPx() }
    }

    val avatarRequest = remember(member.photoUrl, avatarPixelSize) {
        if (member.photoUrl.isNullOrBlank()) {
            null
        } else {
            ImageRequest.Builder(context)
                .data(member.photoUrl)
                .crossfade(true)
                .size(avatarPixelSize)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .networkCachePolicy(CachePolicy.ENABLED)
                .memoryCacheKey(member.photoUrl)
                .diskCacheKey(member.photoUrl)
                .build()
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics {
                contentDescription = "Mention ${member.displayName}"
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (avatarRequest != null) {
            AsyncImage(
                model = avatarRequest,
                contentDescription = "${member.displayName} avatar",
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            MentionAvatarInitials(
                displayName = member.displayName
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = member.displayName,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MentionAvatarInitials(
    displayName: String
) {
    val initial = remember(displayName) {
        displayName
            .trim()
            .take(1)
            .uppercase()
            .ifEmpty { "?" }
    }

    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}