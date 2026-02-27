package com.neilturner.aerialviews.utils

import com.neilturner.aerialviews.models.enums.DateType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Date Helper Tests")
internal class DateHelperTest {
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

    @Test
    @DisplayName("Should return null for invalid EXIF date")
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
}
