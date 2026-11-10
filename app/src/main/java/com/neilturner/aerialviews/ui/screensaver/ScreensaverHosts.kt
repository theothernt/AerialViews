package com.neilturner.aerialviews.ui.screensaver

import android.content.Context
import android.view.Window
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.ui.core.ExitReason
import com.neilturner.aerialviews.ui.core.ScreensaverHost
import com.neilturner.aerialviews.ui.helpers.LocaleHelper

/** The screensaver context shared by both hosts, including the screensaver-language preference. */
internal fun screensaverLocaleContext(context: Context): Context =
    if (GeneralPrefs.localeScreensaver.startsWith("default")) {
        context
    } else {
        LocaleHelper.alternateLocale(context, GeneralPrefs.localeScreensaver)
    }

/** Host adapter for [DreamActivity]. Exits wake the dream, matching the legacy input callback. */
internal class DreamScreensaverHost(
    override val context: Context,
    override val window: Window,
    override val lifecycleOwner: LifecycleOwner,
    override val savedStateRegistryOwner: SavedStateRegistryOwner,
    override val viewModelStoreOwner: ViewModelStoreOwner,
    private val onExit: (ExitReason) -> Unit,
) : ScreensaverHost {
    override fun exit(reason: ExitReason) = onExit(reason)
}

/** Host adapter for [TestActivity]. Exits finish the activity with its existing result contract. */
internal class ActivityScreensaverHost(
    override val context: Context,
    override val window: Window,
    override val lifecycleOwner: LifecycleOwner,
    override val savedStateRegistryOwner: SavedStateRegistryOwner,
    override val viewModelStoreOwner: ViewModelStoreOwner,
    private val onExit: (ExitReason) -> Unit,
) : ScreensaverHost {
    override fun exit(reason: ExitReason) = onExit(reason)
}
