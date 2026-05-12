package com.ovi.where.presentation.people.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ovi.where.core.theme.Dimens

/**
 * A snackbar-style composable with a message and an "Undo" action button.
 */
@Composable
fun WhereUndoSnackbar(
    message: String,
    onUndo: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(Dimens.spaceLarge),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.inverseSurface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.spaceLarge, vertical = Dimens.spaceMedium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.inverseOnSurface
            )
            Spacer(Modifier.width(Dimens.spaceMedium))
            TextButton(onClick = onUndo) {
                Text(
                    text = "Undo",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.inversePrimary
                )
            }
        }
    }
}
