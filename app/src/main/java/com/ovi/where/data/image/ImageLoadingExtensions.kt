package com.ovi.where.data.image

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
