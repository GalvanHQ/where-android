package com.ovi.where.presentation.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.ovi.where.core.theme.Dimens

/**
 * Date separator pill displayed between message groups from different days.
 *
 * Shows a rounded chip with the date label ("Today", "Yesterday", or a formatted date)
 * centered horizontally with vertical margin.
 *
 * Requirement 10.3: Display date separator pills between message groups from different days.
 * Requirement 10.4: surfaceContainerHigh background, onSurfaceVariant text, labelSmall typography,
 * centered horizontally with 16dp vertical margin.
 */
@Composable
fun DateSeparator(
    label: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.spaceLarge),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 12.dp, vertical = Dimens.spaceSmall)
        )
    }
}
