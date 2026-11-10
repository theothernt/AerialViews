package com.neilturner.aerialviews.ui.helpers

import android.view.KeyEvent
import com.neilturner.aerialviews.models.enums.ButtonType

enum class KeyAction {
    DOWN,
    UP,
    OTHER,
}

/** A framework-independent key event for [KeyEventMapper]. */
data class KeyInput(
    val keyCode: Int,
    val action: KeyAction,
    val repeatCount: Int,
    val isLongPress: Boolean,
)

/** The button bindings needed to turn a key event into an action. */
data class KeyBindings(
    val okPress: ButtonType? = null,
    val okHold: ButtonType? = null,
    val upPress: ButtonType? = null,
    val upHold: ButtonType? = null,
    val downPress: ButtonType? = null,
    val downHold: ButtonType? = null,
    val leftPress: ButtonType? = null,
    val leftHold: ButtonType? = null,
    val rightPress: ButtonType? = null,
    val rightHold: ButtonType? = null,
    val mediaButtonPassthrough: Boolean = true,
)

/** The action and press type a key event resolved to, plus whether it was consumed. */
data class KeyMapping(
    val action: ButtonType?,
    val pressType: ButtonPressType = ButtonPressType.PRESS,
    val handled: Boolean,
)

/**
 * The key-event state machine used by both the legacy screensaver and the future Compose path.
 *
 * A normal press is delivered on ACTION_UP, provided the key was not held long enough to repeat.
 * Long presses are delivered on ACTION_DOWN; once a long press has started, every tenth repeat is
 * a held action. The ACTION_UP that follows a repeated key is deliberately suppressed.
 */
class KeyEventMapper {
    private var previousEvent: KeyInput? = null
    private var longPressEvent = false

    fun reset() {
        previousEvent = null
        longPressEvent = false
    }

    fun map(
        event: KeyInput,
        bindings: KeyBindings,
    ): KeyMapping {
        if (event.repeatCount == 0) {
            longPressEvent = false
        }

        var mapping: KeyMapping? = null

        if (event.action == KeyAction.UP &&
            (previousEvent == null || previousEvent?.repeatCount == 0)
        ) {
            mapping = eventToAction(event, bindings, ButtonPressType.PRESS)
        }

        if (event.action == KeyAction.DOWN && event.isLongPress) {
            mapping = eventToAction(event, bindings, ButtonPressType.LONG_PRESS)
            longPressEvent = true
        }

        if (event.action == KeyAction.DOWN &&
            event.repeatCount.rem(10) == 0 &&
            longPressEvent
        ) {
            mapping = eventToAction(event, bindings, ButtonPressType.LONG_PRESS_HOLD)
        }

        previousEvent = event
        return mapping ?: KeyMapping(null, handled = false)
    }

    private fun eventToAction(
        event: KeyInput,
        bindings: KeyBindings,
        type: ButtonPressType,
    ): KeyMapping =
        when (event.keyCode) {
            // Ignore diagonal direction presses
            KeyEvent.KEYCODE_DPAD_DOWN_LEFT,
            KeyEvent.KEYCODE_DPAD_UP_LEFT,
            KeyEvent.KEYCODE_DPAD_DOWN_RIGHT,
            KeyEvent.KEYCODE_DPAD_UP_RIGHT,
            -> {
                KeyMapping(null, type, handled = true)
            }

            // Media keys can be passed through to the background app.
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            KeyEvent.KEYCODE_MEDIA_REWIND,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_NEXT,
            KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            -> {
                KeyMapping(null, type, handled = !bindings.mediaButtonPassthrough)
            }

            KeyEvent.KEYCODE_BACK -> {
                execute(ButtonType.EXIT, type)
            }

            KeyEvent.KEYCODE_DPAD_CENTER -> {
                when {
                    anyOkButtonActionsEnabled(bindings) -> {
                        execute(
                            if (type == ButtonPressType.PRESS) bindings.okPress else bindings.okHold,
                            type,
                        )
                    }

                    anyDpadActionsEnabled(bindings) -> {
                        execute(ButtonType.IGNORE, type)
                    }

                    else -> {
                        KeyMapping(null, type, handled = false)
                    }
                }
            }

            KeyEvent.KEYCODE_DPAD_UP -> {
                execute(
                    if (type == ButtonPressType.PRESS) bindings.upPress else bindings.upHold,
                    type,
                )
            }

            KeyEvent.KEYCODE_DPAD_DOWN -> {
                execute(
                    if (type == ButtonPressType.PRESS) bindings.downPress else bindings.downHold,
                    type,
                )
            }

            KeyEvent.KEYCODE_DPAD_LEFT -> {
                execute(
                    if (type == ButtonPressType.PRESS) bindings.leftPress else bindings.leftHold,
                    type,
                )
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                execute(
                    if (type == ButtonPressType.PRESS) bindings.rightPress else bindings.rightHold,
                    type,
                )
            }

            else -> {
                KeyMapping(null, type, handled = false)
            }
        }

    private fun execute(
        action: ButtonType?,
        type: ButtonPressType,
    ): KeyMapping =
        if (action == null) {
            KeyMapping(null, type, handled = false)
        } else {
            KeyMapping(action, type, handled = true)
        }

    private fun anyDpadActionsEnabled(bindings: KeyBindings): Boolean =
        bindings.upPress != ButtonType.IGNORE ||
            bindings.downPress != ButtonType.IGNORE ||
            bindings.leftPress != ButtonType.IGNORE ||
            bindings.rightPress != ButtonType.IGNORE ||
            bindings.upHold != ButtonType.IGNORE ||
            bindings.downHold != ButtonType.IGNORE ||
            bindings.leftHold != ButtonType.IGNORE ||
            bindings.rightHold != ButtonType.IGNORE

    private fun anyOkButtonActionsEnabled(bindings: KeyBindings): Boolean =
        bindings.okPress != ButtonType.IGNORE ||
            bindings.okHold != ButtonType.IGNORE
}
