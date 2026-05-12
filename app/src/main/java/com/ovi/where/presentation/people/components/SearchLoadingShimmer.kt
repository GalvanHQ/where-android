package com.ovi.where.presentation.people.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.ovi.where.core.theme.Dimens
import com.ovi.where.presentation.common.shimmerBrush

/**
 * Shimmer loading placeholder for search results: ≥4 shimmer rows.
 */
@Composable
fun SearchLoadingShimmer(modifier: Modifier = Modifier) {
    val brush = shimmerBrush()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Dimens.spaceLarge),
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceMedium)
    ) {
        repeat(5) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Dimens.spaceMedium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(Dimens.avatarSizeMedium)
                        .clip(CircleShape)
                        .background(brush)
                )
                Spacer(Modifier.width(Dimens.spaceLarge))
                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.55f)
                            .height(Dimens.shimmerBarHeightL)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(brush)
                    )
                    Spacer(Modifier.height(Dimens.spaceSmall))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.35f)
                            .height(Dimens.shimmerBarHeightS)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(brush)
                    )
                }
                Spacer(Modifier.width(Dimens.spaceMedium))
                Box(
                    modifier = Modifier
                        .width(Dimens.iconSizeXLarge)
                        .height(Dimens.shimmerBarHeightL)
                        .clip(MaterialTheme.shapes.small)
                        .background(brush)
                )
            }
        }
    }
}
