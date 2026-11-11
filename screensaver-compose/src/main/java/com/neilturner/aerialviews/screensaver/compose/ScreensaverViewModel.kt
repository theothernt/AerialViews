package com.neilturner.aerialviews.screensaver.compose

import androidx.lifecycle.ViewModel
import com.neilturner.aerialviews.ui.core.ScreensaverController
import com.neilturner.aerialviews.ui.core.ScreensaverState
import kotlinx.coroutines.flow.StateFlow

/**
 * Lifetime holder for the shared controller.
 *
 * The controller keeps its own coroutine scope and timers; this ViewModel deliberately does not
 * use `viewModelScope` and does not add state or logic of its own. The host's ViewModelStore is
 * cleared when a dream session ends, which reaches [onCleared] and releases the controller.
 */
class ScreensaverViewModel : ViewModel() {
    private val controller = ScreensaverController()

    val state: StateFlow<ScreensaverState> = controller.state

    override fun onCleared() {
        controller.release()
    }
}
