package com.ovi.where.presentation.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ovi.where.core.theme.Dimens

/**
 * The 6 emojis available in the reaction picker.
 * Requirements: 3.1
 */
val REACTION_EMOJIS = listOf("👍", "❤️", "😂", "😮", "😢", "🙏")

/**
 * Full-screen overlay that shows a reaction picker when a message is long-pressed.
 *
 * - Displays 6 emojis in a horizontal row (Requirement 3.1)
 * - Dismisses on tap outside or back gesture (Requirement 3.2)
 * - Appears with scale + fade animation
 *
 * Requirements: 3.1, 3.2
 */
@Composable
fun ReactionPickerOverlay(
    visible: Boolean,
    onEmojiSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(150)) + scaleIn(tween(200), initialScale = 0.8f),
        exit = fadeOut(tween(100)) + scaleOut(tween(150), targetScale = 0.8f)
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.3f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(Dimens.cornerLarge),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // Consume click to prevent dismiss
                    )
                    .semantics { contentDescription = "Reaction picker" }
            ) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = Dimens.spaceLarge,
                        vertical = Dimens.spaceMedium
                    ),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spaceLarge)
                ) {
                    REACTION_EMOJIS.forEach { emoji ->
                        Text(
                            text = emoji,
                            fontSize = 28.sp,
                            modifier = Modifier
                                .clickable { onEmojiSelected(emoji) }
                                .padding(Dimens.spaceSmall)
                                .semantics { contentDescription = "React with $emoji" }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Displays aggregated reaction badges below a message bubble.
 *
 * - Each unique emoji is shown as a badge with the emoji and count
 * - Count is only displayed when >= 2 (Requirement 3.8)
 * - Same emoji from multiple users is aggregated into a single badge (Requirement 3.7)
 *
 * Requirements: 3.7, 3.8
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReactionBadges(
    reactions: Map<String, List<String>>,
    onReactionTap: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (reactions.isEmpty()) return

    FlowRow(
        modifier = modifier.padding(top = Dimens.spaceSmall),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSmall),
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceSmall)
    ) {
        reactions.forEach { (emoji, userIds) ->
            val count = userIds.size
            Surface(
                shape = RoundedCornerShape(Dimens.cornerMedium),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .clickable { onReactionTap(emoji) }
                    .semantics {
                        contentDescription = "$emoji reaction, $count ${if (count == 1) "person" else "people"}"
                    }
            ) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = Dimens.spaceMedium,
                        vertical = Dimens.spaceSmall
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spaceXSmall)
                ) {
                    Text(
                        text = emoji,
                        fontSize = 14.sp
                    )
                    if (count >= 2) {
                        Text(
                            text = count.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
