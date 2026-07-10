package com.neilturner.aerialviews.ui.helpers

import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.Metadata
import com.drew.metadata.exif.ExifDirectoryBase
import com.drew.metadata.exif.ExifIFD0Directory
import com.drew.metadata.exif.ExifSubIFDDirectory
import com.drew.metadata.exif.GpsDirectory
import io.ktor.utils.io.charsets.forName
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.Locale

private const val ORIENTATION_UNDEFINED = 0

data class ExifMetadata(
    val date: String? = null,
    val offset: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val description: String? = null,
    val orientation: Int = ORIENTATION_UNDEFINED,
)

object BitmapHelper {
    internal const val HEADER_BUFFER_SIZE = 512 * 1024 // 512KB - enough for EXIF and image header

    private const val TAG_OFFSET_TIME_ORIGINAL = 36880 // 0x9010
    private const val TAG_OFFSET_TIME = 36881 // 0x9011

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
                val metadata = ImageMetadataReader.readMetadata(stream)
                val exifDir = metadata.getFirstDirectoryOfType(ExifDirectoryBase::class.java)
                val subDir = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory::class.java)
                val gpsDir = metadata.getFirstDirectoryOfType(GpsDirectory::class.java)
                val description = extractExifDescription(metadata)
                ExifMetadata(
                    date = subDir?.getString(ExifDirectoryBase.TAG_DATETIME_ORIGINAL)
                        ?: metadata.getFirstDirectoryOfType(ExifIFD0Directory::class.java)?.getString(ExifDirectoryBase.TAG_DATETIME),
                    offset = subDir?.getString(TAG_OFFSET_TIME_ORIGINAL)
                        ?: subDir?.getString(TAG_OFFSET_TIME),
                    latitude = gpsDir?.geoLocation?.latitude,
                    longitude = gpsDir?.geoLocation?.longitude,
                    description = description,
                    orientation = exifDir?.getInteger(ExifDirectoryBase.TAG_ORIENTATION) ?: ORIENTATION_UNDEFINED,
                )
            } ?: ExifMetadata()
        } catch (_: Exception) {
            ExifMetadata()
        }

    private fun extractExifDescription(metadata: Metadata): String? {
        val imageDescription =
            decodeExifText(
                metadata = metadata,
                exifTag = ExifDirectoryBase.TAG_IMAGE_DESCRIPTION,
                hasUserCommentPrefix = false,
            )
        sanitizeExifDescription(imageDescription)?.let { return it }

        if (!PARSE_USER_COMMENT) return null

        val userComment =
            decodeExifText(
                metadata = metadata,
                exifTag = ExifDirectoryBase.TAG_USER_COMMENT,
                hasUserCommentPrefix = true,
            )
        return sanitizeExifDescription(userComment)
    }

    internal fun sanitizeExifDescription(description: String?): String? {
        val trimmed = description?.trim()?.trimEnd('\u0000') ?: return null
        if (trimmed.isBlank()) return null
        if (trimmed.length > MAX_HUMAN_DESCRIPTION_LENGTH && looksStructured(trimmed)) return null

        val lower = trimmed.lowercase(Locale.ROOT)
        if (VENDOR_METADATA_MARKERS.any { lower.contains(it) }) return null
        if (structuredFragmentCount(trimmed) >= MAX_STRUCTURED_FRAGMENT_COUNT) return null

        return trimmed
    }

    private fun decodeExifText(
        metadata: Metadata,
        exifTag: Int,
        hasUserCommentPrefix: Boolean,
    ): String? {
        val exifDir = metadata.getFirstDirectoryOfType(ExifDirectoryBase::class.java)
        val rawBytes = exifDir?.getByteArray(exifTag)
        val decoded =
            if (rawBytes != null) {
                if (hasUserCommentPrefix) decodeUserComment(rawBytes) else decodeBestEffort(rawBytes)
            } else {
                exifDir?.getString(exifTag)
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

    private fun looksStructured(value: String): Boolean =
        value.count { it == ';' || it == ',' } >= STRUCTURED_SEPARATOR_COUNT ||
            structuredFragmentCount(value) >= MAX_STRUCTURED_FRAGMENT_COUNT

    private fun structuredFragmentCount(value: String): Int =
        value
            .split(';', ',')
            .count { fragment ->
                val normalized = fragment.trim()
                normalized.indexOf(':') in 1 until normalized.lastIndex ||
                    normalized.indexOf('=') in 1 until normalized.lastIndex
            }

    private const val MAX_HUMAN_DESCRIPTION_LENGTH = 180
    private const val STRUCTURED_SEPARATOR_COUNT = 5
    private const val MAX_STRUCTURED_FRAGMENT_COUNT = 4
    private const val PARSE_USER_COMMENT = false

    private val VENDOR_METADATA_MARKERS =
        listOf(
            "sceneMode".lowercase(Locale.ROOT),
            "cct_value",
            "ai scene",
            "weatherInfo".lowercase(Locale.ROOT),
            "portrait-hw-remosaic",
            "aec_lux",
            "albedo",
            "filterIntensity".lowercase(Locale.ROOT),
        )
}
