package com.neilturner.aerialviews.utils

import android.content.Context
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
        exit: (shouldExit: Boolean) -> Unit,
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
            result = eventToAction(event, controller, exit)
        }

        if (event.action == KeyEvent.ACTION_DOWN &&
            event.isLongPress
        ) {
            result = eventToAction(event, controller, exit, ButtonPressType.LONG_PRESS)
            longPressEvent = true
        }

        if (event.action == KeyEvent.ACTION_DOWN &&
            event.repeatCount.rem(10) == 0 &&
            longPressEvent
        ) {
            result = eventToAction(event, controller, exit, ButtonPressType.LONG_PRESS_HOLD)
        }

        previousEvent = event
        return result
    }

    fun setupGestureListener(
        context: Context,
        controller: ScreenController,
        exit: (shouldExit: Boolean) -> Unit,
    ) {
        controller
            .view
            .setOnTouchListener(
                SwipeGestureListener(
                    context = context,
                    onSwipeUp = { gestureToAction(GestureType.UP, controller, exit) },
                    onSwipeDown = { gestureToAction(GestureType.DOWN, controller, exit) },
                    onSwipeLeft = { gestureToAction(GestureType.LEFT, controller, exit) },
                    onSwipeRight = { gestureToAction(GestureType.RIGHT, controller, exit) },
                    onTap = { gestureToAction(GestureType.TAP, controller, exit) },
                    onDoubleTap = { gestureToAction(GestureType.DOUBLE_TAP, controller, exit) },
                    onLongTap = { gestureToAction(GestureType.TAP_HOLD, controller, exit) },
                ),
            )
    }

    private fun gestureToAction(
        gesture: GestureType,
        controller: ScreenController?,
        exit: (shouldExit: Boolean) -> Unit,
    ) {
        val action =
            when (gesture) {
                GestureType.UP -> GeneralPrefs.gestureUp
                GestureType.DOWN -> GeneralPrefs.gestureDown
                GestureType.LEFT -> GeneralPrefs.gestureLeft
                GestureType.RIGHT -> GeneralPrefs.gestureRight
                GestureType.TAP -> GeneralPrefs.gestureTap
                GestureType.DOUBLE_TAP -> GeneralPrefs.gestureDoubleTap
                GestureType.TAP_HOLD -> GeneralPrefs.gestureTapHold
            }

        Timber.i("Gesture: $gesture, Action: $action")

        // Check if any swipe or screen tap should wake from black out mode
        if (GeneralPrefs.wakeOnAnyButtonPress &&
            controller?.blackOutMode == true
        ) {
            controller.toggleBlackOutMode()
            return
        }

        if (action == ButtonType.IGNORE) {
            return
        }

        when (action) {
            ButtonType.SKIP_NEXT -> controller?.skipItem()
            ButtonType.SKIP_PREVIOUS -> controller?.skipItem(true)
            ButtonType.SPEED_INCREASE -> controller?.increaseSpeed()
            ButtonType.MUSIC_NEXT -> controller?.nextTrack()
            ButtonType.MUSIC_PREVIOUS -> controller?.previousTrack()
            ButtonType.SPEED_DECREASE -> controller?.decreaseSpeed()
            ButtonType.SEEK_FORWARD -> controller?.seekForward()
            ButtonType.SEEK_BACKWARD -> controller?.seekBackward()
            ButtonType.SHOW_OVERLAYS -> controller?.showOverlays()
            ButtonType.BLACK_OUT_MODE -> controller?.toggleBlackOutMode()
            ButtonType.TOGGLE_MUTE -> controller?.toggleMute()
            ButtonType.TOGGLE_PAUSE -> controller?.togglePause()
            ButtonType.TOGGLE_LOOPING -> controller?.toggleLooping()
            ButtonType.BRIGHTNESS_INCREASE -> controller?.increaseBrightness()
            ButtonType.BRIGHTNESS_DECREASE -> controller?.decreaseBrightness()
            ButtonType.EXIT_TO_SETTINGS -> exit(false)
            else -> exit(true)
        }
    }

    private fun eventToAction(
        event: KeyEvent,
        controller: ScreenController?,
        exit: (shouldExit: Boolean) -> Unit,
        type: ButtonPressType = ButtonPressType.PRESS,
    ): Boolean {
        var action: ButtonType?

        when (event.keyCode) {
            // Ignore diagonal direction presses
            KeyEvent.KEYCODE_DPAD_DOWN_LEFT,
            KeyEvent.KEYCODE_DPAD_UP_LEFT,
            KeyEvent.KEYCODE_DPAD_DOWN_RIGHT,
            KeyEvent.KEYCODE_DPAD_UP_RIGHT,
            -> {
                return true
            }

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

            KeyEvent.KEYCODE_BACK -> {
                action = ButtonType.EXIT
            }

            KeyEvent.KEYCODE_DPAD_CENTER -> {
                // Only disable OK button if left/right/up/down keys are in use
                action =
                    if (anyOkButtonActionsEnabled()) {
                        if (type == ButtonPressType.PRESS) {
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
                    if (type == ButtonPressType.PRESS) {
                        GeneralPrefs.buttonUpPress
                    } else {
                        GeneralPrefs.buttonUpHold
                    }
            }

            KeyEvent.KEYCODE_DPAD_DOWN -> {
                action =
                    if (type == ButtonPressType.PRESS) {
                        GeneralPrefs.buttonDownPress
                    } else {
                        GeneralPrefs.buttonDownHold
                    }
            }

            KeyEvent.KEYCODE_DPAD_LEFT -> {
                action =
                    if (type == ButtonPressType.PRESS) {
                        GeneralPrefs.buttonLeftPress
                    } else {
                        GeneralPrefs.buttonLeftHold
                    }
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                action =
                    if (type == ButtonPressType.PRESS) {
                        GeneralPrefs.buttonRightPress
                    } else {
                        GeneralPrefs.buttonRightHold
                    }
            }

            // Any other button press will close the screensaver
            else -> {
                return false
            }
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
        exit: (shouldExit: Boolean) -> Unit,
        type: ButtonPressType,
    ): Boolean {
        Timber.i("Action: $action, ButtonPressType: $type")

        // Check if any direction/button press should wake from black out mode
        if (GeneralPrefs.wakeOnAnyButtonPress &&
            controller?.blackOutMode == true &&
            type != ButtonPressType.LONG_PRESS_HOLD
        ) {
            // Timber.i("Action: toggleBlackOutMode")
            controller.toggleBlackOutMode()
            return true
        }

        if (action == ButtonType.IGNORE) {
            // Timber.i("Action: Ignore")
            return false
        }

        // If black out mode is active, should ignore all other actions?

        if (type == ButtonPressType.LONG_PRESS_HOLD) {
            // Timber.i("Action: Ignore")
            return false
        } else {
            // Timber.i("Action: $action")
            when (action) {
                ButtonType.SKIP_NEXT -> controller?.skipItem()
                ButtonType.SKIP_PREVIOUS -> controller?.skipItem(true)
                ButtonType.SPEED_INCREASE -> controller?.increaseSpeed()
                ButtonType.MUSIC_NEXT -> controller?.nextTrack()
                ButtonType.MUSIC_PREVIOUS -> controller?.previousTrack()
                ButtonType.SPEED_DECREASE -> controller?.decreaseSpeed()
                ButtonType.SEEK_FORWARD -> controller?.seekForward()
                ButtonType.SEEK_BACKWARD -> controller?.seekBackward()
                ButtonType.SHOW_OVERLAYS -> controller?.showOverlays()
                ButtonType.BLACK_OUT_MODE -> controller?.toggleBlackOutMode()
                ButtonType.TOGGLE_MUTE -> controller?.toggleMute()
                ButtonType.TOGGLE_PAUSE -> controller?.togglePause()
                ButtonType.EXIT_TO_SETTINGS -> exit(false)
                else -> exit(true)
            }
        }
        return true
    }
}

enum class ButtonPressType {
    PRESS,
    LONG_PRESS, // eg. 1 second
    LONG_PRESS_HOLD, // eg. 5 seconds - for seeking, etc
}

enum class GestureType {
    UP,
    DOWN,
    LEFT,
    RIGHT,
    TAP,
    DOUBLE_TAP,
    TAP_HOLD,
}
