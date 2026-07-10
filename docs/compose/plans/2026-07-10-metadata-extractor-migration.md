# Metadata Extractor Migration Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use compose:subagent (recommended) or compose:execute to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace AndroidX ExifInterface with com.drewnoakes:metadata-extractor:2.19.0 for broader metadata format support while keeping the same ExifMetadata output shape.

**Architecture:** Single-file swap in BitmapHelper.kt + dependency update in libs.versions.toml. The ExifMetadata data class stays unchanged. All callers (ImagePlayerView, tests) continue working without modification.

**Tech Stack:** Kotlin, Gradle (libs.versions.toml), com.drewnoakes:metadata-extractor:2.19.0

## Global Constraints

- Keep the same `ExifMetadata` data class fields: date, offset, latitude, longitude, description, orientation
- Keep `HEADER_BUFFER_SIZE = 512 * 1024`
- Keep all `sanitizeExifDescription` logic unchanged
- Existing tests must continue passing without modification
- The `getAttributeBytes`-based text decoding (UserComment with ASCII/UNICODE prefix detection) must be replicated

---

### Task 1: Update dependencies in libs.versions.toml

**Covers:** Dependency swap

**Files:**
- Modify: `gradle/libs.versions.toml`

**Interfaces:**
- Produces: `libs.metadata.extractor` available for build.gradle.kts

- [ ] **Step 1: Add metadata-extractor version**

In `gradle/libs.versions.toml`, add to the `[versions]` section (after `exifinterface` line):

```toml
metadata-extractor = "2.19.0"
```

- [ ] **Step 2: Add metadata-extractor library entry**

In `gradle/libs.versions.toml`, add to the `[libraries]` section (after the `exifinterface` entry):

```toml
metadata-extractor = { group = "com.drewnoakes", name = "metadata-extractor", version.ref = "metadata-extractor" }
```

- [ ] **Step 3: Remove exifinterface from libraries and version**

Remove the `exifinterface` version line and library entry:

```toml
# DELETE this from [versions]:
exifinterface = "1.4.2"

# DELETE this from [libraries]:
exifinterface = { group = "androidx.exifinterface", name = "exifinterface", version.ref = "exifinterface" }
```

- [ ] **Step 4: Update the androidx bundle**

Replace `exifinterface` with `metadata-extractor` in the `androidx` bundle:

```toml
# Before:
androidx = ["core-ktx", "leanback", "leanback-preference", "preference-ktx", "activity-ktx", "constraintlayout", "appcompat", "exifinterface"]

# After:
androidx = ["core-ktx", "leanback", "leanback-preference", "preference-ktx", "activity-ktx", "constraintlayout", "appcompat", "metadata-extractor"]
```

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml
git commit -m "chore: replace exifinterface with metadata-extractor in dependency catalog"
```

---

### Task 2: Rewrite BitmapHelper.kt to use metadata-extractor

**Covers:** Core migration

**Files:**
- Modify: `app/src/main/java/com/neilturner/aerialviews/ui/helpers/BitmapHelper.kt`

**Interfaces:**
- Consumes: `com.drewnoakes.metadata.exif.ExifDirectory`, `com.drewnoakes.metadata.exif.GpsDirectory`, `com.drewnoakes.metadata.exif.ExifIFD0Directory`, `com.drewnoakes.metadata.Metadata`, `com.drewnoakes.metadata.exif.ExifReader`
- Produces: Same `ExifMetadata` data class, same `extractExifMetadataFromHeader` and `sanitizeExifDescription` public API

- [ ] **Step 1: Replace imports and constants**

Replace the file contents. The key API mapping:

| AndroidX ExifInterface | metadata-extractor |
|------------------------|-------------------|
| `ExifInterface(stream)` | `ImageMetadataReader.readMetadata(stream)` |
| `ExifInterface.TAG_DATETIME_ORIGINAL` | `ExifDirectory.TAG_DATETIME_ORIGINAL` |
| `ExifInterface.TAG_DATETIME` | `ExifIFD0Directory.TAG_DATETIME` |
| `ExifInterface.TAG_OFFSET_TIME_ORIGINAL` | `ExifDirectory.TAG_OFFSET_TIME_ORIGINAL` |
| `ExifInterface.TAG_OFFSET_TIME` | `ExifDirectory.TAG_OFFSET_TIME` |
| `exif.latLong` | `gpsDirectory.geoLocation` |
| `ExifInterface.TAG_ORIENTATION` | `ExifDirectory.TAG_ORIENTATION` |
| `ExifInterface.TAG_IMAGE_DESCRIPTION` | `ExifDirectory.TAG_IMAGE_DESCRIPTION` |
| `ExifDirectory.TAG_IMAGE_DESCRIPTION` | `ExifDirectory.TAG_IMAGE_DESCRIPTION` |
| `ExifInterface.TAG_USER_COMMENT` | `ExifDirectory.TAG_USER_COMMENT` |
| `ExifInterface.ORIENTATION_UNDEFINED` | custom constant `1` (EXIF spec: 1 = normal) |
| `exif.getAttributeBytes(tag)` | `directory.getByteArray(tagType)` |
| `exif.getAttribute(tag)` | `directory.getString(tagType)` |
| `exif.getAttributeInt(tag, default)` | `directory.getInt(tagType)` with fallback |

Here is the complete replacement file:

```kotlin
package com.neilturner.aerialviews.ui.helpers

import com.drewnoakes.metadata.Metadata
import com.drewnoakes.metadata.exif.ExifDirectory
import com.drewnoakes.metadata.exif.ExifIFD0Directory
import com.drewnoakes.metadata.exif.ExifReader
import com.drewnoakes.metadata.exif.GpsDirectory
import com.drewnoakes.metadata.exif.GpsDirectory.TAG_LATITUDE
import com.drewnoakes.metadata.exif.GpsDirectory.TAG_LATITUDE_REF
import com.drewnoakes.metadata.exif.GpsDirectory.TAG_LONGITUDE
import com.drewnoakes.metadata.exif.GpsDirectory.TAG_LONGITUDE_REF
import com.drewnoakes.metadata.gif.GifDirectory
import com.drewnoakes.metadata.ImageMetadataReader
import com.drewnoakes.metadata.iptc.IptcDirectory
import io.ktor.utils.io.charsets.forName
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.Locale

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

    private const val ORIENTATION_UNDEFINED = 0

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
                val exifDir = metadata.getFirstDirectoryOfType(ExifDirectory::class.java)
                val gpsDir = metadata.getFirstDirectoryOfType(GpsDirectory::class.java)
                val description = extractExifDescription(metadata)
                ExifMetadata(
                    date = exifDir?.getString(ExifDirectory.TAG_DATETIME_ORIGINAL)
                        ?: metadata.getFirstDirectoryOfType(ExifIFD0Directory::class.java)?.getString(ExifIFD0Directory.TAG_DATETIME),
                    offset = exifDir?.getString(ExifDirectory.TAG_OFFSET_TIME_ORIGINAL)
                        ?: exifDir?.getString(ExifDirectory.TAG_OFFSET_TIME),
                    latitude = gpsDir?.geoLocation?.latitude,
                    longitude = gpsDir?.geoLocation?.longitude,
                    description = description,
                    orientation = exifDir?.getInt(ExifDirectory.TAG_ORIENTATION) ?: ORIENTATION_UNDEFINED,
                )
            } ?: ExifMetadata()
        } catch (_: Exception) {
            ExifMetadata()
        }

    private fun extractExifDescription(metadata: Metadata): String? {
        val imageDescription =
            decodeExifText(
                metadata = metadata,
                exifTag = ExifDirectory.TAG_IMAGE_DESCRIPTION,
                iptcTag = IptcDirectory.TAG_OBJECT_NAME,
                hasUserCommentPrefix = false,
            )
        sanitizeExifDescription(imageDescription)?.let { return it }

        if (!PARSE_USER_COMMENT) return null

        val userComment =
            decodeExifText(
                metadata = metadata,
                exifTag = ExifDirectory.TAG_USER_COMMENT,
                iptcTag = null,
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
        iptcTag: Int?,
        hasUserCommentPrefix: Boolean,
    ): String? {
        val exifDir = metadata.getFirstDirectoryOfType(ExifDirectory::class.java)
        val rawBytes = exifDir?.getByteArray(exifTag)
        val decoded =
            if (rawBytes != null) {
                if (hasUserCommentPrefix) decodeUserComment(rawBytes) else decodeBestEffort(rawBytes)
            } else {
                // Fall back to IPTC if EXIF tag not found
                val iptcDir = iptcTag?.let { metadata.getFirstDirectoryOfType(IptcDirectory::class.java) }
                iptcDir?.getString(iptcTag)
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
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/neilturner/aerialviews/ui/helpers/BitmapHelper.kt
git commit -m "refactor: replace AndroidX ExifInterface with metadata-extractor in BitmapHelper"
```

---

### Task 3: Run tests and verify build

**Covers:** Verification

**Files:**
- No file changes — verification only

- [ ] **Step 1: Run existing unit tests**

Run: `./gradlew :app:testGithubDebugUnitTest --tests "com.neilturner.aerialviews.ui.helpers.BitmapHelperTest"`
Expected: All 4 tests PASS

- [ ] **Step 2: Run lint check**

Run: `./gradlew :app:lintGithubDebug`
Expected: No new errors related to BitmapHelper or exif

- [ ] **Step 3: Verify full compile**

Run: `./gradlew :app:compileGithubDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit if any fixes needed**

If steps 1-3 revealed issues, fix and commit:

```bash
git add -A
git commit -m "fix: address issues from metadata-extractor migration"
```
