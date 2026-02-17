package com.neilturner.aerialviews.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream

data class ExifMetadata(
    val date: String? = null,
    val offset: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val orientation: Int = ExifInterface.ORIENTATION_UNDEFINED,
)

data class BitmapResult(
    val imageBytes: ByteArray,
    val metadata: ExifMetadata,
)

object BitmapHelper {
    suspend fun loadResizedImageBytes(
        inputStream: InputStream,
        targetWidth: Int,
        targetHeight: Int,
        quality: Int = 90,
    ): BitmapResult? =
        withContext(Dispatchers.IO) {
            try {
                val sourceBytes = inputStream.use { it.readBytes() }
                if (sourceBytes.isEmpty()) {
                    return@withContext null
                }

                val metadata = extractMetadata(sourceBytes)

                val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size, boundsOptions)
                if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
                    return@withContext null
                }

                val decodeOptions =
                    BitmapFactory.Options().apply {
                        inSampleSize = calculateInSampleSize(boundsOptions, targetWidth, targetHeight)
                        inPreferredConfig = Bitmap.Config.RGB_565
                    }

                val decodedBitmap =
                    BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size, decodeOptions)
                        ?: return@withContext null

                val rotatedBitmap = applyExifTransform(decodedBitmap, metadata.orientation)

                val outputStream = ByteArrayOutputStream()
                val compressed = rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
                if (!compressed) {
                    if (rotatedBitmap != decodedBitmap) {
                        rotatedBitmap.recycle()
                    }
                    decodedBitmap.recycle()
                    return@withContext null
                }

                if (rotatedBitmap != decodedBitmap) {
                    rotatedBitmap.recycle()
                }
                decodedBitmap.recycle()

                BitmapResult(
                    imageBytes = outputStream.toByteArray(),
                    metadata = metadata,
                )
            } catch (_: Exception) {
                null
            }
        }

    private fun extractMetadata(sourceBytes: ByteArray): ExifMetadata {
        val exif = ExifInterface(ByteArrayInputStream(sourceBytes))
        return ExifMetadata(
            date = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: exif.getAttribute(ExifInterface.TAG_DATETIME),
            offset = exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL) ?: exif.getAttribute(ExifInterface.TAG_OFFSET_TIME),
            latitude = exif.latLong?.getOrNull(0),
            longitude = exif.latLong?.getOrNull(1),
            orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED),
        )
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int,
    ): Int {
        val width = options.outWidth
        val height = options.outHeight
        var sampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            var halfHeight = height / 2
            var halfWidth = width / 2

            while (halfHeight / sampleSize >= reqHeight && halfWidth / sampleSize >= reqWidth) {
                sampleSize *= 2
                halfHeight = height / 2
                halfWidth = width / 2
            }
        }

        return sampleSize.coerceAtLeast(1)
    }

    private fun applyExifTransform(
        bitmap: Bitmap,
        orientation: Int,
    ): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.setRotate(180f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return bitmap
        }

        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
