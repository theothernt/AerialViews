package com.neilturner.aerialviews.utils

import android.content.Context
import android.view.KeyEvent
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.ButtonType
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.ui.core.ScreenInteractionHandler
import android.view.View
import timber.log.Timber

object InputHelper {
    private var previousEvent: KeyEvent? = null
    private var longPressEvent = false

    fun handleKeyEvent(
        event: KeyEvent,
        handler: ScreenInteractionHandler?,
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
            result = eventToAction(event, handler, exit)
        }

        if (event.action == KeyEvent.ACTION_DOWN &&
            event.isLongPress
        ) {
            result = eventToAction(event, handler, exit, ButtonPressType.LONG_PRESS)
            longPressEvent = true
        }

        if (event.action == KeyEvent.ACTION_DOWN &&
            event.repeatCount.rem(10) == 0 &&
            longPressEvent
        ) {
            result = eventToAction(event, handler, exit, ButtonPressType.LONG_PRESS_HOLD)
        }

        previousEvent = event
        return result
    }

    fun setupGestureListener(
        context: Context,
        view: View,
        handler: ScreenInteractionHandler,
        exit: (shouldExit: Boolean) -> Unit,
    ) {
        view
            .setOnTouchListener(
                SwipeGestureListener(
                    context = context,
                    onSwipeUp = { gestureToAction(GestureType.UP, handler, exit) },
                    onSwipeDown = { gestureToAction(GestureType.DOWN, handler, exit) },
                    onSwipeLeft = { gestureToAction(GestureType.LEFT, handler, exit) },
                    onSwipeRight = { gestureToAction(GestureType.RIGHT, handler, exit) },
                    onTap = { gestureToAction(GestureType.TAP, handler, exit) },
                    onDoubleTap = { gestureToAction(GestureType.DOUBLE_TAP, handler, exit) },
                    onLongTap = { gestureToAction(GestureType.TAP_HOLD, handler, exit) },
                ),
            )
    }

    private fun gestureToAction(
        gesture: GestureType,
        handler: ScreenInteractionHandler?,
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
            handler?.getBlackOutMode() == true
        ) {
            handler.toggleBlackOutMode()
            return
        }

        if (action == ButtonType.IGNORE) {
            return
        }

        when (action) {
            ButtonType.SKIP_NEXT -> handler?.skipItem()
            ButtonType.SKIP_PREVIOUS -> handler?.skipItem(true)
            ButtonType.MUSIC_NEXT -> handler?.nextTrack()
            ButtonType.MUSIC_PREVIOUS -> handler?.previousTrack()
            ButtonType.SPEED_INCREASE -> handler?.increaseSpeed()
            ButtonType.SPEED_DECREASE -> handler?.decreaseSpeed()
            ButtonType.SEEK_FORWARD -> handler?.seekForward()
            ButtonType.SEEK_BACKWARD -> handler?.seekBackward()
            ButtonType.BRIGHTNESS_INCREASE -> handler?.increaseBrightness()
            ButtonType.BRIGHTNESS_DECREASE -> handler?.decreaseBrightness()
            ButtonType.SHOW_OVERLAYS -> handler?.showOverlays()
            ButtonType.BLACK_OUT_MODE -> handler?.toggleBlackOutMode()
            ButtonType.TOGGLE_MUTE -> handler?.toggleMute()
            ButtonType.TOGGLE_PAUSE -> handler?.togglePause()
            ButtonType.TOGGLE_LOOPING -> handler?.toggleLooping()
            ButtonType.EXIT_TO_SETTINGS -> exit(false)
            else -> exit(true)
        }
    }

    private fun eventToAction(
        event: KeyEvent,
        handler: ScreenInteractionHandler?,
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

        return executeAction(action, handler, exit, type)
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

    fun isPlaybackSpeedActionAssigned(): Boolean {
        val speedActions = listOf(ButtonType.SPEED_INCREASE, ButtonType.SPEED_DECREASE)
        return GeneralPrefs.buttonLeftPress in speedActions ||
            GeneralPrefs.buttonRightPress in speedActions ||
            GeneralPrefs.buttonUpPress in speedActions ||
            GeneralPrefs.buttonDownPress in speedActions ||
            GeneralPrefs.buttonOkPress in speedActions ||
            GeneralPrefs.buttonLeftHold in speedActions ||
            GeneralPrefs.buttonRightHold in speedActions ||
            GeneralPrefs.buttonUpHold in speedActions ||
            GeneralPrefs.buttonDownHold in speedActions ||
            GeneralPrefs.buttonOkHold in speedActions ||
            GeneralPrefs.gestureLeft in speedActions ||
            GeneralPrefs.gestureRight in speedActions ||
            GeneralPrefs.gestureUp in speedActions ||
            GeneralPrefs.gestureDown in speedActions ||
            GeneralPrefs.gestureTap in speedActions ||
            GeneralPrefs.gestureDoubleTap in speedActions ||
            GeneralPrefs.gestureTapHold in speedActions
    }

    suspend fun checkAndResetPlaybackSpeed(context: Context) {
        if (!isPlaybackSpeedActionAssigned() && GeneralPrefs.playbackSpeed != "1") {
            GeneralPrefs.playbackSpeed = "1"
            ToastHelper.show(context, R.string.playlist_playback_speed_reset)
        }
    }

    private fun executeAction(
        action: ButtonType?,
        handler: ScreenInteractionHandler?,
        exit: (shouldExit: Boolean) -> Unit,
        type: ButtonPressType,
    ): Boolean {
        Timber.i("Action: $action, ButtonPressType: $type")

        // Check if any direction/button press should wake from black out mode
        if (GeneralPrefs.wakeOnAnyButtonPress &&
            handler?.getBlackOutMode() == true &&
            type != ButtonPressType.LONG_PRESS_HOLD
        ) {
            // Timber.i("Action: toggleBlackOutMode")
            handler.toggleBlackOutMode()
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
                ButtonType.SKIP_NEXT -> handler?.skipItem()
                ButtonType.SKIP_PREVIOUS -> handler?.skipItem(true)
                ButtonType.MUSIC_NEXT -> handler?.nextTrack()
                ButtonType.MUSIC_PREVIOUS -> handler?.previousTrack()
                ButtonType.SPEED_INCREASE -> handler?.increaseSpeed()
                ButtonType.SPEED_DECREASE -> handler?.decreaseSpeed()
                ButtonType.SEEK_FORWARD -> handler?.seekForward()
                ButtonType.SEEK_BACKWARD -> handler?.seekBackward()
                ButtonType.BRIGHTNESS_INCREASE -> handler?.increaseBrightness()
                ButtonType.BRIGHTNESS_DECREASE -> handler?.decreaseBrightness()
                ButtonType.SHOW_OVERLAYS -> handler?.showOverlays()
                ButtonType.BLACK_OUT_MODE -> handler?.toggleBlackOutMode()
                ButtonType.TOGGLE_MUTE -> handler?.toggleMute()
                ButtonType.TOGGLE_PAUSE -> handler?.togglePause()
                ButtonType.TOGGLE_LOOPING -> handler?.toggleLooping()
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
