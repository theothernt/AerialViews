package com.neilturner.aerialviews.ui.controls

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class TimeOfDayBoundaryAdapterTest {
    private fun rows() =
        mutableListOf(
            TimeOfDayRow.Header("Day"),
            TimeOfDayRow.Option(TimeOfDayGroup.DAY, "06:00", "06:00", isSelected = true),
            TimeOfDayRow.Option(TimeOfDayGroup.DAY, "12:00", "12:00"),
            TimeOfDayRow.Header("Night"),
            TimeOfDayRow.Option(TimeOfDayGroup.NIGHT, "20:00", "20:00", isSelected = true),
            TimeOfDayRow.Option(TimeOfDayGroup.NIGHT, "22:00", "22:00", isSelected = true),
        )

    @Test
    fun `item count includes headers and options`() {
        assertEquals(6, TimeOfDayBoundaryAdapter(rows()).itemCount)
    }

    @Test
    fun `headers and options have distinct view types`() {
        val adapter = TimeOfDayBoundaryAdapter(rows())
        assertEquals(adapter.getItemViewType(0), adapter.getItemViewType(3))
        assertEquals(adapter.getItemViewType(1), adapter.getItemViewType(2))
        assertNotEquals(adapter.getItemViewType(0), adapter.getItemViewType(1))
    }

    @Test
    fun `selected values are returned per group`() {
        val adapter = TimeOfDayBoundaryAdapter(rows())
        assertEquals(setOf("06:00"), adapter.getSelectedFor(TimeOfDayGroup.DAY))
        assertEquals(setOf("20:00", "22:00"), adapter.getSelectedFor(TimeOfDayGroup.NIGHT))
    }

    @Test
    fun `headers are ignored when collecting selections`() {
        val adapter = TimeOfDayBoundaryAdapter(mutableListOf(TimeOfDayRow.Header("Day")))
        assertTrue(adapter.getSelectedFor(TimeOfDayGroup.DAY).isEmpty())
    }

    @Test
    fun `duplicate selected values are collapsed`() {
        val adapter =
            TimeOfDayBoundaryAdapter(
                mutableListOf(
                    TimeOfDayRow.Option(TimeOfDayGroup.DAY, "06:00", "First", isSelected = true),
                    TimeOfDayRow.Option(TimeOfDayGroup.DAY, "06:00", "Second", isSelected = true),
                ),
            )
        assertEquals(setOf("06:00"), adapter.getSelectedFor(TimeOfDayGroup.DAY))
    }

    @Test
    fun `selection changes are reflected without rebuilding the adapter`() {
        val rows = rows()
        val adapter = TimeOfDayBoundaryAdapter(rows)
        val option = rows[2] as TimeOfDayRow.Option

        assertTrue(adapter.getSelectedFor(TimeOfDayGroup.DAY) == setOf("06:00"))
        option.isSelected = true
        assertEquals(setOf("06:00", "12:00"), adapter.getSelectedFor(TimeOfDayGroup.DAY))
    }
}
