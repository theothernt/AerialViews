package com.neilturner.aerialviews.ui.helpers

import android.content.Context
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.ButtonType
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.ui.core.ScreenController
import timber.log.Timber

object InputHelper {
    private val keyEventMapper = KeyEventMapper()

    fun handleGenericMotionEvent(
        event: MotionEvent,
        exit: (shouldExit: Boolean) -> Unit,
    ): Boolean {
        if (GeneralPrefs.exitOnMouseMovement &&
            event.actionMasked == MotionEvent.ACTION_HOVER_MOVE &&
            event.isFromSource(InputDevice.SOURCE_MOUSE)
        ) {
            exit(true)
            return true
        }
        return false
    }

    fun handleKeyEvent(
        event: KeyEvent,
        controller: ScreenController?,
        exit: (shouldExit: Boolean) -> Unit,
    ): Boolean {
        val action =
            when (event.action) {
                KeyEvent.ACTION_DOWN -> KeyAction.DOWN
                KeyEvent.ACTION_UP -> KeyAction.UP
                else -> KeyAction.OTHER
            }
        val mapping =
            keyEventMapper.map(
                KeyInput(
                    keyCode = event.keyCode,
                    action = action,
                    repeatCount = event.repeatCount,
                    isLongPress = event.isLongPress,
                ),
                currentKeyBindings(),
            )

        return mapping.action?.let { executeAction(it, controller, exit, mapping.pressType) }
            ?: mapping.handled
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
            ButtonType.MUSIC_NEXT -> controller?.nextTrack()
            ButtonType.MUSIC_PREVIOUS -> controller?.previousTrack()
            ButtonType.SPEED_INCREASE -> controller?.increaseSpeed()
            ButtonType.SPEED_DECREASE -> controller?.decreaseSpeed()
            ButtonType.SEEK_FORWARD -> controller?.seekForward()
            ButtonType.SEEK_BACKWARD -> controller?.seekBackward()
            ButtonType.BRIGHTNESS_INCREASE -> controller?.increaseBrightness()
            ButtonType.BRIGHTNESS_DECREASE -> controller?.decreaseBrightness()
            ButtonType.SHOW_OVERLAYS -> controller?.showOverlays()
            ButtonType.BLACK_OUT_MODE -> controller?.toggleBlackOutMode()
            ButtonType.TOGGLE_MUTE -> controller?.toggleMute()
            ButtonType.TOGGLE_PAUSE -> controller?.togglePause()
            ButtonType.TOGGLE_LOOPING -> controller?.toggleLooping()
            ButtonType.RESTART_VIDEO -> controller?.restartVideo()
            ButtonType.EXIT_TO_SETTINGS -> exit(false)
            else -> exit(true)
        }
    }

    private fun currentKeyBindings(): KeyBindings =
        KeyBindings(
            okPress = GeneralPrefs.buttonOkPress,
            okHold = GeneralPrefs.buttonOkHold,
            upPress = GeneralPrefs.buttonUpPress,
            upHold = GeneralPrefs.buttonUpHold,
            downPress = GeneralPrefs.buttonDownPress,
            downHold = GeneralPrefs.buttonDownHold,
            leftPress = GeneralPrefs.buttonLeftPress,
            leftHold = GeneralPrefs.buttonLeftHold,
            rightPress = GeneralPrefs.buttonRightPress,
            rightHold = GeneralPrefs.buttonRightHold,
            mediaButtonPassthrough = GeneralPrefs.enableMediaButtonPassthrough,
        )

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
        controller: ScreenController?,
        exit: (shouldExit: Boolean) -> Unit,
        type: ButtonPressType,
    ): Boolean {
        Timber.i("Action: $action, ButtonPressType: $type")

        // Check if any direction/button press should wake from black out mode
        if (GeneralPrefs.wakeOnAnyButtonPress &&
            controller?.blackOutMode == true &&
            action != ButtonType.EXIT &&
            action != ButtonType.EXIT_TO_SETTINGS &&
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
                ButtonType.MUSIC_NEXT -> controller?.nextTrack()
                ButtonType.MUSIC_PREVIOUS -> controller?.previousTrack()
                ButtonType.SPEED_INCREASE -> controller?.increaseSpeed()
                ButtonType.SPEED_DECREASE -> controller?.decreaseSpeed()
                ButtonType.SEEK_FORWARD -> controller?.seekForward()
                ButtonType.SEEK_BACKWARD -> controller?.seekBackward()
                ButtonType.BRIGHTNESS_INCREASE -> controller?.increaseBrightness()
                ButtonType.BRIGHTNESS_DECREASE -> controller?.decreaseBrightness()
                ButtonType.SHOW_OVERLAYS -> controller?.showOverlays()
                ButtonType.BLACK_OUT_MODE -> controller?.toggleBlackOutMode()
                ButtonType.TOGGLE_MUTE -> controller?.toggleMute()
                ButtonType.TOGGLE_PAUSE -> controller?.togglePause()
                ButtonType.TOGGLE_LOOPING -> controller?.toggleLooping()
                ButtonType.RESTART_VIDEO -> controller?.restartVideo()
                ButtonType.EXIT_TO_SETTINGS -> exit(false)
                else -> exit(true)
            }
        }
        return true
    }
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
