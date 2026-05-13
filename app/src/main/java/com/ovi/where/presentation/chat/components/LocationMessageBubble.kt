package com.ovi.where.presentation.chat.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.ovi.where.core.theme.Dimens

/**
 * Location message bubble UI.
 *
 * Displays:
 * - Location icon (filled)
 * - "Shared a location" text
 * - Coordinate label formatted as "lat, lng" to 4 decimal places
 *
 * Requirement: 15.2
 */
@Composable
fun LocationMessageBubble(
    latitude: Double,
    longitude: Double,
    modifier: Modifier = Modifier
) {
    val coordinateLabel = "%.4f, %.4f".format(latitude, longitude)

    Row(
        modifier = modifier
            .padding(Dimens.spaceMedium)
            .semantics {
                contentDescription = "Location shared at $coordinateLabel"
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.LocationOn,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(Dimens.iconSizeMedium)
        )

        Spacer(Modifier.width(Dimens.spaceMedium))

        Column {
            Text(
                text = "Shared a location",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = coordinateLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
