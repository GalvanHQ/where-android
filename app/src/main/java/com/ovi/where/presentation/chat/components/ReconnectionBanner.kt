package com.ovi.where.presentation.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ovi.where.core.theme.Dimens

/**
 * Reconnection banner displayed at the top of the chat when the connection is lost.
 *
 * States:
 * - "Reconnecting..." with spinner: shown within 500ms of disconnect (Requirement 13.1)
 * - Error banner with manual "Retry" button: after 10 failed attempts (Requirement 13.3)
 * - Fade-out animation (300ms) on reconnect (Requirement 13.7)
 *
 * Requirements: 13.1, 13.3, 13.7
 */
@Composable
fun ReconnectionBanner(
    showBanner: Boolean,
    showManualRetry: Boolean,
    isFadingOut: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = showBanner,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(FADE_OUT_DURATION_MS))
    ) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            color = if (showManualRetry) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.tertiaryContainer
            },
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.spaceLarge, vertical = Dimens.spaceMedium),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (showManualRetry) {
                    // Error state: show error icon + "Retry" button (Requirement 13.3)
                    Icon(
                        imageVector = Icons.Default.WifiOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(Dimens.iconSizeSmall)
                    )
                    Spacer(Modifier.width(Dimens.spaceMedium))
                    Text(
                        text = "Connection failed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(Modifier.width(Dimens.spaceMedium))
                    TextButton(
                        onClick = onRetry,
                        modifier = Modifier.semantics {
                            contentDescription = "Retry connection"
                        }
                    ) {
                        Text(
                            text = "Retry",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                } else {
                    // Reconnecting state: show spinner + text (Requirement 13.1)
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Spacer(Modifier.width(Dimens.spaceMedium))
                    Text(
                        text = "Reconnecting\u2026",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.semantics {
                            contentDescription = "Reconnecting to server"
                        }
                    )
                }
            }
        }
    }
}

/**
 * Failed message indicator shown on messages with FAILED status.
 *
 * Displays:
 * - Red error indicator icon
 * - "Tap to retry" text
 *
 * Requirements: 1.3, 1.5
 */
@Composable
fun FailedMessageIndicator(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clickable(onClick = onRetry)
            .padding(vertical = Dimens.spaceSmall)
            .semantics { contentDescription = "Message failed to send, tap to retry" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSmall)
    ) {
        Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = "Tap to retry",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}

/**
 * Snackbar host for displaying "Message could not be sent" after 3 consecutive failures.
 *
 * The snackbar is shown for 4 seconds (Requirement 1.5).
 *
 * Requirements: 1.5
 */
@Composable
fun MessageFailureSnackbar(
    message: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            onDismiss()
        }
    }

    SnackbarHost(
        hostState = snackbarHostState,
        modifier = modifier,
        snackbar = { data ->
            Snackbar(
                modifier = Modifier.padding(Dimens.spaceLarge),
                containerColor = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                shape = RoundedCornerShape(Dimens.cornerSmall)
            ) {
                Text(
                    text = data.visuals.message,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    )
}

/** Duration of the fade-out animation on reconnect (Requirement 13.7). */
private const val FADE_OUT_DURATION_MS = 300
