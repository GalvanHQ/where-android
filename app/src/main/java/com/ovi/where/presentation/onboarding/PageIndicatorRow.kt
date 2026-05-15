package com.ovi.where.presentation.onboarding

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.ovi.where.core.theme.Dimens
import com.ovi.where.core.utils.LocalReducedMotion

/**
 * Animated page indicator row for the onboarding flow.
 *
 * Renders one dot per page. The active dot animates to a 24dp-wide pill shape,
 * while idle dots remain as 8dp circles. Respects the system reduced motion setting.
 *
 * @param currentPage The zero-based index of the currently active page.
 * @param pageCount The total number of pages.
 * @param modifier Optional modifier for the row.
 */
@Composable
fun PageIndicatorRow(
    currentPage: Int,
    pageCount: Int,
    modifier: Modifier = Modifier
) {
    val reducedMotion = LocalReducedMotion.current
    val description = "Page ${currentPage + 1} of $pageCount"

    Row(
        modifier = modifier.semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMedium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            val isSelected = index == currentPage
            val targetWidth = if (isSelected) Dimens.indicatorActive else Dimens.indicatorIdle

            val animatedWidth by animateDpAsState(
                targetValue = targetWidth,
                animationSpec = if (reducedMotion) snap() else tween(durationMillis = 300),
                label = "indicator_width_$index"
            )

            Box(
                modifier = Modifier
                    .height(Dimens.indicatorIdle)
                    .width(animatedWidth)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant
                    )
            )
        }
    }
}
