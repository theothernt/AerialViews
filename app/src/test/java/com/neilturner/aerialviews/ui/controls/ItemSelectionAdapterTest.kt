package com.neilturner.aerialviews.ui.controls

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.spyk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ItemSelectionAdapterTest {
    private fun items() =
        mutableListOf(
            SelectableItem("a", "Alpha"),
            SelectableItem("b", "Bravo"),
            SelectableItem("c", "Charlie"),
        )

    // RecyclerView notifications reach android.database.Observable, which is a stub in JVM
    // unit tests. Spy the adapter so the real move logic runs but notification calls are no-ops.
    private fun adapter(items: MutableList<SelectableItem>) =
        spyk(ItemSelectionAdapter(items, {}, {}, {})) {
            every { notifyItemMoved(any(), any()) } just Runs
            every { notifyItemChanged(any()) } just Runs
        }

    @Test
    fun `item count follows the backing list`() {
        val items = items()
        assertEquals(3, adapter(items).itemCount)
        items.add(SelectableItem("d", "Delta"))
        assertEquals(4, adapter(items).itemCount)
    }

    @Test
    fun `move up swaps with the preceding item`() {
        val items = items()
        adapter(items).moveUp(1)

        assertEquals(listOf("b", "a", "c"), items.map { it.id })
    }

    @Test
    fun `move down swaps with the following item`() {
        val items = items()
        adapter(items).moveDown(1)

        assertEquals(listOf("a", "c", "b"), items.map { it.id })
    }

    @Test
    fun `move up at the top is a no-op`() {
        val items = items()
        adapter(items).moveUp(0)

        assertEquals(listOf("a", "b", "c"), items.map { it.id })
    }

    @Test
    fun `move down at the bottom is a no-op`() {
        val items = items()
        adapter(items).moveDown(2)

        assertEquals(listOf("a", "b", "c"), items.map { it.id })
    }

    @Test
    fun `negative positions are ignored`() {
        val items = items()
        val adapter = adapter(items)
        adapter.moveUp(-1)
        adapter.moveDown(-1)

        assertEquals(listOf("a", "b", "c"), items.map { it.id })
    }

    @Test
    fun `consecutive moves keep every item exactly once`() {
        val items = items()
        val adapter = adapter(items)
        adapter.moveUp(2)
        adapter.moveUp(1)
        adapter.moveDown(0)

        assertEquals(listOf("a", "c", "b"), items.map { it.id })
        assertEquals(setOf("a", "b", "c"), items.map { it.id }.toSet())
        assertTrue(items.size == 3)
    }
}
