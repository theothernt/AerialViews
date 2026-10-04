package com.neilturner.aerialviews.ui.helpers

import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.Directory
import com.drew.metadata.exif.ExifDirectoryBase
import com.drew.metadata.exif.ExifIFD0Directory
import com.drew.metadata.exif.ExifSubIFDDirectory
import com.drew.metadata.exif.GpsDirectory
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
                val ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory::class.java)
                val subIfd = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory::class.java)
                val geoLocation = metadata.getFirstDirectoryOfType(GpsDirectory::class.java)?.geoLocation

                ExifMetadata(
                    date = readTag(subIfd, ifd0, ExifDirectoryBase.TAG_DATETIME_ORIGINAL, ExifDirectoryBase.TAG_DATETIME),
                    offset = readTag(subIfd, ifd0, ExifDirectoryBase.TAG_TIME_ZONE_ORIGINAL, ExifDirectoryBase.TAG_TIME_ZONE),
                    latitude = geoLocation?.latitude?.takeUnless { isNullIsland(it, geoLocation.longitude) },
                    longitude = geoLocation?.longitude?.takeUnless { isNullIsland(geoLocation.latitude, it) },
                    description = extractExifDescription(ifd0, subIfd),
                    orientation = readOrientation(ifd0, subIfd),
                )
            } ?: ExifMetadata()
        } catch (_: Exception) {
            ExifMetadata()
        }

    // ExifInterface treated 0/0 as "no GPS fix". Keep that so photos tagged with a
    // default fix are not reverse geocoded to the Gulf of Guinea.
    private fun isNullIsland(
        latitude: Double,
        longitude: Double,
    ): Boolean = latitude == 0.0 && longitude == 0.0

    /** Reads [preferred] from the Exif SubIFD, falling back to [fallback] on IFD0 (or the reverse). */
    private fun readTag(
        preferred: Directory?,
        fallback: Directory?,
        preferredTag: Int,
        fallbackTag: Int = preferredTag,
    ): String? =
        presentDirectory(preferred, preferredTag)?.getString(preferredTag)
            ?: presentDirectory(fallback, fallbackTag)?.getString(fallbackTag)

    private fun presentDirectory(
        directory: Directory?,
        tag: Int,
    ): Directory? = directory?.takeIf { it.getObject(tag) != null }

    /**
     * Orientation is a SHORT, so metadata-extractor stores a number rather than a string and
     * [Directory.getString] returns null for it.
     */
    private fun readOrientation(
        ifd0: Directory?,
        subIfd: Directory?,
    ): Int {
        val tag = ExifDirectoryBase.TAG_ORIENTATION
        val directory = presentDirectory(ifd0, tag) ?: presentDirectory(subIfd, tag) ?: return ORIENTATION_UNDEFINED
        return when (val value = directory.getObject(tag)) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull() ?: ORIENTATION_UNDEFINED
            else -> ORIENTATION_UNDEFINED
        }
    }

    private fun extractExifDescription(
        ifd0: Directory?,
        subIfd: Directory?,
    ): String? = sanitizeExifDescription(readText(ifd0, subIfd, ExifDirectoryBase.TAG_IMAGE_DESCRIPTION))

    internal fun sanitizeExifDescription(description: String?): String? {
        val trimmed = description?.trim()?.trimEnd('\u0000') ?: return null
        if (trimmed.isBlank()) return null
        if (trimmed.length > MAX_HUMAN_DESCRIPTION_LENGTH && looksStructured(trimmed)) return null

        val lower = trimmed.lowercase(Locale.ROOT)
        if (VENDOR_METADATA_MARKERS.any { lower.contains(it) }) return null
        if (structuredFragmentCount(trimmed) >= MAX_STRUCTURED_FRAGMENT_COUNT) return null

        return trimmed
    }

    private fun readText(
        ifd0: Directory?,
        subIfd: Directory?,
        tag: Int,
    ): String? {
        val directory = presentDirectory(ifd0, tag) ?: presentDirectory(subIfd, tag) ?: return null
        // getString honours the EXIF charset markers; getByteArray + best-effort
        // decoding is the fallback for tags stored as raw bytes.
        return directory.getString(tag) ?: directory.getByteArray(tag)?.let(::decodeBestEffort)
    }

    private fun decodeBestEffort(bytes: ByteArray): String? {
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
