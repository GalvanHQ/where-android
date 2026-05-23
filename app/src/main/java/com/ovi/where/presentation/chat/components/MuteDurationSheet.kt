package com.ovi.where.presentation.chat.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ovi.where.core.theme.Dimens
import com.ovi.where.domain.model.MuteOption

/**
 * Modal bottom sheet for picking a mute duration.
 *
 * Surfaces the [MuteOption] enum as a vertical list — the user taps a row,
 * the sheet collapses, and the caller's [onSelect] handler kicks off the
 * write. We keep this dumb on purpose: the sheet doesn't know whether
 * we're muting a chat from the conversation list or from the info screen,
 * which means the same component renders in both places without state
 * leakage.
 *
 * Visual language:
 *  • One row per option, large hit target, leading icon for affordance.
 *  • The label comes from [MuteOption.label] so wording stays consistent
 *    across the app — change once in the enum, propagates everywhere.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MuteDurationSheet(
    onDismiss: () -> Unit,
    onSelect: (MuteOption) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = Dimens.spaceLarge)
        ) {
            Text(
                text = "Mute notifications",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(
                    horizontal = Dimens.spaceLarge,
                    vertical = Dimens.spaceMedium
                )
            )
            Text(
                text = "You won't get notifications from this chat for the selected period. @mentions still come through.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    horizontal = Dimens.spaceLarge,
                    vertical = 4.dp
                )
            )

            Spacer(modifier = Modifier.height(Dimens.spaceMedium))

            MuteOption.values().forEach { option ->
                MuteRow(
                    option = option,
                    onClick = { onSelect(option) }
                )
            }
        }
    }
}

@Composable
private fun MuteRow(
    option: MuteOption,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.spaceLarge, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.NotificationsOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(Dimens.spaceLarge))
        Text(
            text = option.label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Lightweight extension that pairs [MuteDurationSheet] with a
 * Compose-side conditional, so call sites can write:
 *
 * ```
 * MuteDurationSheet.IfShowing(
 *     show = showSheet,
 *     onDismiss = { showSheet = false },
 *     onSelect = { option -> viewModel.muteFor(convId, option) }
 * )
 * ```
 *
 * — instead of repeating the if-else block at every site.
 */
object MuteDurationSheetHost {
    @Composable
    fun IfShowing(
        show: Boolean,
        onDismiss: () -> Unit,
        onSelect: (MuteOption) -> Unit
    ) {
        if (show) {
            MuteDurationSheet(onDismiss = onDismiss, onSelect = onSelect)
        }
    }
}
