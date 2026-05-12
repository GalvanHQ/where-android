package com.ovi.where.presentation.people.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * A 10dp circle indicating online/offline status.
 * Includes a semantic label so color is never the only indicator.
 */
@Composable
fun StatusDot(
    isOnline: Boolean,
    modifier: Modifier = Modifier
) {
    val color = if (isOnline) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.outline
    }
    val label = if (isOnline) "Online" else "Offline"

    Box(
        modifier = modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color)
            .semantics { contentDescription = label }
    )
}
