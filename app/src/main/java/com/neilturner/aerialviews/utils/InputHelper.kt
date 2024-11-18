package com.neilturner.aerialviews.utils

import android.view.KeyEvent
import com.neilturner.aerialviews.models.enums.ButtonType
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.ui.core.ScreenController
import timber.log.Timber

object InputHelper {
    private var previousEvent: KeyEvent? = null
    private var longPressEvent = false

    fun handleKeyEvent(
        event: KeyEvent,
        controller: ScreenController?,
        exit: () -> Unit,
    ): Boolean {
        var result = false

        if (event.repeatCount == 0) {
            longPressEvent = false
        }

        if (event.action == KeyEvent.ACTION_UP &&
            (
                previousEvent == null ||
                    previousEvent?.repeatCount == 0
            )
        ) {
            Timber.i("Key Up")
            result = eventToAction(event, controller, exit)
        }

        if (event.action == KeyEvent.ACTION_DOWN &&
            event.isLongPress
        ) {
            Timber.i("Long Press")
            result = eventToAction(event, controller, exit, ButtonPressType.LONG_PRESS)
            longPressEvent = true
        }

        if (event.action == KeyEvent.ACTION_DOWN &&
            event.repeatCount.rem(10) == 0 &&
            longPressEvent
        ) {
            Timber.i("Another Long Press")
            result = eventToAction(event, controller, exit, ButtonPressType.LONG_PRESS_HOLD)
        }

        previousEvent = event
        return result
    }

    private fun eventToAction(
        event: KeyEvent,
        controller: ScreenController?,
        exit: () -> Unit,
        type: ButtonPressType = ButtonPressType.NORMAL,
    ): Boolean {
        var action: ButtonType? = null

        when (event.keyCode) {
            // Ignore diagonal direction presses
            KeyEvent.KEYCODE_DPAD_DOWN_LEFT,
            KeyEvent.KEYCODE_DPAD_UP_LEFT,
            KeyEvent.KEYCODE_DPAD_DOWN_RIGHT,
            KeyEvent.KEYCODE_DPAD_UP_RIGHT,
            -> return true

            // Media keys
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            KeyEvent.KEYCODE_MEDIA_REWIND,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_NEXT,
            KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            -> {
                // Should play/pause/rewind keys be passed
                // to the background app or not
                return !GeneralPrefs.enableMediaButtonPassthrough
            }

            KeyEvent.KEYCODE_DPAD_CENTER -> {
                // Only disable OK button if left/right/up/down keys are in use
                action =
                    if (anyOkButtonActionsEnabled()) {
                        if (type == ButtonPressType.NORMAL) {
                            GeneralPrefs.buttonOkPress
                        } else {
                            GeneralPrefs.buttonOkHold
                        }
                    } else if (anyDpadActionsEnabled()) {
                        ButtonType.IGNORE
                    } else {
                        return false
                    }
            }

            KeyEvent.KEYCODE_DPAD_UP -> {
                action =
                    if (type == ButtonPressType.NORMAL) {
                        GeneralPrefs.buttonUpPress
                    } else {
                        GeneralPrefs.buttonUpHold
                    }
            }

            KeyEvent.KEYCODE_DPAD_DOWN -> {
                action =
                    if (type == ButtonPressType.NORMAL) {
                        GeneralPrefs.buttonDownPress
                    } else {
                        GeneralPrefs.buttonDownHold
                    }
            }

            KeyEvent.KEYCODE_DPAD_LEFT -> {
                action =
                    if (type == ButtonPressType.NORMAL) {
                        GeneralPrefs.buttonLeftPress
                    } else {
                        GeneralPrefs.buttonLeftHold
                    }
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                action =
                    if (type == ButtonPressType.NORMAL) {
                        GeneralPrefs.buttonRightPress
                    } else {
                        GeneralPrefs.buttonRightHold
                    }
            }

            // Any other button press will close the screensaver
            else -> exit()
        }

        if (action == null) {
            return false
        }

        return executeAction(action, controller, exit, type)
    }

    private fun anyDpadActionsEnabled(): Boolean =
        GeneralPrefs.buttonUpPress != ButtonType.IGNORE ||
            GeneralPrefs.buttonDownPress != ButtonType.IGNORE ||
            GeneralPrefs.buttonLeftPress != ButtonType.IGNORE ||
            GeneralPrefs.buttonRightPress != ButtonType.IGNORE ||
            GeneralPrefs.buttonUpHold != ButtonType.IGNORE ||
            GeneralPrefs.buttonDownHold != ButtonType.IGNORE ||
            GeneralPrefs.buttonLeftHold != ButtonType.IGNORE ||
            GeneralPrefs.buttonRightHold != ButtonType.IGNORE

    private fun anyOkButtonActionsEnabled(): Boolean =
        GeneralPrefs.buttonOkPress != ButtonType.IGNORE ||
            GeneralPrefs.buttonOkHold != ButtonType.IGNORE

    private fun executeAction(
        action: ButtonType?,
        controller: ScreenController?,
        exit: () -> Unit,
        type: ButtonPressType,
    ): Boolean {
        // Check if any direction/button press should wake from black out mode
        if (GeneralPrefs.wakeOnAnyButtonPress &&
            controller?.blackOutMode == true &&
            type != ButtonPressType.LONG_PRESS_HOLD
        ) {
            controller.toggleBlackOutMode()
            return true
        }

        if (action == ButtonType.IGNORE) {
            return false
        }

        // If black out mode is active, should ignore all other actions?

        if (type == ButtonPressType.LONG_PRESS_HOLD) {
            when (action) {
                ButtonType.SEEK_FORWARD -> controller?.seekVideo()
                ButtonType.SEEK_BACKWARD -> controller?.seekVideo(true)
                else -> exit()
            }
        } else {
            when (action) {
                ButtonType.SKIP_NEXT -> controller?.skipItem()
                ButtonType.SKIP_PREVIOUS -> controller?.skipItem(true)
                ButtonType.SPEED_INCREASE -> controller?.increaseSpeed()
                ButtonType.SPEED_DECREASE -> controller?.decreaseSpeed()
                ButtonType.SHOW_OVERLAYS -> controller?.showOverlays()
                ButtonType.BLACK_OUT_MODE -> controller?.toggleBlackOutMode()
                else -> exit()
            }
        }
        return true
    }
}

enum class ButtonPressType {
    NORMAL,
    LONG_PRESS,
    LONG_PRESS_HOLD,
}
