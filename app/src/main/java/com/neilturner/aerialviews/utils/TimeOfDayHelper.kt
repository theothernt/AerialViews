package com.neilturner.aerialviews.utils

import com.neilturner.aerialviews.models.enums.TimeOfDay
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.*

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
    private fun calculateSunriseSunset(calendar: Calendar, latitude: Double, longitude: Double, isSunrise: Boolean): Int? {
        val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)
        val zenith = 90.8333 // Official zenith for sunrise/sunset

        // 1. first calculate the day of the year
        val N = dayOfYear.toDouble()

        // 2. convert the longitude to hour value and calculate an approximate time
        val lngHour = longitude / 15.0
        val t = if (isSunrise) N + ((6.0 - lngHour) / 24.0) else N + ((18.0 - lngHour) / 24.0)

        // 3. calculate the Sun's mean anomaly
        val M = (0.9856 * t) - 3.289

        // 4. calculate the Sun's true longitude
        var L = M + (1.916 * sin(Math.toRadians(M))) + (0.020 * sin(Math.toRadians(2 * M))) + 282.634
        L = L % 360
        if (L < 0) L += 360

        // 5. calculate the Sun's right ascension
        var RA = Math.toDegrees(atan(0.91764 * tan(Math.toRadians(L))))
        RA = RA % 360
        if (RA < 0) RA += 360

        // 5b. right ascension value needs to be in the same quadrant as L
        val Lquadrant = floor(L / 90.0) * 90.0
        val RAquadrant = floor(RA / 90.0) * 90.0
        RA = RA + (Lquadrant - RAquadrant)

        // 5c. right ascension value needs to be converted into hours
        RA = RA / 15.0

        // 6. calculate the Sun's declination
        val sinDec = 0.39782 * sin(Math.toRadians(L))
        val cosDec = cos(asin(sinDec))

        // 7. calculate the Sun's local hour angle
        val cosH = (cos(Math.toRadians(zenith)) - (sinDec * sin(Math.toRadians(latitude)))) / (cosDec * cos(Math.toRadians(latitude)))

        if (cosH > 1) return if (isSunrise) null else null // always night
        if (cosH < -1) return if (isSunrise) 0 else 1439 // always day

        // 8. finish calculating H and convert into hours
        var H = if (isSunrise) 360.0 - Math.toDegrees(acos(cosH)) else Math.toDegrees(acos(cosH))
        H = H / 15.0

        // 9. calculate local mean time of rising/setting
        val T = H + RA - (0.06571 * t) - 6.622

        // 10. adjust back to UTC
        var UT = T - lngHour
        UT = UT % 24
        if (UT < 0) UT += 24

        // 11. convert UT value to local time zone of latitude/longitude
        val localOffset = TimeZone.getDefault().getOffset(calendar.timeInMillis) / 3600000.0
        var localT = UT + localOffset
        localT = localT % 24
        if (localT < 0) localT += 24

        return (localT * 60).toInt()
    }
}
