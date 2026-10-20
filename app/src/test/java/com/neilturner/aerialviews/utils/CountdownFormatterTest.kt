package com.neilturner.aerialviews.utils

import com.neilturner.aerialviews.ui.overlays.utils.CountdownFormatter
import com.neilturner.aerialviews.ui.overlays.utils.CountdownUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CountdownFormatterTest {
    private fun parts(totalSeconds: Long) = CountdownFormatter.parts(totalSeconds).map { it.unit to it.value }

    @Test
    fun `parts returns nothing for elapsed or zero time`() {
        assertTrue(CountdownFormatter.parts(0).isEmpty())
        assertTrue(CountdownFormatter.parts(-1).isEmpty())
    }

    @Test
    fun `parts handles seconds only`() {
        assertEquals(listOf(CountdownUnit.SECONDS to 1L), parts(1))
        assertEquals(listOf(CountdownUnit.SECONDS to 59L), parts(59))
    }

    @Test
    fun `parts handles minutes and seconds`() {
        assertEquals(
            listOf(
                CountdownUnit.MINUTES to 1L,
                CountdownUnit.SECONDS to 0L,
            ),
            parts(60),
        )
        assertEquals(
            listOf(
                CountdownUnit.MINUTES to 59L,
                CountdownUnit.SECONDS to 59L,
            ),
            parts(3599),
        )
    }

    @Test
    fun `parts handles hours and minutes`() {
        assertEquals(
            listOf(
                CountdownUnit.HOURS to 1L,
                CountdownUnit.MINUTES to 0L,
            ),
            parts(3600),
        )
        assertEquals(
            listOf(
                CountdownUnit.HOURS to 23L,
                CountdownUnit.MINUTES to 59L,
            ),
            parts(86399),
        )
    }

    @Test
    fun `parts handles days hours and minutes`() {
        assertEquals(
            listOf(
                CountdownUnit.DAYS to 1L,
                CountdownUnit.HOURS to 0L,
                CountdownUnit.MINUTES to 0L,
            ),
            parts(86400),
        )
        assertEquals(
            listOf(
                CountdownUnit.DAYS to 90L,
                CountdownUnit.HOURS to 5L,
                CountdownUnit.MINUTES to 30L,
            ),
            parts(7795800),
        )
    }

    @Test
    fun `parts omits the smallest unit as the magnitude grows`() {
        // Seconds are dropped once hours are reached, and never appear in the days tier
        val hourTier = CountdownFormatter.parts(3600)
        assertTrue(hourTier.none { it.unit == CountdownUnit.SECONDS })

        val dayTier = CountdownFormatter.parts(86400 + 3661)
        assertTrue(dayTier.none { it.unit == CountdownUnit.SECONDS })
    }
}
