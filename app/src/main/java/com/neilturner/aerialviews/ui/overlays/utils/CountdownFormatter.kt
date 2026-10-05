package com.neilturner.aerialviews.ui.overlays.utils

import androidx.annotation.PluralsRes
import com.neilturner.aerialviews.R

enum class CountdownUnit(
    @PluralsRes val pluralsRes: Int,
) {
    DAYS(R.plurals.countdown_unit_days),
    HOURS(R.plurals.countdown_unit_hours),
    MINUTES(R.plurals.countdown_unit_minutes),
    SECONDS(R.plurals.countdown_unit_seconds),
}

data class CountdownPart(
    val unit: CountdownUnit,
    val value: Long,
)

object CountdownFormatter {
    private const val SECONDS_PER_MINUTE = 60L
    private const val SECONDS_PER_HOUR = 3600L
    private const val SECONDS_PER_DAY = 86400L

    fun parts(totalSeconds: Long): List<CountdownPart> {
        if (totalSeconds <= 0) return emptyList()

        val days = totalSeconds / SECONDS_PER_DAY
        val hours = (totalSeconds % SECONDS_PER_DAY) / SECONDS_PER_HOUR
        val minutes = (totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
        val seconds = totalSeconds % SECONDS_PER_MINUTE

        return when {
            days > 0 -> {
                listOf(
                    CountdownPart(CountdownUnit.DAYS, days),
                    CountdownPart(CountdownUnit.HOURS, hours),
                    CountdownPart(CountdownUnit.MINUTES, minutes),
                )
            }

            hours > 0 -> {
                listOf(
                    CountdownPart(CountdownUnit.HOURS, hours),
                    CountdownPart(CountdownUnit.MINUTES, minutes),
                )
            }

            minutes > 0 -> {
                listOf(
                    CountdownPart(CountdownUnit.MINUTES, minutes),
                    CountdownPart(CountdownUnit.SECONDS, seconds),
                )
            }

            else -> {
                listOf(CountdownPart(CountdownUnit.SECONDS, seconds))
            }
        }
    }
}
