package com.neilturner.aerialviews.ui.overlays.state

import com.neilturner.aerialviews.services.MessageEvent
import com.neilturner.aerialviews.services.MusicEvent
import com.neilturner.aerialviews.services.weather.WeatherEvent
import com.neilturner.aerialviews.ui.overlays.ProgressBarEvent
import me.kosert.flowbus.EventsReceiver
import me.kosert.flowbus.subscribe

class OverlayEventBridge(
    private val store: OverlayStateStore,
) {
    private val receiver = EventsReceiver()

    fun start() {
        receiver.subscribe<MusicEvent> { event ->
            store.setNowPlaying(event)
        }
        receiver.subscribe<WeatherEvent> { event ->
            store.setWeather(event)
        }
        receiver.subscribe<MessageEvent> { event ->
            store.setMessage(event.type, event.toState())
        }
        receiver.subscribe<ProgressBarEvent> { event ->
            store.setProgress(event.state, event.position, event.duration)
        }
    }

    fun stop() {
        receiver.unsubscribe()
    }
}
