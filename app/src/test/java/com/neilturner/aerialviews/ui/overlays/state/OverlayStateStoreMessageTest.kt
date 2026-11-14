package com.neilturner.aerialviews.ui.overlays.state

import com.neilturner.aerialviews.models.enums.OverlayType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

/**
 * The message state's identity.
 *
 * `StateFlow` dedupes equal states, so a re-post of the same text and duration used to be dropped
 * and the auto-clear timer never restarted. Every message now carries a fresh id.
 */
class OverlayStateStoreMessageTest {
    @Test
    fun `a repeated message gets a new id so the auto clear timer restarts`() {
        val store = OverlayStateStore()

        store.setMessage(OverlayType.MESSAGE1, MessageOverlayState(text = "Hi", duration = 5))
        val first =
            store.uiState.value.message
                .getValue(OverlayType.MESSAGE1)

        store.setMessage(OverlayType.MESSAGE1, MessageOverlayState(text = "Hi", duration = 5))
        val second =
            store.uiState.value.message
                .getValue(OverlayType.MESSAGE1)

        assertNotEquals(first.id, second.id)
        assertEquals("Hi", second.text)
    }

    @Test
    fun `different message slots get independent ids`() {
        val store = OverlayStateStore()

        store.setMessage(OverlayType.MESSAGE1, MessageOverlayState(text = "One"))
        store.setMessage(OverlayType.MESSAGE2, MessageOverlayState(text = "Two"))

        val one =
            store.uiState.value.message
                .getValue(OverlayType.MESSAGE1)
        val two =
            store.uiState.value.message
                .getValue(OverlayType.MESSAGE2)

        assertNotEquals(one.id, two.id)
    }
}
