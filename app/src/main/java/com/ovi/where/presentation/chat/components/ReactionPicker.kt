package com.ovi.where.presentation.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ovi.where.core.theme.Dimens
import com.ovi.where.core.utils.LocalReducedMotion

/**
 * The 6 emojis available in the reaction picker.
 * Requirements: 3.1
 */
val REACTION_EMOJIS = listOf("👍", "❤️", "😂", "😮", "😢", "🙏")

/**
 * Full-screen overlay that shows a reaction picker when a message is long-pressed.
 *
 * Messenger-style: a compact floating pill with 6 emojis at large display size, with a
 * subtle scrim behind it. Each emoji scales up on press for tactile feedback. The picker
 * itself enters with a 200ms scale-up + fade combo.
 *
 * Requirements: 3.1, 3.2, 23.6, 23.7
 */
@Composable
fun ReactionPickerOverlay(
    visible: Boolean,
    onEmojiSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val reducedMotion = LocalReducedMotion.current

    val enterTransition = if (reducedMotion) {
        fadeIn(animationSpec = snap())
    } else {
        fadeIn(tween(REACTION_PICKER_DURATION_MS)) +
                scaleIn(
                    animationSpec = tween(REACTION_PICKER_DURATION_MS),
                    initialScale = REACTION_PICKER_INITIAL_SCALE
                )
    }

    val exitTransition = if (reducedMotion) {
        fadeOut(animationSpec = snap())
    } else {
        fadeOut(tween(120)) + scaleOut(tween(150), targetScale = REACTION_PICKER_INITIAL_SCALE)
    }

    AnimatedVisibility(
        visible = visible,
        enter = enterTransition,
        exit = exitTransition
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 12.dp,
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
                        horizontal = 10.dp,
                        vertical = 8.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    REACTION_EMOJIS.forEach { emoji ->
                        ReactionEmojiButton(
                            emoji = emoji,
                            onClick = { onEmojiSelected(emoji) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Single emoji slot in the reaction picker. Scales up to 1.15x on press for a tactile,
 * Messenger-like feel. Reduced-motion users get a static button.
 */
@Composable
private fun ReactionEmojiButton(
    emoji: String,
    onClick: () -> Unit
) {
    val reducedMotion = LocalReducedMotion.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed && !reducedMotion) 1.15f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "emojiPressScale"
    )

    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .semantics { contentDescription = "React with $emoji" },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = emoji,
            fontSize = 30.sp,
            modifier = Modifier.scale(scale)
        )
    }
}

/** Reaction picker animation duration in ms (Requirement 23.6). */
private const val REACTION_PICKER_DURATION_MS = 200

/** Reaction picker initial scale (Requirement 23.6: 80% → 100%). */
private const val REACTION_PICKER_INITIAL_SCALE = 0.8f

/**
 * Displays aggregated reaction badges in a SINGLE Messenger-style pill that overlaps
 * the bubble corner.
 *
 * Behaviour:
 *   - All distinct emojis are stacked side-by-side inside one pill
 *   - The total reactor count is shown next to the emojis when there are 2+ reactors
 *   - Up to [MAX_VISIBLE_EMOJIS] distinct emojis are rendered inline; extras are summarised
 *     by the count alone
 *   - Pill has a thin surface ring + soft shadow so it reads cleanly against any bubble color
 *   - Tapping anywhere on the pill emits the most-reacted emoji (or the only emoji)
 *   - Long-pressing opens the "who reacted" details sheet
 *
 * Requirements: 3.7, 3.8
 */
@OptIn(ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ReactionBadges(
    reactions: Map<String, List<String>>,
    onReactionTap: (String) -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: () -> Unit = {}
) {
    if (reactions.isEmpty()) return

    val ringColor = MaterialTheme.colorScheme.surface
    val totalReactors = reactions.values.sumOf { it.size }

    // Sort emojis by reactor count desc so the most-popular reaction sits leftmost.
    val sortedEntries = reactions.entries.sortedByDescending { it.value.size }
    val visibleEmojis = sortedEntries.take(MAX_VISIBLE_EMOJIS).map { it.key }

    // Tapping the pill toggles the user's existing reaction (or the most-popular one).
    val tapEmoji = visibleEmojis.first()

    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shadowElevation = 1.dp,
        modifier = modifier
            .border(1.5.dp, ringColor, RoundedCornerShape(50))
            .clip(RoundedCornerShape(50))
            .combinedClickable(
                onClick = { onReactionTap(tapEmoji) },
                onLongClick = onLongPress
            )
            .semantics {
                contentDescription =
                    "$totalReactors ${if (totalReactors == 1) "reaction" else "reactions"}"
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            visibleEmojis.forEach { emoji ->
                Text(
                    text = emoji,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 1.dp)
                )
            }
            if (totalReactors >= 2) {
                Text(
                    text = totalReactors.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 2.dp)
                )
            }
        }
    }
}

private const val MAX_VISIBLE_EMOJIS = 3
