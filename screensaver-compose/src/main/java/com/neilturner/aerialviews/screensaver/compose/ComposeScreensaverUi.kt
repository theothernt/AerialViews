package com.neilturner.aerialviews.screensaver.compose

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.ui.core.ExitReason
import com.neilturner.aerialviews.ui.core.ScreensaverHost
import com.neilturner.aerialviews.ui.core.ScreensaverUi
import com.neilturner.aerialviews.ui.helpers.GestureType
import com.neilturner.aerialviews.ui.helpers.SwipeGestureListener
import kotlinx.coroutines.flow.MutableStateFlow
import timber.log.Timber

/**
 * Compose renderer for the shared screensaver host.
 *
 * The host dispatches key and touch events here. The screen installs the controller-aware handler
 * once its composition is ready; before that, events fall through to the host's existing behavior.
 */
class ComposeScreensaverUi(
    private val onFirstItemDisplayed: () -> Unit,
) : ScreensaverUi {
    private var host: ScreensaverHost? = null
    private var composeView: ComposeView? = null
    private var inputHandler: ComposeScreensaverInputHandler? = null
    private var gestureListener: SwipeGestureListener? = null
    private var released = false
    private val hostStarted = MutableStateFlow(false)

    override fun attach(host: ScreensaverHost) {
        check(!released) { "Compose screensaver UI has already been released" }
        if (this.host != null) {
            disposeView()
        }

        hostStarted.value = false
        this.host = host
        // WindowRecomposer resolves the lifecycle owner from the window decor view, not only from
        // the ComposeView, so install the host owners on both levels.
        host.window.decorView.apply {
            setViewTreeLifecycleOwner(host.lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(host.savedStateRegistryOwner)
            setViewTreeViewModelStoreOwner(host.viewModelStoreOwner)
        }

        gestureListener =
            SwipeGestureListener(
                context = host.context,
                onSwipeUp = { inputHandler?.onGesture(GestureType.UP) },
                onSwipeDown = { inputHandler?.onGesture(GestureType.DOWN) },
                onSwipeLeft = { inputHandler?.onGesture(GestureType.LEFT) },
                onSwipeRight = { inputHandler?.onGesture(GestureType.RIGHT) },
                onTap = { inputHandler?.onGesture(GestureType.TAP) },
                onDoubleTap = { inputHandler?.onGesture(GestureType.DOUBLE_TAP) },
                onLongTap = { inputHandler?.onGesture(GestureType.TAP_HOLD) },
            )

        val view =
            ComposeView(host.context).apply {
                // The dream owner reaches DESTROYED on teardown, so this strategy disposes the
                // composition without the renderer owning a lifecycle of its own.
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                // ComposeView must use the locale-wrapped host context and the host's owners.
                setViewTreeLifecycleOwner(host.lifecycleOwner)
                setViewTreeSavedStateRegistryOwner(host.savedStateRegistryOwner)
                setViewTreeViewModelStoreOwner(host.viewModelStoreOwner)
                setContent {
                    val started by hostStarted.collectAsState()
                    ComposeScreensaverScreen(
                        hostStarted = started,
                        onFirstItemDisplayed = onFirstItemDisplayed,
                        onExit = ::reportExit,
                        onInputReady = { inputHandler = it },
                    )
                }
            }

        composeView = view
        host.window.setContentView(view)
        Timber.i("Compose screensaver playback attached")
    }

    override fun onStart() {
        if (!released) {
            hostStarted.value = true
        }
    }

    override fun onStop() {
        hostStarted.value = false
    }

    override fun onKeyEvent(event: KeyEvent): Boolean = inputHandler?.onKeyEvent(event) ?: false

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (GeneralPrefs.exitOnMouseMovement &&
            event.actionMasked == MotionEvent.ACTION_HOVER_MOVE &&
            event.isFromSource(InputDevice.SOURCE_MOUSE)
        ) {
            reportExit(ExitReason.EXIT)
            return true
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean = gestureListener?.onTouch(null, event) == true

    private fun disposeView() {
        inputHandler?.reset()
        inputHandler = null
        gestureListener = null
        val view = composeView ?: return
        composeView = null
        view.disposeComposition()
        (view.parent as? ViewGroup)?.removeView(view)
    }

    override fun release() {
        if (released) return
        released = true
        hostStarted.value = false
        disposeView()
        host = null
        Timber.i("Compose screensaver playback released")
    }

    private fun reportExit(reason: ExitReason) {
        host?.exit(reason)
    }
}
