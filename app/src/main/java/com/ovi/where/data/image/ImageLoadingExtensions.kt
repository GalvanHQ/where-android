package com.ovi.where.data.image

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import coil.request.ImageRequest
import com.ovi.where.R

/**
 * Creates a Coil [ImageRequest] configured for thumbnail display in the message list.
 *
 * - Downscales to the specified dimensions with a maximum of 512px on the longest edge (Req 18.4)
 * - Uses error placeholder on failure (Req 18.6)
 * - No auto-retry on failure — retry happens on scroll out/in or pull-to-refresh (Req 18.6)
 *
 * @param url The image URL to load
 * @param widthPx The target width in pixels (layout dimension)
 * @param heightPx The target height in pixels (layout dimension)
 */
@Composable
fun buildThumbnailRequest(
    url: String?,
    widthPx: Int,
    heightPx: Int
): ImageRequest {
    val context = LocalContext.current
    val (targetWidth, targetHeight) = constrainToMaxSize(widthPx, heightPx)

    return ImageRequest.Builder(context)
        .data(url)
        .size(targetWidth, targetHeight)
        .error(R.drawable.ic_image_error_placeholder)
        .memoryCacheKey(url?.let { "${it}_${targetWidth}x${targetHeight}" })
        .build()
}

/**
 * Constrains dimensions so the longest edge does not exceed [ImageCacheManager.MAX_THUMBNAIL_SIZE_PX].
 *
 * Maintains aspect ratio while ensuring neither dimension exceeds the max.
 *
 * @param widthPx Original width in pixels
 * @param heightPx Original height in pixels
 * @return Pair of (constrainedWidth, constrainedHeight)
 */
fun constrainToMaxSize(widthPx: Int, heightPx: Int): Pair<Int, Int> {
    val maxSize = ImageCacheManager.MAX_THUMBNAIL_SIZE_PX

    if (widthPx <= 0 || heightPx <= 0) {
        return maxSize to maxSize
    }

    val longestEdge = maxOf(widthPx, heightPx)
    if (longestEdge <= maxSize) {
        return widthPx to heightPx
    }

    val scale = maxSize.toFloat() / longestEdge.toFloat()
    val constrainedWidth = (widthPx * scale).toInt().coerceAtLeast(1)
    val constrainedHeight = (heightPx * scale).toInt().coerceAtLeast(1)
    return constrainedWidth to constrainedHeight
}
