package com.neilturner.aerialviews.services

import com.neilturner.aerialviews.ui.overlays.ProgressBarEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class PlaybackProgressRepository {
    private val _progressEvent = MutableSharedFlow<ProgressBarEvent>(extraBufferCapacity = 10)
    val progressEvent: SharedFlow<ProgressBarEvent> = _progressEvent.asSharedFlow()

    fun post(event: ProgressBarEvent) {
        _progressEvent.tryEmit(event)
    }
}
