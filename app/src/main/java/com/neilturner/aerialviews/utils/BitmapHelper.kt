package com.neilturner.aerialviews.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
        openInputStream: () -> InputStream?,
        targetWidth: Int,
        targetHeight: Int,
        quality: Int = 85,
    ): BitmapResult? =
        withContext(Dispatchers.IO) {
            try {
                val metadata = extractMetadata(openInputStream)

                val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                val hasValidBounds =
                    openInputStream()?.use { stream ->
                        BitmapFactory.decodeStream(stream, null, boundsOptions)
                        boundsOptions.outWidth > 0 && boundsOptions.outHeight > 0
                    } ?: false

                if (!hasValidBounds) {
                    return@withContext null
                }

                val decodeOptions =
                    BitmapFactory.Options().apply {
                        inSampleSize = calculateInSampleSize(boundsOptions, targetWidth, targetHeight)
                        inPreferredConfig = Bitmap.Config.RGB_565
                    }

                val decodedBitmap =
                    openInputStream()?.use { stream ->
                        BitmapFactory.decodeStream(stream, null, decodeOptions)
                    } ?: return@withContext null

                val outputStream = ByteArrayOutputStream()
                val compressed = decodedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
                if (!compressed) {
                    decodedBitmap.recycle()
                    return@withContext null
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

    private fun extractMetadata(openInputStream: () -> InputStream?): ExifMetadata =
        try {
            openInputStream()?.use { stream ->
                val exif = ExifInterface(stream)
                ExifMetadata(
                    date = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: exif.getAttribute(ExifInterface.TAG_DATETIME),
                    offset = exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL) ?: exif.getAttribute(ExifInterface.TAG_OFFSET_TIME),
                    latitude = exif.latLong?.getOrNull(0),
                    longitude = exif.latLong?.getOrNull(1),
                    orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED),
                )
            } ?: ExifMetadata()
        } catch (_: Exception) {
            ExifMetadata()
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
            val halfHeight = height / 2
            val halfWidth = width / 2

            while (halfHeight / sampleSize >= reqHeight && halfWidth / sampleSize >= reqWidth) {
                sampleSize *= 2
            }
        }

        return sampleSize.coerceAtLeast(1)
    }
}
