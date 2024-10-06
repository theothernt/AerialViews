package com.neilturner.aerialviews.utils

import android.view.KeyEvent
import com.neilturner.aerialviews.models.enums.ButtonType
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.ui.core.ScreenController
import timber.log.Timber

object InputHelper {
    private var previousEvent: KeyEvent? = null

    fun handleKeyEvent(
        event: KeyEvent,
        controller: ScreenController?,
        exit: () -> Unit,
    ): Boolean {
        var result = false
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
            result = eventToAction(event, controller, exit, true)
        }
        previousEvent = event
        return result
    }

    private fun eventToAction(
        event: KeyEvent,
        controller: ScreenController?,
        exit: () -> Unit,
        longPress: Boolean = false,
    ): Boolean {
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
                return if (anyOkButtonActionsEnabled()) {
                    if (longPress) {
                        executeAction(GeneralPrefs.buttonOkHold, controller, exit)
                    } else {
                        executeAction(GeneralPrefs.buttonOkPress, controller, exit)
                    }
                } else if (anyDpadActionsEnabled()) {
                    executeAction(ButtonType.IGNORE, controller, exit)
                } else {
                    false
                }
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                return if (longPress) {
                    executeAction(GeneralPrefs.buttonUpHold, controller, exit)
                } else {
                    executeAction(GeneralPrefs.buttonUpPress, controller, exit)
                }
            }

            KeyEvent.KEYCODE_DPAD_DOWN -> {
                return if (longPress) {
                    executeAction(GeneralPrefs.buttonDownHold, controller, exit)
                } else {
                    executeAction(GeneralPrefs.buttonDownPress, controller, exit)
                }
            }

            KeyEvent.KEYCODE_DPAD_LEFT -> {
                return if (longPress) {
                    executeAction(GeneralPrefs.buttonLeftHold, controller, exit)
                } else {
                    executeAction(GeneralPrefs.buttonLeftPress, controller, exit)
                }
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                return if (longPress) {
                    executeAction(GeneralPrefs.buttonRightHold, controller, exit)
                } else {
                    executeAction(GeneralPrefs.buttonRightPress, controller, exit)
                }
            }

            // Any other button press will close the screensaver
            else -> exit()
        }
        return false
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
        type: ButtonType?,
        controller: ScreenController?,
        exit: () -> Unit,
    ): Boolean {
        // Check if any direction/button press should wake from black out mode
        if (GeneralPrefs.wakeOnAnyButtonPress &&
            controller?.blackOutMode == true
        ) {
            controller.toggleBlackOutMode()
            return true
        }

        when (type) {
            ButtonType.IGNORE -> return false
            ButtonType.SKIP_NEXT -> controller?.skipItem()
            ButtonType.SKIP_PREVIOUS -> controller?.skipItem(true)
            ButtonType.SPEED_INCREASE -> controller?.increaseSpeed()
            ButtonType.SPEED_DECREASE -> controller?.decreaseSpeed()
            ButtonType.SHOW_OVERLAYS -> controller?.showOverlays()
            ButtonType.BLACK_OUT_MODE -> controller?.toggleBlackOutMode()
            else -> exit()
        }
        return true
    }
}
