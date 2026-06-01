package com.ovi.where.core.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ImageCompressor"

/**
 * Utility class for compressing chat images before upload.
 *
 * - Compresses to max 1920px on the longest edge at 80% JPEG quality
 * - Accepts JPEG, PNG, WebP, HEIF source formats
 * - Rejects images exceeding 10MB before compression
 *
 * Requirements: 6.1, 6.3
 */
@Singleton
class ImageCompressor @Inject constructor() {

    companion object {
        /** Maximum file size before compression (10MB) */
        const val MAX_SOURCE_SIZE_BYTES = 10L * 1024 * 1024

        /** Maximum dimension on the longest edge after compression */
        const val MAX_LONGEST_EDGE_PX = 1920

        /** JPEG compression quality (80%) */
        const val JPEG_QUALITY = 80

        /** Accepted MIME types */
        val ACCEPTED_MIME_TYPES = setOf(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/heif",
            "image/heic"
        )
    }

    /**
     * Result of image compression.
     */
    sealed class CompressionResult {
        data class Success(val compressedFile: File, val width: Int, val height: Int) : CompressionResult()
        data class Error(val message: String) : CompressionResult()
    }

    /**
     * Compresses an image from the given URI.
     *
     * @param context Android context for content resolver access
     * @param imageUri URI of the source image
     * @return CompressionResult with compressed file or error
     */
    fun compress(context: Context, imageUri: Uri): CompressionResult {
        // Step 1: Validate MIME type (fallback to extension-based detection for file:// URIs)
        val mimeType = context.contentResolver.getType(imageUri)
            ?: guessMimeTypeFromUri(imageUri)
        if (mimeType == null || mimeType !in ACCEPTED_MIME_TYPES) {
            return CompressionResult.Error(
                "Unsupported image format. Accepted formats: JPEG, PNG, WebP, HEIF"
            )
        }

        // Step 2: Check file size before compression (reject > 10MB)
        val fileSize = getFileSize(context, imageUri)
        if (fileSize > MAX_SOURCE_SIZE_BYTES) {
            return CompressionResult.Error(
                "Image exceeds 10MB size limit. Please select a smaller image."
            )
        }

        // Step 3: Decode bitmap with inJustDecodeBounds to get dimensions first
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(imageUri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }

        if (options.outWidth <= 0 || options.outHeight <= 0) {
            return CompressionResult.Error("Failed to decode image dimensions")
        }

        // Step 4: Calculate sample size for memory efficiency
        val sampleSize = calculateSampleSize(options.outWidth, options.outHeight, MAX_LONGEST_EDGE_PX)

        // Step 5: Decode the bitmap with sample size
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        var bitmap: Bitmap? = context.contentResolver.openInputStream(imageUri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        }

        if (bitmap == null) {
            return CompressionResult.Error("Failed to decode image")
        }

        // Step 6: Correct orientation from EXIF data
        bitmap = correctOrientation(context, imageUri, bitmap)

        // Step 7: Resize to max 1920px on longest edge
        bitmap = resizeToMaxDimension(bitmap, MAX_LONGEST_EDGE_PX)

        val finalWidth = bitmap.width
        val finalHeight = bitmap.height

        // Step 8: Compress to JPEG at 80% quality
        return try {
            val compressedFile = File.createTempFile("chat_img_", ".jpg", context.cacheDir)
            FileOutputStream(compressedFile).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
                outputStream.flush()
            }
            bitmap.recycle()

            CompressionResult.Success(compressedFile, finalWidth, finalHeight)
        } catch (e: Exception) {
            bitmap.recycle()
            CompressionResult.Error("Failed to compress image: ${e.message}")
        }
    }

    /**
     * Gets the file size of the content at the given URI.
     */
    private fun getFileSize(context: Context, uri: Uri): Long {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.available().toLong()
            } ?: 0L
        } catch (e: Exception) {
            // Fallback: try using AssetFileDescriptor
            try {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { fd ->
                    fd.length
                } ?: 0L
            } catch (e2: Exception) {
                0L
            }
        }
    }

    /**
     * Calculates an appropriate sample size for BitmapFactory to avoid loading
     * a full-resolution image into memory when we only need MAX_LONGEST_EDGE_PX.
     */
    private fun calculateSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        val longestEdge = maxOf(width, height)
        var sampleSize = 1
        while (longestEdge / (sampleSize * 2) >= maxDimension) {
            sampleSize *= 2
        }
        return sampleSize
    }

    /**
     * Corrects image orientation based on EXIF data.
     */
    private fun correctOrientation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return bitmap
            val exif = ExifInterface(inputStream)
            inputStream.close()

            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_UNDEFINED
            )

            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                else -> return bitmap
            }

            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated != bitmap) bitmap.recycle()
            rotated
        } catch (_: Exception) {
            bitmap
        }
    }

    /**
     * Resizes a bitmap so that the longest edge does not exceed maxDimension.
     * Maintains aspect ratio.
     */
    private fun resizeToMaxDimension(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val longestEdge = maxOf(width, height)

        if (longestEdge <= maxDimension) {
            return bitmap
        }

        val scale = maxDimension.toFloat() / longestEdge.toFloat()
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()

        val resized = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        if (resized != bitmap) bitmap.recycle()
        return resized
    }

    /**
     * Guesses MIME type from URI path extension when contentResolver.getType() returns null.
     * This happens for file:// URIs on many devices.
     */
    private fun guessMimeTypeFromUri(uri: Uri): String? {
        val path = uri.path ?: uri.toString()
        return when {
            path.endsWith(".jpg", ignoreCase = true) || path.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
            path.endsWith(".png", ignoreCase = true) -> "image/png"
            path.endsWith(".webp", ignoreCase = true) -> "image/webp"
            path.endsWith(".heif", ignoreCase = true) || path.endsWith(".heic", ignoreCase = true) -> "image/heif"
            path.contains("gallery_") || path.contains("chat_img_") -> "image/jpeg"
            else -> null
        }
    }
}
