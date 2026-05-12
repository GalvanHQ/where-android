package com.ovi.where.presentation.people.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ovi.where.core.theme.Dimens

/**
 * Section header for friends list: "title · count" with optional accent dot.
 */
@Composable
fun FriendsSectionHeader(
    title: String,
    count: Int,
    accentColor: Color? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(
            horizontal = Dimens.spaceLarge,
            vertical = Dimens.spaceMedium
        ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        accentColor?.let { color ->
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(Modifier.width(Dimens.spaceMedium))
        }
        Text(
            text = "$title · $count",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
