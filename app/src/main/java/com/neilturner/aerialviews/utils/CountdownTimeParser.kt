package com.neilturner.aerialviews.utils

import java.time.LocalDateTime
import java.time.LocalTime
import timber.log.Timber

object CountdownTimeParser {

    private val TIME_REGEX = Regex("""^(\d{1,2}):(\d{2})$""")
    private val DATE_TIME_REGEX = Regex("""^(\d{4})-(\d{1,2})-(\d{1,2}) (\d{1,2}):(\d{2})$""")

    fun parseTargetTime(
        timeString: String,
        currentDateTime: LocalDateTime = LocalDateTime.now(),
        // For dependency injection/testing, though passed instance is usually sufficient
    ): LocalDateTime? {
        if (timeString.isEmpty()) return null

        return try {
            when {
                // HH:MM format
                TIME_REGEX.matches(timeString) -> {
                    val matchResult = TIME_REGEX.find(timeString) ?: return null
                    val (hourStr, minuteStr) = matchResult.destructured
                    
                    val hour = hourStr.toInt()
                    val minute = minuteStr.toInt()

                    // Semantic validation
                    if (hour !in 0..23 || minute !in 0..59) {
                        return null
                    }

                    val targetTime = LocalTime.of(hour, minute)
                    val targetDate = currentDateTime.toLocalDate()
                    var targetDateTime = LocalDateTime.of(targetDate, targetTime)

                    // If time has passed today, schedule for tomorrow
                    if (targetDateTime.isBefore(currentDateTime)) {
                        targetDateTime = targetDateTime.plusDays(1)
                    }
                    targetDateTime
                }

                // YYYY-MM-DD HH:MM format
                DATE_TIME_REGEX.matches(timeString) -> {
                    val matchResult = DATE_TIME_REGEX.find(timeString) ?: return null
                    val (yearStr, monthStr, dayStr, hourStr, minuteStr) = matchResult.destructured

                    val year = yearStr.toInt()
                    val month = monthStr.toInt()
                    val day = dayStr.toInt()
                    val hour = hourStr.toInt()
                    val minute = minuteStr.toInt()

                    // Semantic validation
                    if (month !in 1..12 || day !in 1..31 || hour !in 0..23 || minute !in 0..59) {
                        // Keep strict validation simple here. LocalDateTime.of will also throw for invalid dates (e.g. Feb 30)
                        return null
                    }

                    try {
                         LocalDateTime.of(year, month, day, hour, minute)
                    } catch (e: Exception) {
                        // Build-in java.time validation failed (e.g. Feb 30)
                        null
                    }
                }

                else -> null
            }
        } catch (e: Exception) {
            Timber.e("Error parsing target time: $e")
            null
        }
    }
}
