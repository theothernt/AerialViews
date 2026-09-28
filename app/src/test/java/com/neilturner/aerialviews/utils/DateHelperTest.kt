package com.neilturner.aerialviews.utils

import com.neilturner.aerialviews.models.enums.DateType
import com.neilturner.aerialviews.ui.helpers.DateHelper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Date Helper Tests")
internal class DateHelperTest {
    // ─── EXIF-format dates (local photos / local videos) ───────────────────────

    @Test
    @DisplayName("Should format EXIF date with offset")
    fun testFormatExifDateWithOffset() {
        val result =
            DateHelper.formatExifDate(
                date = "2025:01:15 13:45:30",
                offset = "+05:00",
                type = DateType.CUSTOM,
                custom = "yyyy-MM-dd HH:mm",
            )
        assertEquals("2025-01-15 13:45", result)
    }

    @Test
    @DisplayName("Should format EXIF date without offset in system zone")
    fun testFormatExifDateWithoutOffset() {
        val result =
            DateHelper.formatExifDate(
                date = "2025:01:15 13:45:30",
                offset = null,
                type = DateType.CUSTOM,
                custom = "yyyy-MM-dd HH:mm",
            )
        assertNotNull(result)
    }

    // ─── ISO-8601 dates (NC Memories, Immich, etc.) ───────────────────────────

    @Test
    @DisplayName("Should format ISO UTC date with separate offset (NC Memories bug)")
    fun testFormatExifDateIsoUtcWithSeparateOffset() {
        // NC Memories produces ISO-8601 UTC instants with a separate offset.
        // The instant 10:30 UTC should display as 15:30 in the +05:00 timezone.
        val result =
            DateHelper.formatExifDate(
                date = "2024-01-15T10:30:00Z",
                offset = "+05:00",
                type = DateType.CUSTOM,
                custom = "yyyy-MM-dd HH:mm",
            )
        assertEquals("2024-01-15 15:30", result)
    }

    @Test
    @DisplayName("Should format ISO UTC date with positive separate offset")
    fun testFormatExifDateIsoUtcWithPositiveOffset() {
        // 10:30 UTC = 07:30 in -03:00
        val result =
            DateHelper.formatExifDate(
                date = "2024-01-15T10:30:00Z",
                offset = "-03:00",
                type = DateType.CUSTOM,
                custom = "yyyy-MM-dd HH:mm",
            )
        assertEquals("2024-01-15 07:30", result)
    }

    @Test
    @DisplayName("Should format ISO UTC date without separate offset")
    fun testFormatExifDateIsoUtcWithoutOffset() {
        val result =
            DateHelper.formatExifDate(
                date = "2024-01-15T10:30:00Z",
                offset = null,
                type = DateType.CUSTOM,
                custom = "yyyy-MM-dd HH:mm",
            )
        assertNotNull(result)
    }

    @Test
    @DisplayName("Should format ISO date with embedded negative offset")
    fun testFormatExifDateIsoEmbeddedNegativeOffset() {
        val result =
            DateHelper.formatExifDate(
                date = "2024-01-15T10:30:00.000-05:00",
                offset = "-05:00",
                type = DateType.CUSTOM,
                custom = "yyyy-MM-dd HH:mm",
            )
        assertEquals("2024-01-15 10:30", result)
    }

    @Test
    @DisplayName("Should format ISO date with embedded positive offset")
    fun testFormatExifDateIsoEmbeddedPositiveOffset() {
        val result =
            DateHelper.formatExifDate(
                date = "2024-01-15T10:30:00.000+05:00",
                offset = "+05:00",
                type = DateType.CUSTOM,
                custom = "yyyy-MM-dd HH:mm",
            )
        assertEquals("2024-01-15 10:30", result)
    }

    @Test
    @DisplayName("Should format ISO date with embedded offset but no separate offset")
    fun testFormatExifDateIsoEmbeddedOffsetOnly() {
        val result =
            DateHelper.formatExifDate(
                date = "2024-01-15T10:30:00.000-05:00",
                offset = null,
                type = DateType.CUSTOM,
                custom = "yyyy-MM-dd HH:mm",
            )
        // 10:30 at -05:00 → formats in system default zone
        assertNotNull(result)
    }

    @Test
    @DisplayName("Should format Immich-style ISO date with separate offset")
    fun testFormatExifDateImmichIsoWithOffset() {
        // Immich returns "2024-01-15T19:30:00.000Z" with a separate offset
        // 19:30 UTC = 00:30 next day in +05:00
        val result =
            DateHelper.formatExifDate(
                date = "2024-01-15T19:30:00.000Z",
                offset = "+05:00",
                type = DateType.CUSTOM,
                custom = "yyyy-MM-dd HH:mm",
            )
        assertEquals("2024-01-16 00:30", result)
    }

    // ─── ZonedDateTime fallback ───────────────────────────────────────────────

    @Test
    @DisplayName("Should format ZonedDateTime with separate offset")
    fun testFormatExifDateZonedDateTimeWithOffset() {
        val result =
            DateHelper.formatExifDate(
                date = "2024-01-15T10:30:00+05:00[Asia/Karachi]",
                offset = "+05:00",
                type = DateType.CUSTOM,
                custom = "yyyy-MM-dd HH:mm",
            )
        assertEquals("2024-01-15 10:30", result)
    }

    // ─── FULL / COMPACT modes ─────────────────────────────────────────────────

    @Test
    @DisplayName("Should format ISO date in FULL mode with offset")
    fun testFormatExifDateIsoFullMode() {
        val result =
            DateHelper.formatExifDate(
                date = "2024-01-15T10:30:00Z",
                offset = "+05:00",
                type = DateType.FULL,
                custom = null,
            )
        assertNotNull(result)
    }

    @Test
    @DisplayName("Should format ISO date in COMPACT mode with offset")
    fun testFormatExifDateIsoCompactMode() {
        val result =
            DateHelper.formatExifDate(
                date = "2024-01-15T10:30:00Z",
                offset = "+05:00",
                type = DateType.COMPACT,
                custom = null,
            )
        assertNotNull(result)
    }

    // ─── Error handling ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Should return null for invalid date")
    fun testFormatExifDateInvalidDate() {
        val result =
            DateHelper.formatExifDate(
                date = "not-a-date",
                offset = null,
                type = DateType.COMPACT,
                custom = null,
            )
        assertNull(result)
    }

    @Test
    @DisplayName("Should return null for invalid custom date pattern")
    fun testFormatExifDateInvalidPattern() {
        val result =
            DateHelper.formatExifDate(
                date = "2025:01:15 13:45:30",
                offset = null,
                type = DateType.CUSTOM,
                custom = "invalid-pattern",
            )
        assertNull(result)
    }

    // ─── Formatter timezone regression (TimeZone.getTimeZone overload bug) ────
    // These tests catch the silent GMT fallback in TimeZone.getTimeZone(String)
    // when given a "+HH:mm" offset string. The formatter must use the correct
    // offset or the displayed hour will be wrong.

    @Test
    @DisplayName("Formatter should use EXIF offset, not silently fall back to GMT (positive offset)")
    fun testFormatterUsesPositiveOffset() {
        // EXIF local time 00:30 at +02:00. The Instant is 2025-01-14T22:30:00Z.
        // A formatter silently in GMT would render "22:30" (the previous UTC hour).
        // The correct formatter at +02:00 must render "00:30".
        val result =
            DateHelper.formatExifDate(
                date = "2025:01:15 00:30:00",
                offset = "+02:00",
                type = DateType.CUSTOM,
                custom = "HH:mm",
            )
        assertEquals("00:30", result)
    }

    @Test
    @DisplayName("Formatter should use EXIF offset, not silently fall back to GMT (negative offset)")
    fun testFormatterUsesNegativeOffset() {
        // EXIF local time 03:00 at -05:00. The Instant is 2025-01-15T08:00:00Z.
        // A formatter silently in GMT would render "08:00".
        // The correct formatter at -05:00 must render "03:00".
        val result =
            DateHelper.formatExifDate(
                date = "2025:01:15 03:00:00",
                offset = "-05:00",
                type = DateType.CUSTOM,
                custom = "HH:mm",
            )
        assertEquals("03:00", result)
    }

    // ─── Original regression tests ────────────────────────────────────────────

    @Test
    @DisplayName("Should format EXIF date with custom pattern")
    fun testFormatExifDateCustom() {
        val result =
            DateHelper.formatExifDate(
                date = "2025:01:15 13:45:30",
                offset = null,
                type = DateType.CUSTOM,
                custom = "yyyy-MM-dd",
            )
        assertEquals("2025-01-15", result)
    }

    @Test
    @DisplayName("Should format EXIF date in compact mode")
    fun testFormatExifDateCompact() {
        val result =
            DateHelper.formatExifDate(
                date = "2025:01:15 13:45:30",
                offset = null,
                type = DateType.COMPACT,
                custom = null,
            )
        assertNotNull(result)
    }

    @Test
    @DisplayName("Should parse ISO date from Immich")
    fun testFormatExifDateIso() {
        val result =
            DateHelper.formatExifDate(
                date = "2024-01-15T19:30:00.000Z",
                offset = null,
                type = DateType.CUSTOM,
                custom = "yyyy-MM-dd",
            )
        assertEquals("2024-01-15", result)
    }

    @Test
    @DisplayName("Should parse ISO date with negative offset from Immich")
    fun testFormatExifDateIsoNegativeOffset() {
        val result =
            DateHelper.formatExifDate(
                date = "2024-01-15T10:30:00.000-05:00",
                offset = "-05:00",
                type = DateType.CUSTOM,
                custom = "yyyy-MM-dd HH:mm",
            )
        assertEquals("2024-01-15 10:30", result)
    }
}
