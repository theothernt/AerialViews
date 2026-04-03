package com.neilturner.aerialviews.utils

import androidx.exifinterface.media.ExifInterface
import io.ktor.utils.io.charsets.forName
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

data class ExifMetadata(
    val date: String? = null,
    val offset: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val description: String? = null,
    val orientation: Int = ExifInterface.ORIENTATION_UNDEFINED,
)

object BitmapHelper {
    internal const val HEADER_BUFFER_SIZE = 512 * 1024 // 512KB - enough for EXIF and image header

    fun extractExifMetadataFromHeader(
        headerBytes: ByteArray,
        headerLength: Int,
    ): ExifMetadata =
        try {
            if (headerLength <= 0) return ExifMetadata()
            extractMetadata { ByteArrayInputStream(headerBytes, 0, headerLength) }
        } catch (ex: Exception) {
            Timber.e(ex, "BitmapHelper: Exception in extractExifMetadataFromHeader: ${ex.message}")
            ExifMetadata()
        }

    private fun extractMetadata(openInputStream: () -> InputStream?): ExifMetadata =
        try {
            openInputStream()?.use { stream ->
                val exif = ExifInterface(stream)
                val description = extractExifDescription(exif)
                ExifMetadata(
                    date = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: exif.getAttribute(ExifInterface.TAG_DATETIME),
                    offset = exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL) ?: exif.getAttribute(ExifInterface.TAG_OFFSET_TIME),
                    latitude = exif.latLong?.getOrNull(0),
                    longitude = exif.latLong?.getOrNull(1),
                    description = description,
                    orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED),
                )
            } ?: ExifMetadata()
        } catch (_: Exception) {
            ExifMetadata()
        }

    private fun extractExifDescription(exif: ExifInterface): String? {
        val imageDescription =
            decodeExifText(
                exif = exif,
                tag = ExifInterface.TAG_IMAGE_DESCRIPTION,
                hasUserCommentPrefix = false,
            )
        if (!imageDescription.isNullOrBlank()) return imageDescription

        val userComment =
            decodeExifText(
                exif = exif,
                tag = ExifInterface.TAG_USER_COMMENT,
                hasUserCommentPrefix = true,
            )
        return userComment
    }

    private fun decodeExifText(
        exif: ExifInterface,
        tag: String,
        hasUserCommentPrefix: Boolean,
    ): String? {
        val rawBytes = exif.getAttributeBytes(tag)
        val decoded =
            if (rawBytes != null) {
                if (hasUserCommentPrefix) decodeUserComment(rawBytes) else decodeBestEffort(rawBytes)
            } else {
                exif.getAttribute(tag)
            }
        return decoded?.trim()?.trimEnd('\u0000')?.takeIf { it.isNotBlank() }
    }

    private fun decodeUserComment(bytes: ByteArray): String? {
        if (bytes.size < 8) return decodeBestEffort(bytes)
        val prefix = String(bytes, 0, 8, Charsets.US_ASCII)
        val payload = bytes.copyOfRange(8, bytes.size)
        return when (prefix) {
            "ASCII\u0000\u0000\u0000" -> String(payload, Charsets.US_ASCII)
            "JIS\u0000\u0000\u0000\u0000\u0000" -> String(payload, Charsets.forName("Shift_JIS"))
            "UNICODE\u0000" -> String(payload, Charsets.UTF_16)
            "UNDEFINED" -> decodeBestEffort(payload)
            else -> decodeBestEffort(bytes)
        }
    }

    private fun decodeBestEffort(bytes: ByteArray): String {
        val utf8 = decodeUtf8IfValid(bytes)
        if (!utf8.isNullOrEmpty()) return utf8
        return String(bytes, Charsets.ISO_8859_1)
    }

    private fun decodeUtf8IfValid(bytes: ByteArray): String? =
        try {
            val decoder =
                Charsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
            decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            null
        }
}
