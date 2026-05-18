package com.ovi.where.presentation.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.ovi.where.domain.model.MessageStatus

/**
 * Premium image message bubble — full-bleed rounded image with minimal overlays.
 *
 * Design:
 * - Image fills the bubble with 12dp rounded corners
 * - No background color visible (image is the bubble)
 * - Upload progress: circular indicator centered over dimmed image
 * - Failed: retry icon centered over red-tinted image
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

    // Chat bubble should prefer thumbnail for smooth scrolling.
    // Full image should be loaded in FullScreenImageViewer.
    val displayImageUrl = thumbnailUrl ?: imageUrl

    val bubbleImageRequest = rememberCachedImageRequest(
        data = displayImageUrl,
        cacheKey = displayImageUrl ?: "empty_bubble_image",
        width = 480,
        height = 480
    )

    Box(
        modifier = modifier
            .widthIn(max = 240.dp)
            .aspectRatio(aspectRatio)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .semantics {
                contentDescription = when {
                    isUploading -> "Image uploading, ${uploadProgress ?: 0}% complete"
                    isFailed -> "Image upload failed, tap to retry"
                    else -> "Image message"
                }
            },
        contentAlignment = Alignment.Center
    ) {
        when {
            bubbleImageRequest != null && !isFailed -> {
                SubcomposeAsyncImage(
                    model = bubbleImageRequest,
                    contentDescription = "Image message",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    loading = {
                        ImageLoadingPlaceholder()
                    },
                    error = {
                        ImageEmptyPlaceholder()
                    }
                )
            }

            else -> {
                ImageEmptyPlaceholder()
            }
        }

        if (isUploading) {
            UploadProgressOverlay(uploadProgress = uploadProgress ?: 0)
        }

        if (isFailed) {
            FailedImageOverlay(onRetry = onRetry)
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
            .semantics {
                contentDescription =
                    "Image size limit exceeded, must be under 10 megabytes"
            }
    )
}

/**
 * Messenger-style image collage grid for 2-5 consecutive images.
 *
 * Layouts:
 * - 2 images: side by side
 * - 3 images: 1 large left + 2 stacked right
 * - 4 images: 2x2 grid
 * - 5 images: 2 top + 3 bottom
 */
@Composable
fun ImageCollageGrid(
    imageUrls: List<String>,
    onImageTap: (String) -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gap = 2.dp
    val cornerRadius = 12.dp

    Box(
        modifier = modifier
            .widthIn(max = 260.dp)
            .clip(RoundedCornerShape(cornerRadius))
    ) {
        when (imageUrls.size) {
            2 -> {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    imageUrls.forEach { url ->
                        CollageImage(
                            url = url,
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f),
                            onTap = { onImageTap(url) },
                            onLongPress = onLongPress
                        )
                    }
                }
            }

            3 -> {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    CollageImage(
                        url = imageUrls[0],
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(0.75f),
                        onTap = { onImageTap(imageUrls[0]) },
                        onLongPress = onLongPress
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        CollageImage(
                            url = imageUrls[1],
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            onTap = { onImageTap(imageUrls[1]) },
                            onLongPress = onLongPress
                        )

                        CollageImage(
                            url = imageUrls[2],
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            onTap = { onImageTap(imageUrls[2]) },
                            onLongPress = onLongPress
                        )
                    }
                }
            }

            4 -> {
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        CollageImage(
                            url = imageUrls[0],
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f),
                            onTap = { onImageTap(imageUrls[0]) },
                            onLongPress = onLongPress
                        )

                        CollageImage(
                            url = imageUrls[1],
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f),
                            onTap = { onImageTap(imageUrls[1]) },
                            onLongPress = onLongPress
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        CollageImage(
                            url = imageUrls[2],
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f),
                            onTap = { onImageTap(imageUrls[2]) },
                            onLongPress = onLongPress
                        )

                        CollageImage(
                            url = imageUrls[3],
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f),
                            onTap = { onImageTap(imageUrls[3]) },
                            onLongPress = onLongPress
                        )
                    }
                }
            }

            else -> {
                // 5 or more: show first 5 only.
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        CollageImage(
                            url = imageUrls[0],
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f),
                            onTap = { onImageTap(imageUrls[0]) },
                            onLongPress = onLongPress
                        )

                        CollageImage(
                            url = imageUrls[1],
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f),
                            onTap = { onImageTap(imageUrls[1]) },
                            onLongPress = onLongPress
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        imageUrls
                            .drop(2)
                            .take(3)
                            .forEach { url ->
                                CollageImage(
                                    url = url,
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f),
                                    onTap = { onImageTap(url) },
                                    onLongPress = onLongPress
                                )
                            }
                    }
                }
            }
        }
    }
}

@Composable
private fun CollageImage(
    url: String,
    modifier: Modifier,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    val imageRequest = rememberCachedImageRequest(
        data = url,
        cacheKey = url,
        width = 360,
        height = 360
    )

    SubcomposeAsyncImage(
        model = imageRequest,
        contentDescription = "Photo",
        contentScale = ContentScale.Crop,
        modifier = modifier
            .clip(RoundedCornerShape(2.dp))
            .pointerInput(url) {
                detectTapGestures(
                    onTap = { onTap() },
                    onLongPress = { onLongPress() }
                )
            },
        loading = {
            ImageEmptyPlaceholder()
        },
        error = {
            ImageEmptyPlaceholder()
        }
    )
}

@Composable
private fun rememberCachedImageRequest(
    data: String?,
    cacheKey: String,
    width: Int? = null,
    height: Int? = null
): ImageRequest? {
    val context = LocalContext.current

    return remember(data, cacheKey, width, height) {
        if (data.isNullOrBlank()) {
            null
        } else {
            ImageRequest.Builder(context)
                .data(data)
                .apply {
                    if (width != null && height != null) {
                        size(width, height)
                    }
                }
                .crossfade(true)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .networkCachePolicy(CachePolicy.ENABLED)
                .memoryCacheKey(cacheKey)
                .diskCacheKey(cacheKey)
                .build()
        }
    }
}

@Composable
private fun ImageLoadingPlaceholder() {
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

@Composable
private fun ImageEmptyPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    )
}

@Composable
private fun UploadProgressOverlay(uploadProgress: Int) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            progress = { uploadProgress / 100f },
            modifier = Modifier.size(40.dp),
            strokeWidth = 3.dp,
            color = Color.White,
            trackColor = Color.White.copy(alpha = 0.3f),
            strokeCap = StrokeCap.Round
        )
    }
}

@Composable
private fun FailedImageOverlay(
    onRetry: () -> Unit
) {
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
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
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

/** Default aspect ratio for image placeholders. */
private const val DEFAULT_ASPECT_RATIO = 4f / 3f