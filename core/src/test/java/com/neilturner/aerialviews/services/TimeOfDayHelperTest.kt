package com.neilturner.aerialviews.services

import com.neilturner.aerialviews.models.enums.TimeOfDay
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Calendar
import java.util.TimeZone

internal class TimeOfDayHelperTest {
    private val utc: TimeZone = TimeZone.getTimeZone("UTC")

    @Test
    fun `period is day between sunrise and sunset`() {
        assertEquals(TimeOfDay.DAY, TimeOfDayHelper.periodFor(currentMinutes = 12 * 60, sunriseMinutes = 6 * 60, sunsetMinutes = 18 * 60))
    }

    @Test
    fun `sunrise minute counts as day and sunset minute counts as night`() {
        assertEquals(TimeOfDay.DAY, TimeOfDayHelper.periodFor(currentMinutes = 6 * 60, sunriseMinutes = 6 * 60, sunsetMinutes = 18 * 60))
        assertEquals(TimeOfDay.NIGHT, TimeOfDayHelper.periodFor(currentMinutes = 18 * 60, sunriseMinutes = 6 * 60, sunsetMinutes = 18 * 60))
    }

    @Test
    fun `minutes outside the range are night`() {
        assertEquals(
            TimeOfDay.NIGHT,
            TimeOfDayHelper.periodFor(
                currentMinutes = 5 * 60 + 59,
                sunriseMinutes = 6 * 60,
                sunsetMinutes =
                    18 * 60,
            ),
        )
        assertEquals(TimeOfDay.NIGHT, TimeOfDayHelper.periodFor(currentMinutes = 23 * 60, sunriseMinutes = 6 * 60, sunsetMinutes = 18 * 60))
    }

    @Test
    fun `period is null when a boundary could not be calculated`() {
        assertNull(TimeOfDayHelper.periodFor(currentMinutes = 60, sunriseMinutes = null, sunsetMinutes = 18 * 60))
        assertNull(TimeOfDayHelper.periodFor(currentMinutes = 60, sunriseMinutes = 6 * 60, sunsetMinutes = null))
    }

    @Test
    fun `polar night keeps the whole day at night`() {
        // Empty sunrise..sunset range: every minute falls outside it.
        for (minute in 0 until 24 * 60 step 60) {
            assertEquals(
                TimeOfDay.NIGHT,
                TimeOfDayHelper.periodFor(currentMinutes = minute, sunriseMinutes = 0, sunsetMinutes = 0),
                "Expected night at minute $minute",
            )
        }
    }

    @Test
    fun `polar day keeps the whole day at day`() {
        // Full 0..1440 range: every minute of the day falls inside it.
        for (minute in 0 until 24 * 60 step 60) {
            assertEquals(
                TimeOfDay.DAY,
                TimeOfDayHelper.periodFor(currentMinutes = minute, sunriseMinutes = 0, sunsetMinutes = 24 * 60),
                "Expected day at minute $minute",
            )
        }
    }

    @Test
    fun `fallback treats 06 to 17 as day`() {
        assertEquals(TimeOfDay.DAY, TimeOfDayHelper.fallback(calendar(2024, Calendar.JUNE, 21, hour = 6)))
        assertEquals(TimeOfDay.DAY, TimeOfDayHelper.fallback(calendar(2024, Calendar.JUNE, 21, hour = 17, minute = 59)))
        assertEquals(TimeOfDay.NIGHT, TimeOfDayHelper.fallback(calendar(2024, Calendar.JUNE, 21, hour = 18)))
        assertEquals(TimeOfDay.NIGHT, TimeOfDayHelper.fallback(calendar(2024, Calendar.JUNE, 21, hour = 5, minute = 59)))
    }

    @Test
    fun `missing location uses the fixed fallback`() {
        assertEquals(
            TimeOfDay.DAY,
            TimeOfDayHelper.getTimePeriod(
                now = calendar(2024, Calendar.JUNE, 21, hour = 12),
                zone = utc,
                latitude = null,
                longitude = null,
            ),
        )
        assertEquals(
            TimeOfDay.NIGHT,
            TimeOfDayHelper.getTimePeriod(
                now = calendar(2024, Calendar.JUNE, 21, hour = 23),
                zone = utc,
                latitude = null,
                longitude = null,
            ),
        )
    }

    @Test
    fun `london midsummer sunrise and sunset are in the expected range`() {
        val sunrise =
            TimeOfDayHelper.calculateSunriseSunset(
                dayOfYear = dayOfYear(2024, Calendar.JUNE, 21),
                latitude = LONDON_LAT,
                longitude = LONDON_LON,
                isSunrise = true,
                zoneOffsetHours = 0.0,
            )
        val sunset =
            TimeOfDayHelper.calculateSunriseSunset(
                dayOfYear = dayOfYear(2024, Calendar.JUNE, 21),
                latitude = LONDON_LAT,
                longitude = LONDON_LON,
                isSunrise = false,
                zoneOffsetHours = 0.0,
            )

        assertNotNull(sunrise)
        assertNotNull(sunset)
        assertTrue(sunrise!! in 200..300, "Expected sunrise around 03:43 UTC, got $sunrise minutes")
        assertTrue(sunset!! in 1_200..1_300, "Expected sunset around 20:21 UTC, got $sunset minutes")
        assertTrue(sunrise < sunset)
    }

    @Test
    fun `london midsummer period is day at noon and night after dark`() {
        val noon =
            TimeOfDayHelper.getTimePeriod(
                now = calendar(2024, Calendar.JUNE, 21, hour = 12),
                zone = utc,
                latitude = LONDON_LAT,
                longitude = LONDON_LON,
            )
        val night =
            TimeOfDayHelper.getTimePeriod(
                now = calendar(2024, Calendar.JUNE, 21, hour = 23),
                zone = utc,
                latitude = LONDON_LAT,
                longitude = LONDON_LON,
            )

        assertEquals(TimeOfDay.DAY, noon)
        assertEquals(TimeOfDay.NIGHT, night)
    }

    /**
     * Documents current behaviour: the device time zone is applied to the location's solar times,
     * so a device set to UTC+2 sees sunrise two hours later than one set to UTC. A location-aware
     * fix is tracked separately.
     */
    @Test
    fun `device time zone shifts calculated solar times`() {
        val dayOfYear = dayOfYear(2024, Calendar.JUNE, 21)
        val sunriseUtc =
            TimeOfDayHelper.calculateSunriseSunset(dayOfYear, LONDON_LAT, LONDON_LON, isSunrise = true, zoneOffsetHours = 0.0)
        val sunrisePlusTwo =
            TimeOfDayHelper.calculateSunriseSunset(dayOfYear, LONDON_LAT, LONDON_LON, isSunrise = true, zoneOffsetHours = 2.0)

        assertNotNull(sunriseUtc)
        assertNotNull(sunrisePlusTwo)
        assertEquals(120, sunrisePlusTwo!! - sunriseUtc!!)
    }

    @Test
    fun `svalbard has polar night in january and polar day in july`() {
        val january = dayOfYear(2024, Calendar.JANUARY, 15)
        val july = dayOfYear(2024, Calendar.JULY, 15)

        val januarySunrise =
            TimeOfDayHelper.calculateSunriseSunset(january, SVALBARD_LAT, SVALBARD_LON, isSunrise = true, zoneOffsetHours = 1.0)
        val januarySunset =
            TimeOfDayHelper.calculateSunriseSunset(january, SVALBARD_LAT, SVALBARD_LON, isSunrise = false, zoneOffsetHours = 1.0)
        assertEquals(0, januarySunrise)
        assertEquals(0, januarySunset)
        assertEquals(
            TimeOfDay.NIGHT,
            TimeOfDayHelper.periodFor(12 * 60, januarySunrise, januarySunset),
        )

        val julySunrise =
            TimeOfDayHelper.calculateSunriseSunset(july, SVALBARD_LAT, SVALBARD_LON, isSunrise = true, zoneOffsetHours = 2.0)
        val julySunset =
            TimeOfDayHelper.calculateSunriseSunset(july, SVALBARD_LAT, SVALBARD_LON, isSunrise = false, zoneOffsetHours = 2.0)
        assertEquals(0, julySunrise)
        assertEquals(24 * 60, julySunset)
        assertEquals(
            TimeOfDay.DAY,
            TimeOfDayHelper.periodFor(23 * 60, julySunrise, julySunset),
        )
    }

    private fun calendar(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int = 0,
        zone: TimeZone = utc,
    ): Calendar =
        Calendar.getInstance(zone).apply {
            clear()
            set(year, month, day, hour, minute)
        }

    private fun dayOfYear(
        year: Int,
        month: Int,
        day: Int,
    ): Int = calendar(year, month, day, hour = 12).get(Calendar.DAY_OF_YEAR)

    private companion object {
        const val LONDON_LAT = 51.5074
        const val LONDON_LON = -0.1278
        const val SVALBARD_LAT = 78.2232
        const val SVALBARD_LON = 15.6267
    }
}
