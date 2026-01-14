package com.neilturner.aerialviews.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class CountdownTimeParserTest {

    @Test
    fun `parseTargetTime handles time format`() {
        val now = LocalDateTime.of(2023, 1, 1, 10, 0) // 10:00

        // Future time same day
        var result = CountdownTimeParser.parseTargetTime("12:00", now)
        assertNotNull(result)
        assertEquals(LocalDateTime.of(2023, 1, 1, 12, 0), result)

        // Past time -> next day
        result = CountdownTimeParser.parseTargetTime("09:00", now)
        assertNotNull(result)
        assertEquals(LocalDateTime.of(2023, 1, 2, 9, 0), result)
    }

    @Test
    fun `parseTargetTime handles invalid times`() {
        assertNull(CountdownTimeParser.parseTargetTime("25:00"))
        assertNull(CountdownTimeParser.parseTargetTime("12:60"))
        assertNull(CountdownTimeParser.parseTargetTime("99:99"))
        assertNull(CountdownTimeParser.parseTargetTime("abc"))
    }

    @Test
    fun `parseTargetTime handles full date time format`() {
        // Standard ISOish
        var result = CountdownTimeParser.parseTargetTime("2023-12-31 23:59")
        assertEquals(LocalDateTime.of(2023, 12, 31, 23, 59), result)

        // Single digit month/day
        result = CountdownTimeParser.parseTargetTime("2023-1-1 1:05")
        assertEquals(LocalDateTime.of(2023, 1, 1, 1, 5), result)
    }

    @Test
    fun `parseTargetTime handles invalid dates`() {
        // Invalid month
        assertNull(CountdownTimeParser.parseTargetTime("2023-13-01 12:00"))
        // Invalid day (generic)
        assertNull(CountdownTimeParser.parseTargetTime("2023-01-32 12:00"))
        // Invalid Leap year date (Feb 29 2023 is invalid)
        assertNull(CountdownTimeParser.parseTargetTime("2023-02-29 12:00"))
        // Valid Leap year date
        val result = CountdownTimeParser.parseTargetTime("2024-02-29 12:00")
        assertEquals(LocalDateTime.of(2024, 2, 29, 12, 0), result)
    }
}
