package com.ovi.where.core.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Utility for uploading profile pictures to Firebase Storage
 */
object ImageUploadUtil {
    
    private const val TAG = "ImageUpload"
    private const val PROFILE_PICTURES_PATH = "profile_pictures"
    private const val MAX_FILE_SIZE = 200 * 1024 // 200KB
    private const val MAX_IMAGE_SIZE = 512 // 512x512 pixels
    private const val JPEG_QUALITY_HIGH = 90
    private const val JPEG_QUALITY_MEDIUM = 75
    private const val JPEG_QUALITY_LOW = 60
    
    /**
     * Upload profile picture to Firebase Storage with compression
     * @param context Android context
     * @param imageUri Local image URI from gallery
     * @return Result with permanent download URL or error
     */
    suspend fun uploadProfilePicture(
        context: Context,
        imageUri: Uri
    ): Result<String> {
        return try {
            val userId = FirebaseAuth.getInstance().currentUser?.uid
            if (userId == null) {
                return Result.failure(Exception("User not authenticated"))
            }
            
            // Compress and resize the image
            val compressedImageUri = compressImage(context, imageUri)
            if (compressedImageUri == null) {
                return Result.failure(Exception("Failed to compress image"))
            }
            
            // Create a unique filename
            val imageId = UUID.randomUUID().toString()
            val fileName = "profile_${imageId}.jpg"
            
            // Get Firebase Storage reference
            val storage = FirebaseStorage.getInstance()
            val storageRef = storage.reference
            val profilePictureRef = storageRef.child("$PROFILE_PICTURES_PATH/$userId/$fileName")

            
            // Upload the compressed file
            val uploadTask = profilePictureRef.putFile(compressedImageUri).await()
            
            // Clean up temporary compressed file
            val compressedFile = File(compressedImageUri.path ?: "")
            if (compressedFile.exists()) {
                compressedFile.delete()
            }
            
            if (uploadTask.task.isSuccessful) {
                // Get the download URL
                val downloadUrl = profilePictureRef.downloadUrl.await()
                val downloadUrlString = downloadUrl.toString()

                Result.success(downloadUrlString)
            } else {
                val error = uploadTask.task.exception ?: Exception("Upload failed for unknown reason")
                Result.failure(error)
            }
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Delete previous profile picture from Firebase Storage
     * @param imageUrl The Firebase Storage URL to delete
     */
    suspend fun deleteProfilePicture(imageUrl: String): Result<Unit> {
        return try {
            if (!imageUrl.contains("firebasestorage.googleapis.com")) {
                // Not a Firebase Storage URL, skip deletion
                return Result.success(Unit)
            }
            
            val storage = FirebaseStorage.getInstance()
            val photoRef = storage.getReferenceFromUrl(imageUrl)
            
            photoRef.delete().await()
            Result.success(Unit)
            
        } catch (e: Exception) {
            // Don't fail the whole operation if deletion fails
            Result.success(Unit)
        }
    }
    
    /**
     * Compress and resize image to fit under MAX_FILE_SIZE
     * @param context Android context
     * @param imageUri Original image URI
     * @return Compressed image URI or null if failed
     */
    private fun compressImage(context: Context, imageUri: Uri): Uri? {
        return try {
            
            // Load the original bitmap
            val inputStream = context.contentResolver.openInputStream(imageUri)
            var bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            
            if (bitmap == null) {
                return null
            }

            
            // Handle image rotation based on EXIF data
            bitmap = correctImageOrientation(context, imageUri, bitmap)
            
            // Resize bitmap to maximum dimensions while maintaining aspect ratio
            bitmap = resizeBitmap(bitmap, MAX_IMAGE_SIZE)

            
            // Compress with progressive quality reduction
            val compressedFile = compressBitmapToFile(context, bitmap)

            return if (compressedFile != null && compressedFile.length() <= MAX_FILE_SIZE) {
                Uri.fromFile(compressedFile)
            } else {
                null
            }
            
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Correct image orientation based on EXIF data
     */
    private fun correctImageOrientation(context: Context, imageUri: Uri, bitmap: Bitmap): Bitmap {
        return try {
            val inputStream = context.contentResolver.openInputStream(imageUri)
            val exif = ExifInterface(inputStream!!)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_UNDEFINED
            )
            inputStream.close()
            
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            }
            
            if (!matrix.isIdentity) {
                val rotatedBitmap = Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                )
                bitmap.recycle()
                rotatedBitmap
            } else {
                bitmap
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read EXIF data, using original orientation", e)
            bitmap
        }
    }
    
    /**
     * Resize bitmap while maintaining aspect ratio
     */
    private fun resizeBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        // If already smaller than max size, return as is
        if (width <= maxSize && height <= maxSize) {
            return bitmap
        }
        
        val aspectRatio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int
        
        if (aspectRatio > 1) {
            // Landscape
            newWidth = maxSize
            newHeight = (maxSize / aspectRatio).toInt()
        } else {
            // Portrait or square
            newWidth = (maxSize * aspectRatio).toInt()
            newHeight = maxSize
        }
        
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        bitmap.recycle()
        return resizedBitmap
    }
    
    /**
     * Compress bitmap to file with progressive quality reduction
     */
    private fun compressBitmapToFile(context: Context, bitmap: Bitmap): File? {
        return try {
            val tempFile = File.createTempFile("compressed_profile_", ".jpg", context.cacheDir)
            
            val qualities = intArrayOf(JPEG_QUALITY_HIGH, JPEG_QUALITY_MEDIUM, JPEG_QUALITY_LOW, 50, 30, 20, 10)
            
            for (quality in qualities) {
                val outputStream = FileOutputStream(tempFile)
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
                outputStream.flush()
                outputStream.close()
                
                Log.d(TAG, "Compressed with quality $quality, size: ${tempFile.length()} bytes")
                
                if (tempFile.length() <= MAX_FILE_SIZE) {
                    Log.d(TAG, "Achieved target size with quality: $quality")
                    return tempFile
                }
            }
            
            // If still too large, try with ByteArrayOutputStream for more aggressive compression
            return compressWithByteArray(bitmap, tempFile)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error compressing bitmap to file", e)
            null
        }
    }
    
    /**
     * More aggressive compression using ByteArrayOutputStream
     */
    private fun compressWithByteArray(bitmap: Bitmap, targetFile: File): File? {
        return try {
            var quality = 10
            var compressedData: ByteArray
            
            do {
                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
                compressedData = outputStream.toByteArray()
                outputStream.close()
                
                Log.d(TAG, "ByteArray compression with quality $quality, size: ${compressedData.size} bytes")
                
                if (compressedData.size <= MAX_FILE_SIZE) {
                    // Write to file
                    val fileOutputStream = FileOutputStream(targetFile)
                    fileOutputStream.write(compressedData)
                    fileOutputStream.close()
                    
                    Log.d(TAG, "Final compression successful with quality: $quality")
                    return targetFile
                }
                
                quality -= 2
            } while (quality > 0)
            
            Log.w(TAG, "Could not compress image under ${MAX_FILE_SIZE} bytes even with minimum quality")
            null
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in aggressive compression", e)
            null
        }
    }
    
    /**
     * Check if a URI is a local content URI that needs uploading
     */
    fun isLocalUri(uri: String): Boolean {
        return uri.startsWith("content://") || uri.startsWith("file://")
    }
}
