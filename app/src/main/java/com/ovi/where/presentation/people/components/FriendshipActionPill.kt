package com.ovi.where.presentation.people.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ovi.where.core.theme.Dimens
import com.ovi.where.presentation.model.FriendshipActionUiModel

/**
 * Pill-shaped button representing the friendship action state.
 * Three variants: ADD (filled primary), PENDING (outlined warning), FRIENDS (tonal tertiary).
 * Minimum touch target 48dp.
 */
@Composable
fun FriendshipActionPill(
    action: FriendshipActionUiModel,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (action) {
        FriendshipActionUiModel.ADD -> {
            Button(
                onClick = onTap,
                modifier = modifier.heightIn(min = Dimens.buttonHeightSmall),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.PersonAdd,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(Dimens.spaceSmall))
                Text("Add", style = MaterialTheme.typography.labelMedium)
            }
        }

        FriendshipActionUiModel.PENDING -> {
            OutlinedButton(
                onClick = onTap,
                modifier = modifier.heightIn(min = Dimens.buttonHeightSmall),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.tertiary
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(Dimens.spaceSmall))
                Text("Pending", style = MaterialTheme.typography.labelMedium)
            }
        }

        FriendshipActionUiModel.FRIENDS -> {
            FilledTonalButton(
                onClick = onTap,
                modifier = modifier.heightIn(min = Dimens.buttonHeightSmall),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(Dimens.spaceSmall))
                Text("Friends", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
