package com.neilturner.aerialviews.ui.core

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.neilturner.aerialviews.models.enums.DateType
import com.neilturner.aerialviews.models.enums.LocationType
import com.neilturner.aerialviews.models.enums.MetadataType
import com.neilturner.aerialviews.models.enums.OverlayType
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.services.KtorServer
import com.neilturner.aerialviews.services.NowPlayingService
import com.neilturner.aerialviews.services.weather.WeatherService
import com.neilturner.aerialviews.ui.helpers.PermissionHelper
import com.neilturner.aerialviews.ui.overlays.state.OverlayEventBridge
import com.neilturner.aerialviews.ui.overlays.state.OverlayStateStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.kosert.flowbus.GlobalBus
import timber.log.Timber

class ScreensaverViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application

    private var currentMedia: AerialMedia? = null
    private var nowPlayingService: NowPlayingService? = null
    private var weatherService: WeatherService? = null
    private var ktorServer: KtorServer? = null
    private val metadataResolver = MetadataResolver()
    private val metadataJobs = mutableMapOf<OverlayType, Job>()
    private var sleepTimerJob: Job? = null

    data class LoadingState(
        val visible: Boolean = true,
        val text: String = "",
        val spinnerVisible: Boolean = false,
    )

    private val _loadingState = MutableStateFlow(LoadingState())
    val loadingState: StateFlow<LoadingState> = _loadingState.asStateFlow()

    private val _blackOutMode = MutableStateFlow(false)
    val blackOutMode: StateFlow<Boolean> = _blackOutMode.asStateFlow()

    private val _overlayStateStore = OverlayStateStore()
    val overlayStateStore: OverlayStateStore = _overlayStateStore
    private val overlayEventBridge = OverlayEventBridge(_overlayStateStore)

    fun startOverlayEventBridge() {
        overlayEventBridge.start()
    }

    fun stopOverlayEventBridge() {
        overlayEventBridge.stop()
    }

    fun startServices() {
        viewModelScope.launch {
            if (PermissionHelper.hasNotificationListenerPermission(context)) {
                nowPlayingService = NowPlayingService(context)
            }

            val slots = listOf(
                GeneralPrefs.slotBottomLeft1, GeneralPrefs.slotBottomLeft2,
                GeneralPrefs.slotBottomRight1, GeneralPrefs.slotBottomRight2,
                GeneralPrefs.slotTopLeft1, GeneralPrefs.slotTopLeft2,
                GeneralPrefs.slotTopRight1, GeneralPrefs.slotTopRight2,
            )
            val hasWeatherNowOverlay = slots.any { it == OverlayType.WEATHER1 }
            val hasForecastOverlay = slots.any { it == OverlayType.WEATHER2 }
            if (hasWeatherNowOverlay || hasForecastOverlay) {
                weatherService = WeatherService(context).apply {
                    startUpdates(fetchCurrentWeather = hasWeatherNowOverlay, fetchForecast = hasForecastOverlay)
                }
            }

            val hasMessageOverlay = slots.any { it == OverlayType.MESSAGE1 || it == OverlayType.MESSAGE2 || it == OverlayType.MESSAGE3 || it == OverlayType.MESSAGE4 }
            if (hasMessageOverlay && GeneralPrefs.messageApiEnabled) {
                ktorServer = KtorServer(context) { event -> GlobalBus.post(event) }.apply { start() }
            }
        }
    }

    fun stopServices() {
        nowPlayingService?.stop()
        weatherService?.stop()
        ktorServer?.stop()
    }

    fun scheduleSleepTimer() {
        sleepTimerJob?.cancel()
        val minutes = GeneralPrefs.sleepTimer.toLongOrNull() ?: 0L
        if (minutes <= 0L) {
            Timber.i("Sleep timer disabled")
            return
        }
        Timber.i("Scheduling sleep timer for $minutes minute(s)")
        sleepTimerJob = viewModelScope.launch {
            delay(minutes * 60_000L)
            if (!_blackOutMode.value) {
                Timber.i("Sleep timer finished - toggling blackout mode")
                _blackOutMode.value = true
            }
        }
    }

    fun onOverlayReset() {
        _overlayStateStore.resetForNextMedia()
    }

    fun onLoadingStateUpdate(visible: Boolean, text: String, spinnerVisible: Boolean) {
        _loadingState.value = LoadingState(visible = visible, text = text, spinnerVisible = spinnerVisible)
    }

    fun updateMetadataOverlayData(media: AerialMedia) {
        currentMedia = media
        val metadataSlots = listOf(
            OverlayType.METADATA1, OverlayType.METADATA2,
            OverlayType.METADATA3, OverlayType.METADATA4,
        )
        metadataSlots.forEach { slot ->
            metadataJobs[slot]?.cancel()
            metadataJobs[slot] = viewModelScope.launch {
                try {
                    val preferences = getMetadataPreferences(slot)
                    val resolved = metadataResolver.resolve(context, media, preferences)
                    if (currentMedia !== media) return@launch
                    _overlayStateStore.setMetadata(slot, resolved.text, resolved.poi, resolved.metadataType)
                } catch (e: Exception) {
                    Timber.e(e, "Metadata slot $slot resolver failed")
                    if (currentMedia === media) {
                        _overlayStateStore.setMetadata(slot, "", emptyMap(), MetadataType.STATIC)
                    }
                }
            }
        }
    }

    private fun getMetadataPreferences(slot: OverlayType): MetadataResolver.Preferences =
        when (slot) {
            OverlayType.METADATA1 -> MetadataResolver.Preferences(
                videoSelection = GeneralPrefs.overlayMetadata1Videos,
                videoFolderDepth = GeneralPrefs.overlayMetadata1VideosFolderLevel.toIntOrNull() ?: 1,
                videoLocationType = GeneralPrefs.overlayMetadata1VideosLocationType ?: LocationType.CITY_COUNTRY,
                photoSelection = GeneralPrefs.overlayMetadata1Photos,
                photoFolderDepth = GeneralPrefs.overlayMetadata1PhotosFolderLevel.toIntOrNull() ?: 1,
                photoLocationType = GeneralPrefs.overlayMetadata1PhotosLocationType ?: LocationType.CITY_COUNTRY,
                photoDateType = GeneralPrefs.overlayMetadata1PhotosDateType ?: DateType.COMPACT,
                photoDateCustom = GeneralPrefs.overlayMetadata1PhotosDateCustom,
            )
            OverlayType.METADATA2 -> MetadataResolver.Preferences(
                videoSelection = GeneralPrefs.overlayMetadata2Videos,
                videoFolderDepth = GeneralPrefs.overlayMetadata2VideosFolderLevel.toIntOrNull() ?: 1,
                videoLocationType = GeneralPrefs.overlayMetadata2VideosLocationType ?: LocationType.CITY_COUNTRY,
                photoSelection = GeneralPrefs.overlayMetadata2Photos,
                photoFolderDepth = GeneralPrefs.overlayMetadata2PhotosFolderLevel.toIntOrNull() ?: 1,
                photoLocationType = GeneralPrefs.overlayMetadata2PhotosLocationType ?: LocationType.CITY_COUNTRY,
                photoDateType = GeneralPrefs.overlayMetadata2PhotosDateType ?: DateType.COMPACT,
                photoDateCustom = GeneralPrefs.overlayMetadata2PhotosDateCustom,
            )
            OverlayType.METADATA3 -> MetadataResolver.Preferences(
                videoSelection = GeneralPrefs.overlayMetadata3Videos,
                videoFolderDepth = GeneralPrefs.overlayMetadata3VideosFolderLevel.toIntOrNull() ?: 1,
                videoLocationType = GeneralPrefs.overlayMetadata3VideosLocationType ?: LocationType.CITY_COUNTRY,
                photoSelection = GeneralPrefs.overlayMetadata3Photos,
                photoFolderDepth = GeneralPrefs.overlayMetadata3PhotosFolderLevel.toIntOrNull() ?: 1,
                photoLocationType = GeneralPrefs.overlayMetadata3PhotosLocationType ?: LocationType.CITY_COUNTRY,
                photoDateType = GeneralPrefs.overlayMetadata3PhotosDateType ?: DateType.COMPACT,
                photoDateCustom = GeneralPrefs.overlayMetadata3PhotosDateCustom,
            )
            else -> MetadataResolver.Preferences(
                videoSelection = GeneralPrefs.overlayMetadata4Videos,
                videoFolderDepth = GeneralPrefs.overlayMetadata4VideosFolderLevel.toIntOrNull() ?: 1,
                videoLocationType = GeneralPrefs.overlayMetadata4VideosLocationType ?: LocationType.CITY_COUNTRY,
                photoSelection = GeneralPrefs.overlayMetadata4Photos,
                photoFolderDepth = GeneralPrefs.overlayMetadata4PhotosFolderLevel.toIntOrNull() ?: 1,
                photoLocationType = GeneralPrefs.overlayMetadata4PhotosLocationType ?: LocationType.CITY_COUNTRY,
                photoDateType = GeneralPrefs.overlayMetadata4PhotosDateType ?: DateType.COMPACT,
                photoDateCustom = GeneralPrefs.overlayMetadata4PhotosDateCustom,
            )
        }

    override fun onCleared() {
        super.onCleared()
        stopServices()
        sleepTimerJob?.cancel()
        metadataJobs.values.forEach { it.cancel() }
        metadataJobs.clear()
    }
}
