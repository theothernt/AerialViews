package com.neilturner.aerialviews.screensaver.compose

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.neilturner.aerialviews.ui.core.ScreensaverController
import com.neilturner.aerialviews.ui.core.ScreensaverState
import com.neilturner.aerialviews.ui.overlays.state.OverlayUiState
import kotlinx.coroutines.flow.StateFlow

/**
 * Lifetime holder for the shared controller.
 *
 * The controller keeps its own coroutine scope and timers; this ViewModel deliberately does not
 * use `viewModelScope` and does not add state or logic of its own. The host's ViewModelStore is
 * cleared when a dream session ends, which reaches [onCleared] and releases the session.
 */
class ScreensaverViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val session = ScreensaverSession(application)

    val state: StateFlow<ScreensaverState> = session.controller.state
    val overlayState: StateFlow<OverlayUiState> = session.controller.overlayState
    val loadingStatus = session.loadingStatus

    /** Shared controller used by the playback layer for the transition protocol. */
    internal val controller: ScreensaverController
        get() = session.controller

    fun start() = session.start()

    override fun onCleared() {
        session.release()
        super.onCleared()
    }
}
