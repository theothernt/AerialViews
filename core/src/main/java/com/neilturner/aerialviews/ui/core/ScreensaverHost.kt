package com.neilturner.aerialviews.ui.core

import android.content.Context
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Window
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner

/** The two ways the screensaver can leave a host. */
enum class ExitReason {
    EXIT,
    EXIT_TO_SETTINGS,
}

/**
 * The host-owned values and actions a [ScreensaverUi] needs.
 *
 * [context] is already wrapped with the screensaver locale; renderers must not create their own
 * locale context. The lifecycle owner is the dream service for [DreamService]-based hosts and the
 * activity for test launches. The host owns result/finish semantics: a renderer only reports an
 * [ExitReason].
 */
interface ScreensaverHost {
    val context: Context
    val window: Window
    val lifecycleOwner: LifecycleOwner
    val savedStateRegistryOwner: SavedStateRegistryOwner
    val viewModelStoreOwner: ViewModelStoreOwner

    fun exit(reason: ExitReason)
}

/**
 * A screensaver renderer attached to a [ScreensaverHost].
 *
 * The host controls when the renderer starts and stops. [release] must be idempotent and clear any
 * process-global state the renderer owns.
 */
interface ScreensaverUi {
    fun attach(host: ScreensaverHost)

    fun onStart()

    fun onStop()

    fun onKeyEvent(event: KeyEvent): Boolean

    /** Preserves mouse-to-exit behavior until the Compose input path replaces it. */
    fun onGenericMotionEvent(event: MotionEvent): Boolean = false

    fun release()
}
