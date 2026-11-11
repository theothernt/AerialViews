package com.neilturner.aerialviews.screensaver.compose

import android.view.KeyEvent
import com.neilturner.aerialviews.models.enums.ButtonType
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.ui.core.ExitReason
import com.neilturner.aerialviews.ui.core.ScreensaverController
import com.neilturner.aerialviews.ui.helpers.ButtonPressType
import com.neilturner.aerialviews.ui.helpers.GestureType
import com.neilturner.aerialviews.ui.helpers.KeyAction
import com.neilturner.aerialviews.ui.helpers.KeyBindings
import com.neilturner.aerialviews.ui.helpers.KeyEventMapper
import com.neilturner.aerialviews.ui.helpers.KeyInput

/** Playback operations that live in the Compose player layer rather than the shared controller. */
internal interface ComposePlaybackControls {
    fun togglePause()

    fun toggleMute()

    fun increaseSpeed()

    fun decreaseSpeed()

    fun seekForward()

    fun seekBackward()

    fun restartVideo()

    fun refreshFinishTimer()

    fun increaseBrightness()

    fun decreaseBrightness()
}

internal interface ComposeScreensaverInputHandler {
    fun onKeyEvent(event: KeyEvent): Boolean

    fun onGesture(gesture: GestureType)

    fun reset()
}

/**
 * Input adapter for the Compose renderer.
 *
 * Key mapping deliberately matches the legacy [com.neilturner.aerialviews.ui.helpers.InputHelper]
 * path: it reuses the same state machine, preference bindings and blackout wake rule. Playback
 * actions are delegated to the hosted player views; playlist, music and blackout actions stay with
 * the shared controller.
 */
internal class ComposeScreensaverInput(
    private val controller: ScreensaverController,
    private val playbackControls: () -> ComposePlaybackControls?,
    private val onExit: (ExitReason) -> Unit,
    private val onRevealOverlays: () -> Unit,
) : ComposeScreensaverInputHandler {
    private val keyEventMapper = KeyEventMapper()

    override fun onKeyEvent(event: KeyEvent): Boolean {
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

        return mapping.action?.let { executeAction(it, mapping.pressType) } ?: mapping.handled
    }

    override fun onGesture(gesture: GestureType) {
        val action =
            when (gesture) {
                GestureType.UP -> GeneralPrefs.gestureUp
                GestureType.DOWN -> GeneralPrefs.gestureDown
                GestureType.LEFT -> GeneralPrefs.gestureLeft
                GestureType.RIGHT -> GeneralPrefs.gestureRight
                GestureType.TAP -> GeneralPrefs.gestureTap
                GestureType.DOUBLE_TAP -> GeneralPrefs.gestureDoubleTap
                GestureType.TAP_HOLD -> GeneralPrefs.gestureTapHold
            } ?: return

        if (GeneralPrefs.wakeOnAnyButtonPress && controller.state.value.blackout.active) {
            controller.toggleBlackout()
            return
        }

        if (action != ButtonType.IGNORE) {
            executeAction(action, ButtonPressType.PRESS)
        }
    }

    override fun reset() {
        keyEventMapper.reset()
    }

    private fun executeAction(
        action: ButtonType,
        type: ButtonPressType,
    ): Boolean {
        if (GeneralPrefs.wakeOnAnyButtonPress &&
            controller.state.value.blackout.active &&
            action != ButtonType.EXIT &&
            action != ButtonType.EXIT_TO_SETTINGS &&
            type != ButtonPressType.LONG_PRESS_HOLD
        ) {
            controller.toggleBlackout()
            return true
        }

        if (action == ButtonType.IGNORE) return false
        if (type == ButtonPressType.LONG_PRESS_HOLD) return false

        val controls = playbackControls()
        when (action) {
            ButtonType.SKIP_NEXT -> {
                controller.skip()
            }

            ButtonType.SKIP_PREVIOUS -> {
                controller.skip(previous = true)
            }

            ButtonType.MUSIC_NEXT -> {
                controller.nextTrack()
            }

            ButtonType.MUSIC_PREVIOUS -> {
                controller.previousTrack()
            }

            ButtonType.SPEED_INCREASE -> {
                controls?.increaseSpeed()
            }

            ButtonType.SPEED_DECREASE -> {
                controls?.decreaseSpeed()
            }

            ButtonType.SEEK_FORWARD -> {
                controls?.seekForward()
            }

            ButtonType.SEEK_BACKWARD -> {
                controls?.seekBackward()
            }

            ButtonType.BRIGHTNESS_INCREASE -> {
                controls?.increaseBrightness()
            }

            ButtonType.BRIGHTNESS_DECREASE -> {
                controls?.decreaseBrightness()
            }

            ButtonType.SHOW_OVERLAYS -> {
                revealOverlays()
            }

            ButtonType.BLACK_OUT_MODE -> {
                controller.toggleBlackout()
            }

            ButtonType.TOGGLE_MUTE -> {
                controls?.toggleMute()
            }

            ButtonType.TOGGLE_PAUSE -> {
                controls?.togglePause()
            }

            ButtonType.TOGGLE_LOOPING -> {
                val enabled = controller.toggleLoopUntilSkipped()
                GeneralPrefs.loopUntilSkipped = enabled
                controls?.refreshFinishTimer()
            }

            ButtonType.RESTART_VIDEO -> {
                controls?.restartVideo()
            }

            ButtonType.EXIT -> {
                onExit(ExitReason.EXIT)
            }

            ButtonType.EXIT_TO_SETTINGS -> {
                onExit(ExitReason.EXIT_TO_SETTINGS)
            }

            else -> {
                onExit(ExitReason.EXIT)
            }
        }
        return true
    }

    private fun revealOverlays() {
        if (GeneralPrefs.overlayVisibility == "ALWAYS_VISIBLE") return
        if (controller.state.value.blackout.active) return
        if (!controller.state.value.canShowOverlays) return
        onRevealOverlays()
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
}
