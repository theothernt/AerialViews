package com.neilturner.aerialviews.utils

import java.util.concurrent.TimeUnit
import kotlin.math.abs

object TimeHelper {
    // Function to calculate time ago from a given Unix timestamp
    fun calculateTimeAgo(unixTimestamp: Long): String {
        val currentTimeMillis = System.currentTimeMillis()
        val timestampMillis = unixTimestamp * 1000

        val diffMillis = abs(currentTimeMillis - timestampMillis)

        // Convert to appropriate time units
        val seconds = TimeUnit.MILLISECONDS.toSeconds(diffMillis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diffMillis)
        val hours = TimeUnit.MILLISECONDS.toHours(diffMillis)
        val days = TimeUnit.MILLISECONDS.toDays(diffMillis)

        return when {
            seconds < 60 -> "$seconds seconds ago"
            minutes < 60 -> "$minutes minutes ago"
            hours < 24 -> "$hours hours ago"
            days < 30 -> "$days days ago"
            days < 365 -> "${days / 30} months ago"
            else -> "${days / 365} years ago"
        }
    }
}
