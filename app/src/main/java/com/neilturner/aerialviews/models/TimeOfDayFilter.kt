package com.neilturner.aerialviews.models

import com.neilturner.aerialviews.models.enums.AerialMediaType
import com.neilturner.aerialviews.models.enums.TimeOfDay
import com.neilturner.aerialviews.models.videos.AerialMedia

/**
 * The effective time-of-day view of a playlist.
 *
 * Videos are filtered down to [allowed]; photos and any future non-video types always pass.
 * A null [period] means filtering is disabled and [allowed] contains every category.
 *
 * The same [allowed] set is used by the in-memory filter and by the playlist cache SQL predicate,
 * so the two paths cannot drift apart.
 */
data class TimeOfDayFilter(
    val period: TimeOfDay?,
    val allowed: Set<TimeOfDay>,
) {
    val enabled: Boolean get() = period != null

    /** Enum names in the form used by the SQL predicate. */
    fun allowedNames(): List<String> = allowed.map { it.name }

    fun includes(media: AerialMedia): Boolean = media.type != AerialMediaType.VIDEO || media.metadata.timeOfDay in allowed

    /**
     * Applies the filter in memory, mirroring the SQL predicate. If nothing matches, the original
     * list is returned: hiding the entire library is worse than showing the wrong time of day.
     */
    fun apply(media: List<AerialMedia>): List<AerialMedia> {
        if (!enabled) return media
        val filtered = media.filter(::includes)
        return if (filtered.none { it.type == AerialMediaType.VIDEO }) media else filtered
    }

    companion object {
        val ALL: TimeOfDayFilter = TimeOfDayFilter(period = null, allowed = TimeOfDay.entries.toSet())

        fun forPeriod(
            period: TimeOfDay,
            dayIncludes: Set<String>,
            nightIncludes: Set<String>,
        ): TimeOfDayFilter {
            val allowed =
                buildSet {
                    add(TimeOfDay.UNKNOWN)
                    when (period) {
                        TimeOfDay.DAY -> {
                            add(TimeOfDay.DAY)
                            if (SUNRISE in dayIncludes) add(TimeOfDay.SUNRISE)
                            if (SUNSET in dayIncludes) add(TimeOfDay.SUNSET)
                        }

                        TimeOfDay.NIGHT -> {
                            add(TimeOfDay.NIGHT)
                            if (SUNRISE in nightIncludes) add(TimeOfDay.SUNRISE)
                            if (SUNSET in nightIncludes) add(TimeOfDay.SUNSET)
                        }

                        // Should not happen: the current period is always DAY or NIGHT.
                        else -> {
                            addAll(TimeOfDay.entries)
                        }
                    }
                }
            return TimeOfDayFilter(period, allowed)
        }

        private const val SUNRISE = "SUNRISE"
        private const val SUNSET = "SUNSET"
    }
}
