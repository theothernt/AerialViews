package com.neilturner.aerialviews.ui.helpers

import android.view.KeyEvent
import com.neilturner.aerialviews.models.enums.ButtonType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KeyEventMapperTest {
    private val bindings =
        KeyBindings(
            okPress = ButtonType.SKIP_NEXT,
            okHold = ButtonType.TOGGLE_PAUSE,
            upPress = ButtonType.IGNORE,
            downPress = ButtonType.IGNORE,
            leftPress = ButtonType.SKIP_PREVIOUS,
            rightPress = ButtonType.SKIP_NEXT,
        )

    @Test
    fun `a normal press is delivered on key up`() {
        val mapper = KeyEventMapper()

        val down = mapper.map(key(KeyEvent.KEYCODE_DPAD_LEFT, KeyAction.DOWN), bindings)
        assertFalse(down.handled)

        val up = mapper.map(key(KeyEvent.KEYCODE_DPAD_LEFT, KeyAction.UP), bindings)
        assertTrue(up.handled)
        assertEquals(ButtonType.SKIP_PREVIOUS, up.action)
        assertEquals(ButtonPressType.PRESS, up.pressType)
    }

    @Test
    fun `long press and hold repeats produce hold actions`() {
        val mapper = KeyEventMapper()

        val longPress =
            mapper.map(
                key(KeyEvent.KEYCODE_DPAD_CENTER, KeyAction.DOWN, isLongPress = true),
                bindings,
            )
        assertEquals(ButtonType.TOGGLE_PAUSE, longPress.action)
        // Legacy evaluates the repeat-zero hold branch after the long-press branch, so the
        // initial long-press event is reported as a held press too.
        assertEquals(ButtonPressType.LONG_PRESS_HOLD, longPress.pressType)

        val hold =
            mapper.map(
                key(KeyEvent.KEYCODE_DPAD_CENTER, KeyAction.DOWN, repeatCount = 10),
                bindings,
            )
        assertEquals(ButtonType.TOGGLE_PAUSE, hold.action)
        assertEquals(ButtonPressType.LONG_PRESS_HOLD, hold.pressType)
    }

    @Test
    fun `a repeated key's release is suppressed`() {
        val mapper = KeyEventMapper()
        mapper.map(key(KeyEvent.KEYCODE_DPAD_RIGHT, KeyAction.DOWN), bindings)
        mapper.map(key(KeyEvent.KEYCODE_DPAD_RIGHT, KeyAction.DOWN, repeatCount = 1), bindings)

        val suppressed = mapper.map(key(KeyEvent.KEYCODE_DPAD_RIGHT, KeyAction.UP), bindings)
        assertFalse(suppressed.handled)
        assertNull(suppressed.action)

        mapper.map(key(KeyEvent.KEYCODE_DPAD_RIGHT, KeyAction.DOWN), bindings)
        val nextPress = mapper.map(key(KeyEvent.KEYCODE_DPAD_RIGHT, KeyAction.UP), bindings)
        assertEquals(ButtonType.SKIP_NEXT, nextPress.action)
    }

    @Test
    fun `ok falls back to ignore when only dpad actions are configured`() {
        val mapper = KeyEventMapper()
        val onlyDpad = bindings.copy(okPress = ButtonType.IGNORE, okHold = ButtonType.IGNORE)

        val mapping = mapper.map(key(KeyEvent.KEYCODE_DPAD_CENTER, KeyAction.UP), onlyDpad)
        assertTrue(mapping.handled)
        assertEquals(ButtonType.IGNORE, mapping.action)
    }

    @Test
    fun `unmapped ok is not consumed when no dpad action is configured`() {
        val mapper = KeyEventMapper()
        val noActions =
            bindings.copy(
                okPress = ButtonType.IGNORE,
                okHold = ButtonType.IGNORE,
                upPress = ButtonType.IGNORE,
                upHold = ButtonType.IGNORE,
                downPress = ButtonType.IGNORE,
                downHold = ButtonType.IGNORE,
                leftPress = ButtonType.IGNORE,
                leftHold = ButtonType.IGNORE,
                rightPress = ButtonType.IGNORE,
                rightHold = ButtonType.IGNORE,
            )

        val mapping = mapper.map(key(KeyEvent.KEYCODE_DPAD_CENTER, KeyAction.UP), noActions)
        assertFalse(mapping.handled)
        assertNull(mapping.action)
    }

    @Test
    fun `media passthrough and diagonal keys preserve their consumed state`() {
        val mapper = KeyEventMapper()
        val mediaEvent = key(KeyEvent.KEYCODE_MEDIA_PLAY, KeyAction.UP)

        assertFalse(mapper.map(mediaEvent, bindings).handled)
        assertTrue(mapper.map(mediaEvent, bindings.copy(mediaButtonPassthrough = false)).handled)
        assertTrue(mapper.map(key(KeyEvent.KEYCODE_DPAD_UP_LEFT, KeyAction.UP), bindings).handled)
    }

    private fun key(
        keyCode: Int,
        action: KeyAction,
        repeatCount: Int = 0,
        isLongPress: Boolean = false,
    ) = KeyInput(keyCode, action, repeatCount, isLongPress)
}
