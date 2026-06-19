package com.neilturner.aerialviews.ui.core

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.neilturner.aerialviews.data.PlaylistCacheRepository
import com.neilturner.aerialviews.models.LoadingStatus
import com.neilturner.aerialviews.models.MediaPlaylist
import com.neilturner.aerialviews.models.enums.AerialMediaType
import com.neilturner.aerialviews.models.enums.DateType
import com.neilturner.aerialviews.models.enums.LocationType
import com.neilturner.aerialviews.models.enums.MetadataType
import com.neilturner.aerialviews.models.enums.OverlayType
import com.neilturner.aerialviews.models.enums.ProgressBarLocation
import com.neilturner.aerialviews.models.music.MusicPlaylist
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.services.MediaService
import com.neilturner.aerialviews.services.MusicPlayer
import com.neilturner.aerialviews.services.NowPlayingService
import com.neilturner.aerialviews.services.weather.WeatherService
import com.neilturner.aerialviews.services.KtorServer
import com.neilturner.aerialviews.ui.helpers.OverlayHelper
import com.neilturner.aerialviews.ui.helpers.PermissionHelper
import com.neilturner.aerialviews.ui.overlays.MessageOverlay
import com.neilturner.aerialviews.ui.overlays.WeatherForecastOverlay
import com.neilturner.aerialviews.ui.overlays.WeatherNowOverlay
import com.neilturner.aerialviews.ui.overlays.state.OverlayEventBridge
import com.neilturner.aerialviews.ui.overlays.state.OverlayStateStore
import com.neilturner.aerialviews.ui.overlays.state.ProgressOverlayState
import com.neilturner.aerialviews.ui.controls.ProgressState
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
    private val cacheRepository = PlaylistCacheRepository(application)

    private var playlist: MediaPlaylist? = null
    private var currentMedia: AerialMedia? = null
    private var musicPlayer: MusicPlayer? = null
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

    private val _currentMedia = MutableStateFlow<AerialMedia?>(null)
    val currentMediaState: StateFlow<AerialMedia?> = _currentMedia.asStateFlow()

    private val _isVideoVisible = MutableStateFlow(false)
    val isVideoVisible: StateFlow<Boolean> = _isVideoVisible.asStateFlow()

    private val _isImageVisible = MutableStateFlow(false)
    val isImageVisible: StateFlow<Boolean> = _isImageVisible.asStateFlow()

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

    fun startServices(overlayHelper: OverlayHelper) {
        viewModelScope.launch {
            if (PermissionHelper.hasNotificationListenerPermission(context)) {
                nowPlayingService = NowPlayingService(context)
            }

            val hasWeatherNowOverlay = overlayHelper.findOverlay<WeatherNowOverlay>().isNotEmpty()
            val hasForecastOverlay = overlayHelper.findOverlay<WeatherForecastOverlay>().isNotEmpty()
            if (hasWeatherNowOverlay || hasForecastOverlay) {
                weatherService = WeatherService(context).apply {
                    startUpdates(fetchCurrentWeather = hasWeatherNowOverlay, fetchForecast = hasForecastOverlay)
                }
            }

            if (overlayHelper.findOverlay<MessageOverlay>().isNotEmpty() && GeneralPrefs.messageApiEnabled) {
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
                toggleBlackOutMode()
            }
        }
    }

    fun loadPlaylist() {
        viewModelScope.launch {
            val mediaResult = MediaService(context).fetchMedia { status ->
                val text = when (status) {
                    LoadingStatus.RESUMING -> context.getString(com.neilturner.aerialviews.R.string.loading_resuming)
                    LoadingStatus.BUILDING -> context.getString(com.neilturner.aerialviews.R.string.loading_building)
                    LoadingStatus.LOADING -> context.getString(com.neilturner.aerialviews.R.string.loading_title)
                }
                _loadingState.value = LoadingState(visible = true, text = text, spinnerVisible = true)
            }

            playlist = mediaResult.mediaPlaylist
            if (playlist != null && playlist!!.size > 0) {
                Timber.i("Playlist size: ${playlist!!.size}")
                loadNextItem()
            } else {
                _loadingState.value = LoadingState(visible = true, text = context.getString(com.neilturner.aerialviews.R.string.loading_error), spinnerVisible = false)
            }

            // Setup music player
            setupMusicPlayer(mediaResult.musicPlaylist, mediaResult.musicResumeIndex)
        }
    }

    private fun setupMusicPlayer(musicPlaylist: MusicPlaylist?, resumeIndex: Int) {
        val backgroundMusicSelected = GeneralPrefs.playsBackgroundMusic
        if (!backgroundMusicSelected || musicPlaylist == null || musicPlaylist.size == 0) {
            return
        }

        musicPlayer = MusicPlayer(context, musicPlaylist)
        musicPlayer?.createPlayer()
        if (resumeIndex > 0) {
            musicPlayer?.seekToTrack(resumeIndex)
        }
        musicPlayer?.play()
        Timber.i("MusicPlayer: playing ${musicPlaylist.size} tracks")
    }

    fun loadNextItem(previous: Boolean = false) {
        val p = playlist ?: return
        val media = if (previous) p.previousItem() else p.nextItem()
        loadItem(media)
        savePlaybackPosition()
    }

    private fun loadItem(media: AerialMedia) {
        currentMedia = media
        _currentMedia.value = media
        _overlayStateStore.resetForNextMedia()

        // Update visibility
        _isVideoVisible.value = media.type == AerialMediaType.VIDEO
        _isImageVisible.value = media.type == AerialMediaType.IMAGE

        // Update metadata overlays
        updateMetadataOverlayData(media)

        // Post progress bar reset
        if (GeneralPrefs.progressBarLocation != ProgressBarLocation.DISABLED) {
            GlobalBus.post(com.neilturner.aerialviews.ui.controls.ProgressBarEvent(ProgressState.RESET))
        }
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

    private fun savePlaybackPosition() {
        if (GeneralPrefs.playlistCache && playlist != null) {
            viewModelScope.launch {
                cacheRepository.saveMediaPosition(playlist!!.currentPosition)
            }
        }
    }

    fun skipItem(previous: Boolean = false) {
        loadNextItem(previous)
    }

    fun toggleBlackOutMode() {
        val p = playlist ?: return
        if (p.size == 0) return

        _blackOutMode.value = !_blackOutMode.value
        if (_blackOutMode.value) {
            // Enter blackout
        } else {
            // Exit blackout
            loadNextItem()
        }
    }

    fun nextTrack() {
        musicPlayer?.nextTrack()
        saveMusicTrackPosition()
    }

    fun previousTrack() {
        musicPlayer?.previousTrack()
        saveMusicTrackPosition()
    }

    private fun saveMusicTrackPosition() {
        if (GeneralPrefs.playlistCache) {
            viewModelScope.launch {
                musicPlayer?.let {
                    cacheRepository.saveMusicTrackIndex(it.getCurrentTrackIndex())
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopServices()
        musicPlayer?.pause()
        musicPlayer?.release()
    }
}
