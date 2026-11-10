package com.neilturner.aerialviews.ui.screensaver

import android.view.KeyEvent
import android.view.MotionEvent
import com.neilturner.aerialviews.ui.core.ExitReason
import com.neilturner.aerialviews.ui.core.ScreenController
import com.neilturner.aerialviews.ui.core.ScreensaverHost
import com.neilturner.aerialviews.ui.core.ScreensaverUi
import com.neilturner.aerialviews.ui.helpers.InputHelper

/** Preserves the existing View-based screensaver behind the shared host/UI seam. */
internal class LegacyScreensaverUi : ScreensaverUi {
    private var host: ScreensaverHost? = null
    private var controller: ScreenController? = null

    override fun attach(host: ScreensaverHost) {
        check(this.host == null) { "Screensaver UI is already attached" }
        check(controller == null) { "Screensaver controller already exists" }

        this.host = host
        InputHelper.resetKeyState()

        val controller = ScreenController(host.context)
        this.controller = controller
        host.window.setContentView(controller.view)
        InputHelper.setupGestureListener(
            context = host.context,
            controller = controller,
            exit = ::reportExit,
        )
    }

    override fun onStart() = Unit

    override fun onStop() {
        controller?.stop()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean = controller?.let { InputHelper.handleKeyEvent(event, it, ::reportExit) } ?: false

    override fun onGenericMotionEvent(event: MotionEvent): Boolean = InputHelper.handleGenericMotionEvent(event, ::reportExit)

    override fun release() {
        val controller = this.controller
        this.controller = null
        controller?.stop()
        InputHelper.resetKeyState()
        host = null
    }

    private fun reportExit(exitApp: Boolean) {
        host?.exit(
            if (exitApp) {
                ExitReason.EXIT
            } else {
                ExitReason.EXIT_TO_SETTINGS
            },
        )
    }
}
