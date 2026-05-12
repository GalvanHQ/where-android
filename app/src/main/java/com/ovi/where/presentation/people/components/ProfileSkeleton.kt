package com.ovi.where.presentation.people.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.ovi.where.core.theme.Dimens
import com.ovi.where.presentation.common.shimmerBrush

/**
 * Ghost avatar (96dp circle) + 2 shimmer text bars for profile loading state.
 */
@Composable
fun ProfileSkeleton(modifier: Modifier = Modifier) {
    val brush = shimmerBrush()

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Ghost avatar
        Box(
            modifier = Modifier
                .size(Dimens.avatarSizeXLarge)
                .clip(CircleShape)
                .background(brush)
        )

        Spacer(Modifier.height(Dimens.spaceLarge))

        // Name bar
        Box(
            modifier = Modifier
                .fillMaxWidth(0.4f)
                .height(Dimens.shimmerBarHeightL)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(brush)
        )

        Spacer(Modifier.height(Dimens.spaceMedium))

        // Username bar
        Box(
            modifier = Modifier
                .fillMaxWidth(0.3f)
                .height(Dimens.shimmerBarHeightS)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(brush)
        )
    }
}
