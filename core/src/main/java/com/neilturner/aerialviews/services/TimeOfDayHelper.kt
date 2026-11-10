package com.neilturner.aerialviews.services

import com.neilturner.aerialviews.models.TimeOfDayFilter
import com.neilturner.aerialviews.models.enums.TimeOfDay
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

object TimeOfDayHelper {
    /**
     * Determines the current time phase (DAY or NIGHT) based on local calculation.
     * Uses the stored latitude and longitude from GeneralPrefs.
     */
    fun getCurrentTimePeriod(
        now: Calendar = Calendar.getInstance(),
        zone: TimeZone = TimeZone.getDefault(),
    ): TimeOfDay =
        getTimePeriod(
            now = now,
            zone = zone,
            latitude = GeneralPrefs.weatherLocationLat.toDoubleOrNull(),
            longitude = GeneralPrefs.weatherLocationLon.toDoubleOrNull(),
        )

    /** The filter implied by the current preferences. */
    fun currentFilter(): TimeOfDayFilter =
        if (GeneralPrefs.autoTimeOfDay) {
            TimeOfDayFilter.forPeriod(
                period = getCurrentTimePeriod(),
                dayIncludes = GeneralPrefs.playlistTimeOfDayDayIncludes,
                nightIncludes = GeneralPrefs.playlistTimeOfDayNightIncludes,
            )
        } else {
            TimeOfDayFilter.ALL
        }

    /**
     * Pure version of [getCurrentTimePeriod], used by the tests.
     */
    internal fun getTimePeriod(
        now: Calendar,
        zone: TimeZone,
        latitude: Double?,
        longitude: Double?,
    ): TimeOfDay {
        if (latitude == null || longitude == null) return fallback(now)

        val dayOfYear = now.get(Calendar.DAY_OF_YEAR)
        val zoneOffsetHours = zone.getOffset(now.timeInMillis) / 3_600_000.0
        val sunriseMinutes =
            calculateSunriseSunset(
                dayOfYear = dayOfYear,
                latitude = latitude,
                longitude = longitude,
                isSunrise = true,
                zoneOffsetHours = zoneOffsetHours,
            )
        val sunsetMinutes =
            calculateSunriseSunset(
                dayOfYear = dayOfYear,
                latitude = latitude,
                longitude = longitude,
                isSunrise = false,
                zoneOffsetHours = zoneOffsetHours,
            )
        val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

        return periodFor(currentMinutes, sunriseMinutes, sunsetMinutes) ?: fallback(now)
    }

    /**
     * Returns DAY when [currentMinutes] falls inside the sunrise/sunset range, NIGHT otherwise, or
     * null when either boundary could not be calculated.
     */
    internal fun periodFor(
        currentMinutes: Int,
        sunriseMinutes: Int?,
        sunsetMinutes: Int?,
    ): TimeOfDay? {
        if (sunriseMinutes == null || sunsetMinutes == null) return null
        return if (currentMinutes in sunriseMinutes until sunsetMinutes) {
            TimeOfDay.DAY
        } else {
            TimeOfDay.NIGHT
        }
    }

    internal fun fallback(now: Calendar = Calendar.getInstance()): TimeOfDay {
        val hour = now.get(Calendar.HOUR_OF_DAY)
        return if (hour in 6..17) TimeOfDay.DAY else TimeOfDay.NIGHT
    }

    /**
     * Calculates sunrise or sunset time in minutes from the start of the day.
     * Algorithm based on "Solar Calculation" by NOAA.
     */
    internal fun calculateSunriseSunset(
        dayOfYear: Int,
        latitude: Double,
        longitude: Double,
        isSunrise: Boolean,
        zoneOffsetHours: Double,
    ): Int? {
        val zenith = 90.8333 // Official zenith for sunrise/sunset

        // 1. first calculate the day of the year
        val n = dayOfYear.toDouble()

        // 2. convert the longitude to hour value and calculate an approximate time
        val lngHour = longitude / 15.0
        val t = if (isSunrise) n + ((6.0 - lngHour) / 24.0) else n + ((18.0 - lngHour) / 24.0)

        // 3. calculate the Sun's mean anomaly
        val m = (0.9856 * t) - 3.289

        // 4. calculate the Sun's true longitude
        var l = m + (1.916 * sin(Math.toRadians(m))) + (0.020 * sin(Math.toRadians(2 * m))) + 282.634
        l %= 360
        if (l < 0) l += 360

        // 5. calculate the Sun's right ascension
        var ra = Math.toDegrees(atan(tan(Math.toRadians(l)) * 0.91764))
        ra %= 360
        if (ra < 0) ra += 360

        // 5b. right ascension value needs to be in the same quadrant as L
        val lQuadrant = floor(l / 90.0) * 90.0
        val raQuadrant = floor(ra / 90.0) * 90.0
        ra += (lQuadrant - raQuadrant)

        // 5c. right ascension value needs to be converted into hours
        ra /= 15.0

        // 6. calculate the Sun's declination
        val sinDec = 0.39782 * sin(Math.toRadians(l))
        val cosDec = cos(asin(sinDec))

        // 7. calculate the Sun's local hour angle
        val cosH =
            (cos(Math.toRadians(zenith)) - (sinDec * sin(Math.toRadians(latitude)))) / (
                cosDec *
                    cos(
                        Math.toRadians(latitude),
                    )
            )

        // The sun never rises: an empty sunrise..sunset range keeps the result NIGHT all day.
        if (cosH > 1) return 0
        // The sun never sets: a full 0..1440 range keeps the result DAY all day.
        if (cosH < -1) return if (isSunrise) 0 else 1440

        // 8. finish calculating H and convert into hours
        var h = if (isSunrise) 360.0 - Math.toDegrees(acos(cosH)) else Math.toDegrees(acos(cosH))
        h /= 15.0

        // 9. calculate local mean time of rising/setting
        val tTime = h + ra - (0.06571 * t) - 6.622

        // 10. adjust back to UTC
        var ut = tTime - lngHour
        ut %= 24
        if (ut < 0) ut += 24

        // 11. convert UT value to local time zone of latitude/longitude
        var localT = ut + zoneOffsetHours
        localT %= 24
        if (localT < 0) localT += 24

        return (localT * 60).toInt()
    }
}
