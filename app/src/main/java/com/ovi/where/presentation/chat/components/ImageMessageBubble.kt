package com.ovi.where.presentation.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.ovi.where.domain.model.MessageStatus

/**
 * Premium image message bubble — full-bleed rounded image with minimal overlays.
 *
 * Design:
 * - Image fills the bubble with 12dp rounded corners
 * - No background color visible (image is the bubble)
 * - Upload progress: circular indicator centered over dimmed image
 * - Failed: retry icon centered over red-tinted image
 * - Time stamp shown as a small pill overlay at bottom-right
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

    Box(
        modifier = modifier
            .widthIn(max = 240.dp)
            .aspectRatio(aspectRatio)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .semantics {
                contentDescription = when {
                    isUploading -> "Image uploading, ${uploadProgress}% complete"
                    isFailed -> "Image upload failed, tap to retry"
                    else -> "Image message"
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // The image itself
        when {
            imageUrl != null && !isFailed -> {
                SubcomposeAsyncImage(
                    model = imageUrl,
                    contentDescription = "Image message",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    loading = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
            }
            thumbnailUrl != null -> {
                SubcomposeAsyncImage(
                    model = thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    loading = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        )
                    }
                )
            }
            else -> {
                // Empty placeholder
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                )
            }
        }

        // Upload progress overlay
        if (isUploading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { (uploadProgress ?: 0) / 100f },
                    modifier = Modifier.size(40.dp),
                    strokeWidth = 3.dp,
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f),
                    strokeCap = StrokeCap.Round
                )
            }
        }

        // Failed overlay with retry
        if (isFailed) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
                    .clickable(onClick = onRetry),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.White,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Retry upload",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
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
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .semantics { contentDescription = "Image size limit exceeded, must be under 10 megabytes" }
    )
}

/** Default aspect ratio for image placeholders (4:3). */
private const val DEFAULT_ASPECT_RATIO = 4f / 3f
