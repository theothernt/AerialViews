package com.neilturner.aerialviews.ui.overlays.utils

import com.neilturner.aerialviews.models.enums.ClockType
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Clock text for the Compose overlay, matching what the TextClock in the XML layer shows.
 */
object ClockFormatter {
    const val PATTERN_24_HOUR = "HH:mm"
    const val PATTERN_12_HOUR = "h:mm a"

    /**
     * [is24Hour] is the system setting, which is what a TextClock falls back to for
     * ClockType.DEFAULT.
     */
    fun pattern(
        type: ClockType?,
        is24Hour: Boolean,
    ): String =
        when (type) {
            ClockType.HOUR_24 -> PATTERN_24_HOUR
            ClockType.HOUR_12 -> PATTERN_12_HOUR
            else -> if (is24Hour) PATTERN_24_HOUR else PATTERN_12_HOUR
        }

    fun format(
        time: LocalDateTime,
        pattern: String,
    ): String = DateTimeFormatter.ofPattern(pattern).format(time)

    /** Milliseconds until the displayed minute changes, so the clock ticks on the minute. */
    fun millisToNextMinute(time: LocalDateTime): Long = (60L - time.second) * 1000L - time.nano / 1_000_000L
}
