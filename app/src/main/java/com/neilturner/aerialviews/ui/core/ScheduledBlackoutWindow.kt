package com.neilturner.aerialviews.ui.core

import java.time.LocalTime

/** Determines whether [now] falls within a daily blackout window. */
internal object ScheduledBlackoutWindow {
    fun contains(
        start: LocalTime,
        end: LocalTime,
        now: LocalTime,
    ): Boolean =
        if (start <= end) {
            !now.isBefore(start) && now.isBefore(end)
        } else {
            !now.isBefore(start) || now.isBefore(end)
        }
}
