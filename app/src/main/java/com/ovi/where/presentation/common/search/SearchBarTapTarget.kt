package com.ovi.where.presentation.common.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.ovi.where.core.theme.Dimens

private val SEARCH_BAR_HEIGHT = 48.dp
private const val PLACEHOLDER_ALPHA = 0.6f

/**
 * Non-editable search bar tap target that visually matches WhereSearchBar
 * but navigates to the full-screen search screen when tapped.
 *
 * Renders as a pill-shaped surface with a search icon and placeholder text.
 */
@Composable
fun SearchBarTapTarget(
    placeholderText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(Dimens.cornerRound),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(SEARCH_BAR_HEIGHT)
                .padding(horizontal = Dimens.spaceLarge),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Dimens.iconSizeMedium)
            )

            Text(
                text = placeholderText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(start = Dimens.spaceMedium)
                    .alpha(PLACEHOLDER_ALPHA)
            )
        }
    }
}
