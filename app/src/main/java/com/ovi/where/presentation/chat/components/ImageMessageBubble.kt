package com.ovi.where.presentation.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.ovi.where.core.theme.Dimens
import com.ovi.where.domain.model.MessageStatus

/**
 * Image message bubble with upload progress, error overlay, and placeholder.
 *
 * Features:
 * - Determinate progress bar overlay (0-100%) during upload (Requirement 6.2)
 * - Error overlay with retry icon on failure (Requirement 6.3)
 * - Solid placeholder matching aspect ratio (4:3 default) while loading (Requirement 6.4)
 * - Rounded corners matching the bubble style
 *
 * Requirements: 6.2, 6.3, 6.4, 6.6, 6.7
 */
@Composable
fun ImageMessageBubble(
    imageUrl: String?,
    thumbnailUrl: String?,
    uploadProgress: Int?,
    status: MessageStatus,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    aspectRatio: Float = DEFAULT_ASPECT_RATIO
) {
    val isUploading = uploadProgress != null && status == MessageStatus.PENDING
    val isFailed = status == MessageStatus.FAILED

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = when {
                    isUploading -> "Image uploading, ${uploadProgress}% complete"
                    isFailed -> "Image upload failed, tap to retry"
                    else -> "Image message"
                }
            },
        shape = RoundedCornerShape(Dimens.cornerSmall),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Box(
            modifier = Modifier.aspectRatio(aspectRatio),
            contentAlignment = Alignment.Center
        ) {
            when {
                // Uploading: show thumbnail/placeholder with progress overlay
                isUploading -> {
                    // Placeholder or thumbnail background
                    ImagePlaceholderOrThumbnail(
                        thumbnailUrl = thumbnailUrl,
                        aspectRatio = aspectRatio
                    )

                    // Determinate progress bar overlay (Requirement 6.2)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        LinearProgressIndicator(
                            progress = { (uploadProgress ?: 0) / 100f },
                            modifier = Modifier
                                .fillMaxWidth(0.7f)
                                .padding(horizontal = Dimens.spaceLarge),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f),
                            strokeCap = StrokeCap.Round
                        )
                    }
                }

                // Failed: show error overlay with retry icon (Requirement 6.3)
                isFailed -> {
                    ImagePlaceholderOrThumbnail(
                        thumbnailUrl = thumbnailUrl,
                        aspectRatio = aspectRatio
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f))
                            .clickable(onClick = onRetry),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Retry upload",
                                    tint = MaterialTheme.colorScheme.onError,
                                    modifier = Modifier.size(Dimens.iconSizeMedium)
                                )
                            }
                        }
                    }
                }

                // Loaded: show the actual image
                imageUrl != null -> {
                    SubcomposeAsyncImage(
                        model = imageUrl,
                        contentDescription = "Image message",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(Dimens.cornerSmall)),
                        loading = {
                            // Solid placeholder while loading received images (Requirement 6.4)
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            )
                        }
                    )
                }

                // No image URL yet: show placeholder
                else -> {
                    ImagePlaceholderOrThumbnail(
                        thumbnailUrl = thumbnailUrl,
                        aspectRatio = aspectRatio
                    )
                }
            }
        }
    }
}

/**
 * Solid placeholder or thumbnail preview for image messages.
 * Uses 4:3 default aspect ratio (Requirement 6.4).
 */
@Composable
private fun ImagePlaceholderOrThumbnail(
    thumbnailUrl: String?,
    aspectRatio: Float
) {
    if (thumbnailUrl != null) {
        AsyncImage(
            model = thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(Dimens.cornerSmall))
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        )
    }
}

/**
 * Inline error text shown below the image picker when a file exceeds 10MB.
 *
 * Requirement 6.7: Size limit error inline below image picker for >10MB.
 */
@Composable
fun ImageSizeLimitError(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    if (!visible) return

    Text(
        text = "Image must be under 10 MB",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = modifier
            .padding(horizontal = Dimens.spaceLarge, vertical = Dimens.spaceSmall)
            .semantics { contentDescription = "Image size limit exceeded, must be under 10 megabytes" }
    )
}

/** Default aspect ratio for image placeholders (4:3). */
private const val DEFAULT_ASPECT_RATIO = 4f / 3f
