package com.neilturner.aerialviews.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.InputStream

data class ExifMetadata(
    val date: String? = null,
    val offset: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val description: String? = null,
    val orientation: Int = ExifInterface.ORIENTATION_UNDEFINED,
)

data class BitmapResult(
    val bitmap: Bitmap,
    val metadata: ExifMetadata,
)

object BitmapHelper {
    private const val HEADER_BUFFER_SIZE = 512 * 1024 // 512KB - enough for EXIF and image header

    suspend fun loadResizedImageBytes(
        openInputStream: () -> InputStream?,
        targetWidth: Int,
        targetHeight: Int,
    ): BitmapResult? =
        withContext(Dispatchers.IO) {
            val totalStartTime = System.currentTimeMillis()
            try {
                // Read header bytes once - used for EXIF and bounds checking
                val headerStartTime = System.currentTimeMillis()
                val headerBytes = ByteArray(HEADER_BUFFER_SIZE)
                val headerLength =
                    openInputStream()?.use { stream ->
                        stream.read(headerBytes)
                    } ?: return@withContext null
                Timber.d("BitmapHelper: Read header in ${System.currentTimeMillis() - headerStartTime}ms")

                val headerStream = { ByteArrayInputStream(headerBytes, 0, headerLength) }

                // Extract metadata from header
                val exifStartTime = System.currentTimeMillis()
                val metadata = extractMetadata(headerStream)
                Timber.d(
                    "BitmapHelper: Extracted EXIF in ${System.currentTimeMillis() - exifStartTime}ms (orientation=${metadata.orientation})",
                )

                val isSwapped =
                    metadata.orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
                        metadata.orientation == ExifInterface.ORIENTATION_ROTATE_270 ||
                        metadata.orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
                        metadata.orientation == ExifInterface.ORIENTATION_TRANSVERSE

                val effectiveTargetWidth = if (isSwapped) targetHeight else targetWidth
                val effectiveTargetHeight = if (isSwapped) targetWidth else targetHeight

                // Get bounds from header to calculate sample size
                val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(headerBytes, 0, headerBytes.size, boundsOptions)
                val hasValidBounds = boundsOptions.outWidth > 0 && boundsOptions.outHeight > 0

                if (!hasValidBounds) {
                    return@withContext null
                }

                val targetAspect = effectiveTargetWidth.toFloat() / effectiveTargetHeight.toFloat()
                val sourceAspect = boundsOptions.outWidth.toFloat() / boundsOptions.outHeight.toFloat()
                val decodeTargetWidth: Int
                val decodeTargetHeight: Int
                if (targetAspect > sourceAspect) {
                    decodeTargetHeight = effectiveTargetHeight
                    decodeTargetWidth = (effectiveTargetHeight * sourceAspect).toInt().coerceAtLeast(1)
                } else {
                    decodeTargetWidth = effectiveTargetWidth
                    decodeTargetHeight = (effectiveTargetWidth / sourceAspect).toInt().coerceAtLeast(1)
                }

                val decodeOptions =
                    BitmapFactory.Options().apply {
                        inSampleSize = calculateInSampleSize(boundsOptions, decodeTargetWidth, decodeTargetHeight)
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    decodeOptions.inPreferredConfig = Bitmap.Config.HARDWARE
                }

                // 2nd open: Full stream for actual bitmap decode
                val decodeStartTime = System.currentTimeMillis()
                val decodedBitmap =
                    openInputStream()?.use { stream ->
                        BitmapFactory.decodeStream(stream, null, decodeOptions)
                    } ?: return@withContext null
                Timber.d(
                    "BitmapHelper: Decoded bitmap in ${System.currentTimeMillis() - decodeStartTime}ms (sampleSize=${decodeOptions.inSampleSize})",
                )
                Timber.d(
                    "BitmapHelper: Decoded dimensions ${decodedBitmap.width}x${decodedBitmap.height}, target=${effectiveTargetWidth}x$effectiveTargetHeight, decodeTarget=${decodeTargetWidth}x$decodeTargetHeight",
                )

                val result =
                    BitmapResult(
                        bitmap = decodedBitmap,
                        metadata = metadata,
                    )
                Timber.d(
                    "BitmapHelper: Returning bitmap. Total loadResizedImageBytes time: ${System.currentTimeMillis() - totalStartTime}ms",
                )
                result
            } catch (ex: Exception) {
                Timber.e(ex, "BitmapHelper: Exception in loadResizedImageBytes: ${ex.message}")
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
                    description =
                        exif
                            .getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION)
                            ?.trim()
                            ?.takeIf { it.isNotBlank() }
                            ?: exif
                                .getAttribute(ExifInterface.TAG_USER_COMMENT)
                                ?.trim()
                                ?.takeIf { it.isNotBlank() },
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

        if (reqWidth <= 0 || reqHeight <= 0 || width <= 0 || height <= 0) {
            return 1
        }

        // Use a direct ratio so the decode lands closer to target dimensions.
        // This avoids expensive post-scale work when power-of-two sampling undershoots.
        val widthRatio = width / reqWidth
        val heightRatio = height / reqHeight
        return minOf(widthRatio, heightRatio).coerceAtLeast(1)
    }
}
