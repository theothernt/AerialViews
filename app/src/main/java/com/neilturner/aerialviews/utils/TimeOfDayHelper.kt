package com.neilturner.aerialviews.utils

import com.neilturner.aerialviews.models.enums.TimeOfDay
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import java.lang.Math.toDegrees
import java.lang.Math.toRadians
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
    fun getCurrentTimePeriod(): TimeOfDay {
        val lat = GeneralPrefs.weatherLocationLat.toDoubleOrNull() ?: return fallback()
        val lon = GeneralPrefs.weatherLocationLon.toDoubleOrNull() ?: return fallback()

        val now = Calendar.getInstance()
        val sunriseMinutes = calculateSunriseSunset(now, lat, lon, true)
        val sunsetMinutes = calculateSunriseSunset(now, lat, lon, false)

        if (sunriseMinutes == null || sunsetMinutes == null) return fallback()

        val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

        return if (currentMinutes in sunriseMinutes until sunsetMinutes) {
            TimeOfDay.DAY
        } else {
            TimeOfDay.NIGHT
        }
    }

    private fun fallback(): TimeOfDay {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return if (hour in 6..17) TimeOfDay.DAY else TimeOfDay.NIGHT
    }

    /**
     * Calculates sunrise or sunset time in minutes from the start of the day.
     * Algorithm based on "Solar Calculation" by NOAA.
     */
    private fun calculateSunriseSunset(
        calendar: Calendar,
        latitude: Double,
        longitude: Double,
        isSunrise: Boolean,
    ): Int? {
        val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)
        val zenith = 90.8333 // Official zenith for sunrise/sunset

        // 1. first calculate the day of the year
        val n = dayOfYear.toDouble()

        // 2. convert the longitude to hour value and calculate an approximate time
        val lngHour = longitude / 15.0
        val t = if (isSunrise) n + ((6.0 - lngHour) / 24.0) else n + ((18.0 - lngHour) / 24.0)

        // 3. calculate the Sun's mean anomaly
        val m = (0.9856 * t) - 3.289

        // 4. calculate the Sun's true longitude
        var l = m + (1.916 * sin(toRadians(m))) + (0.020 * sin(toRadians(2 * m))) + 282.634
        l %= 360
        if (l < 0) l += 360

        // 5. calculate the Sun's right ascension
        var ra = toDegrees(atan(tan(toRadians(l)) * 0.91764))
        ra %= 360
        if (ra < 0) ra += 360

        // 5b. right ascension value needs to be in the same quadrant as L
        val lQuadrant = floor(l / 90.0) * 90.0
        val raQuadrant = floor(ra / 90.0) * 90.0
        ra += (lQuadrant - raQuadrant)

        // 5c. right ascension value needs to be converted into hours
        ra /= 15.0

        // 6. calculate the Sun's declination
        val sinDec = 0.39782 * sin(toRadians(l))
        val cosDec = cos(asin(sinDec))

        // 7. calculate the Sun's local hour angle
        val cosH = (cos(toRadians(zenith)) - (sinDec * sin(toRadians(latitude)))) / (cosDec * cos(toRadians(latitude)))

        if (cosH > 1) return if (isSunrise) null else null // always night
        if (cosH < -1) return if (isSunrise) 0 else 1439 // always day

        // 8. finish calculating H and convert into hours
        var h = if (isSunrise) 360.0 - toDegrees(acos(cosH)) else toDegrees(acos(cosH))
        h /= 15.0

        // 9. calculate local mean time of rising/setting
        val tTime = h + ra - (0.06571 * t) - 6.622

        // 10. adjust back to UTC
        var ut = tTime - lngHour
        ut %= 24
        if (ut < 0) ut += 24

        // 11. convert UT value to local time zone of latitude/longitude
        val localOffset = TimeZone.getDefault().getOffset(calendar.timeInMillis) / 3600000.0
        var localT = ut + localOffset
        localT %= 24
        if (localT < 0) localT += 24

        return (localT * 60).toInt()
    }
}
