package com.neilturner.aerialviews.ui.core

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalTime

internal class ScheduledBlackoutWindowTest {
    @Test
    fun `contains time for an overnight window`() {
        val start = LocalTime.of(22, 0)
        val end = LocalTime.of(8, 0)

        assertTrue(ScheduledBlackoutWindow.contains(start, end, LocalTime.of(22, 0)))
        assertTrue(ScheduledBlackoutWindow.contains(start, end, LocalTime.of(2, 0)))
        assertTrue(ScheduledBlackoutWindow.contains(start, end, LocalTime.of(7, 59)))
        assertFalse(ScheduledBlackoutWindow.contains(start, end, LocalTime.of(8, 0)))
        assertFalse(ScheduledBlackoutWindow.contains(start, end, LocalTime.of(21, 59)))
    }

    @Test
    fun `contains time for a same-day window`() {
        val start = LocalTime.of(9, 0)
        val end = LocalTime.of(17, 0)

        assertTrue(ScheduledBlackoutWindow.contains(start, end, LocalTime.of(9, 0)))
        assertFalse(ScheduledBlackoutWindow.contains(start, end, LocalTime.of(17, 0)))
    }
}
