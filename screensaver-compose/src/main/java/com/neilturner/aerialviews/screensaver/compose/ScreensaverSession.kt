package com.neilturner.aerialviews.screensaver.compose

import android.content.Context
import com.neilturner.aerialviews.models.MediaPlaylist
import com.neilturner.aerialviews.services.MediaService
import com.neilturner.aerialviews.ui.core.ScreensaverController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * The Compose path's session bootstrap.
 *
 * It fetches the same playlist the legacy controller uses and starts the UI-agnostic controller.
 * The controller keeps its own coroutine scope; this session's scope only covers the playlist
 * fetch. It deliberately uses neither the ViewModel nor a host lifecycle scope.
 */
internal class ScreensaverSession(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val controller = ScreensaverController(scope = scope)

    private var started = false
    private var released = false

    fun start() {
        if (started || released) return
        started = true

        scope.launch {
            val result =
                try {
                    MediaService(appContext).fetchMedia()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Compose screensaver playlist fetch failed")
                    null
                }

            if (released) return@launch

            if (result == null) {
                controller.start(MediaPlaylist(emptyList()))
            } else {
                controller.start(
                    playlist = result.mediaPlaylist,
                    fromCache = result.isFromCache,
                )
            }
        }
    }

    fun release() {
        if (released) return
        released = true
        started = true
        scope.cancel()
        controller.release()
    }
}
