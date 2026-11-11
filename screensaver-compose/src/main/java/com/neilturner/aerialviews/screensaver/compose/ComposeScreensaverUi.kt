package com.neilturner.aerialviews.screensaver.compose

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
import com.neilturner.aerialviews.ui.core.ScreensaverHost
import com.neilturner.aerialviews.ui.core.ScreensaverUi
import kotlinx.coroutines.flow.MutableStateFlow
import timber.log.Timber

/**
 * Compose renderer for the shared screensaver host.
 *
 * Phase 8 hosts the existing playback views and the controller-driven transitions. Overlays and
 * input are added in later batches; returning false lets the host retain its existing key and
 * motion handling until then.
 */
class ComposeScreensaverUi(
    private val onFirstItemDisplayed: () -> Unit,
) : ScreensaverUi {
    private var host: ScreensaverHost? = null
    private var composeView: ComposeView? = null
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

    override fun onKeyEvent(event: KeyEvent): Boolean = false

    override fun onGenericMotionEvent(event: MotionEvent): Boolean = false

    private fun disposeView() {
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
}
