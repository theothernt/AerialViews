package com.neilturner.aerialviews.ui.core

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neilturner.aerialviews.data.PlaylistCacheRepository
import com.neilturner.aerialviews.models.LoadingStatus
import com.neilturner.aerialviews.models.MediaPlaylist
import com.neilturner.aerialviews.models.music.MusicPlaylist
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.models.enums.AerialMediaType
import com.neilturner.aerialviews.models.enums.DateType
import com.neilturner.aerialviews.models.enums.LocationType
import com.neilturner.aerialviews.models.enums.MetadataType
import com.neilturner.aerialviews.models.enums.OverlayType
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.services.KtorServer
import com.neilturner.aerialviews.services.MediaService
import com.neilturner.aerialviews.services.MessageRepository
import com.neilturner.aerialviews.services.MusicPlayer
import com.neilturner.aerialviews.services.NowPlayingService
import com.neilturner.aerialviews.services.PlaybackProgressRepository
import com.neilturner.aerialviews.services.weather.WeatherService
import com.neilturner.aerialviews.ui.overlays.state.MetadataOverlayState
import com.neilturner.aerialviews.ui.overlays.state.MessageOverlayState
import com.neilturner.aerialviews.ui.overlays.state.OverlayUiState
import com.neilturner.aerialviews.ui.overlays.state.NowPlayingOverlayState
import com.neilturner.aerialviews.ui.overlays.state.WeatherOverlayState
import com.neilturner.aerialviews.ui.overlays.state.ForecastOverlayState
import com.neilturner.aerialviews.ui.overlays.state.ProgressOverlayState
import com.neilturner.aerialviews.utils.PermissionHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

data class ScreenUiState(
    val currentMedia: AerialMedia? = null,
    val loadingStatus: LoadingStatus? = null,
    val isPaused: Boolean = false,
    val blackOutMode: Boolean = false,
    val canSkip: Boolean = false,
    val isPlaylistLoaded: Boolean = false,
    val error: String? = null,
    val alternate: Boolean = false,
    val overlayState: OverlayUiState = OverlayUiState(),
    val brightness: Float = 1.0f,
    val isMuted: Boolean = false,
    val isLooping: Boolean = false,
    val playbackSpeed: String = "1",
    val showOverlaysEvent: Long = 0,
    val seekForwardEvent: Long = 0,
    val seekBackwardEvent: Long = 0,
    val playbackSpeedChangedEvent: Long = 0,
    val brightnessChangedEvent: Long = 0
)

class ScreenViewModel(
    private val context: Context,
    private val mediaService: MediaService,
    private val nowPlayingService: NowPlayingService,
    private val weatherService: WeatherService,
    private val cacheRepository: PlaylistCacheRepository,
    private val messageRepository: MessageRepository,
    private val progressRepository: PlaybackProgressRepository
) : ViewModel(), ScreenInteractionHandler {

    private val _uiState = MutableStateFlow(ScreenUiState())
    val uiState: StateFlow<ScreenUiState> = _uiState.asStateFlow()

    private lateinit var playlist: MediaPlaylist
    private var musicPlayer: MusicPlayer? = null
    private var ktorServer: KtorServer? = null
    private var sleepTimerJob: Job? = null
    private val metadataResolver = MetadataResolver()
    private val metadataJobs = mutableMapOf<OverlayType, Job>()
    
    private val shouldAlternateOverlays = GeneralPrefs.alternateTextPosition
    private val brightnessValues = context.resources.getStringArray(com.neilturner.aerialviews.R.array.percentage1_values)

    init {
        loadPlaylist()
        startEventCollection()
    }

    private fun startEventCollection() {
        viewModelScope.launch {
            nowPlayingService.musicEvent.collect { event ->
                _uiState.update { it.copy(overlayState = it.overlayState.copy(nowPlaying = NowPlayingOverlayState(event))) }
            }
        }
        viewModelScope.launch {
            weatherService.weatherEvent.collect { event ->
                event?.let { e ->
                    _uiState.update { it.copy(overlayState = it.overlayState.copy(weather = WeatherOverlayState(e))) }
                }
            }
        }
        viewModelScope.launch {
            weatherService.forecastEvent.collect { event ->
                event?.let { e ->
                    _uiState.update { it.copy(overlayState = it.overlayState.copy(forecast = ForecastOverlayState(e))) }
                }
            }
        }
        viewModelScope.launch {
            messageRepository.messageEvent.collect { event ->
                _uiState.update { 
                    it.copy(overlayState = it.overlayState.copy(
                        message = it.overlayState.message.toMutableMap().apply { 
                            put(event.type, MessageOverlayState(event.text, event.duration, event.textSize, event.textWeight)) 
                        }
                    )) 
                }
            }
        }
        viewModelScope.launch {
            progressRepository.progressEvent.collect { event ->
                _uiState.update { 
                    it.copy(overlayState = it.overlayState.copy(
                        progress = ProgressOverlayState(event.state, event.position, event.duration)
                    )) 
                }
            }
        }
    }

    private fun loadPlaylist() {
        viewModelScope.launch {
            val mediaResult = mediaService.fetchMedia { status ->
                _uiState.update { it.copy(loadingStatus = status) }
            }
            playlist = mediaResult.mediaPlaylist
            if (playlist.size > 0) {
                _uiState.update { it.copy(isPlaylistLoaded = true) }
                
                // Initialize services that were previously in Legacy Controller init/launch
                setupNotificationService()
                setupKtorServer()
                setupWeatherUpdates()
                setupMusicPlayer(mediaResult.musicPlaylist, mediaResult.musicResumeIndex)
                
                loadNextItem()
                scheduleSleepTimer()
            } else {
                _uiState.update { it.copy(error = "No media found") }
            }
        }
    }

    private fun setupNotificationService() {
        if (PermissionHelper.hasNotificationListenerPermission(context)) {
            // nowPlayingService is already created via Koin singleton
        }
    }

    private fun setupKtorServer() {
        // OverlayHelper check is tricky here since it's a view utility.
        // We might need to rethink this or just check prefs.
        if (GeneralPrefs.messageApiEnabled) {
            ktorServer = KtorServer(context) { messageEvent ->
                messageRepository.post(messageEvent)
            }.apply {
                start()
            }
        }
    }

    private fun setupWeatherUpdates() {
        // Similar to Ktor, checking for overlays might be hard without view access.
        // For now, let's just start it if enabled in prefs or always if it's low resource.
        weatherService.startUpdates(
            fetchCurrentWeather = true, // Simplified for now
            fetchForecast = true
        )
    }

    private fun setupMusicPlayer(musicPlaylist: MusicPlaylist?, resumeIndex: Int) {
        val backgroundMusicSelected = GeneralPrefs.playsBackgroundMusic
        if (!backgroundMusicSelected || musicPlaylist == null || musicPlaylist.size == 0) return

        musicPlayer = MusicPlayer(context, musicPlaylist).apply {
            onMediaItemChanged = { saveMusicTrackPosition() }
            createPlayer()
            if (resumeIndex > 0) {
                seekToTrack(resumeIndex)
            }
            play()
        }
    }

    fun loadNextItem() {
        if (!this::playlist.isInitialized) return
        viewModelScope.launch {
            val media = playlist.nextItem()
            _uiState.update { it.copy(
                currentMedia = media, 
                isPaused = false,
                alternate = if (shouldAlternateOverlays) !it.alternate else it.alternate
            ) }
            updateMetadataOverlayData(media)
            savePlaybackPosition()
        }
    }

    fun loadPreviousItem() {
        if (!this::playlist.isInitialized) return
        viewModelScope.launch {
            val media = playlist.previousItem()
            _uiState.update { it.copy(
                currentMedia = media, 
                isPaused = false,
                alternate = if (shouldAlternateOverlays) !it.alternate else it.alternate
            ) }
            updateMetadataOverlayData(media)
            savePlaybackPosition()
        }
    }

    override fun togglePause() {
        _uiState.update { it.copy(isPaused = !it.isPaused) }
    }

    override fun toggleBlackOutMode() {
        val newMode = !_uiState.value.blackOutMode
        _uiState.update { it.copy(blackOutMode = newMode) }
        if (newMode) {
            sleepTimerJob?.cancel()
        } else {
            loadNextItem()
            scheduleSleepTimer()
        }
    }

    fun onVideoMetadataExtracted(metadata: ExtractedVideoMetadata) {
        val media = _uiState.value.currentMedia ?: return
        Timber.i("Video metadata: %s", formatVideoMetadataForLog(metadata))
        val changed = applyVideoMetadataToMedia(media, metadata)

        if (changed) {
            updateMetadataOverlayData(media)
        }
    }

    fun onImagePrepared() {
        val media = _uiState.value.currentMedia ?: return
        if (media.type == AerialMediaType.IMAGE) {
            updateMetadataOverlayData(media)
        }
    }

    private fun updateMetadataOverlayData(media: AerialMedia) {
        val metadataSlots =
            listOf(
                OverlayType.METADATA1,
                OverlayType.METADATA2,
                OverlayType.METADATA3,
                OverlayType.METADATA4,
            )

        metadataSlots.forEach { slot ->
            metadataJobs[slot]?.cancel()
            metadataJobs[slot] =
                viewModelScope.launch {
                    try {
                        val preferences = getMetadataPreferences(slot)
                        val resolved = metadataResolver.resolve(context, media, preferences)
                        if (_uiState.value.currentMedia !== media) return@launch

                        setMetadata(
                            slot,
                            resolved.text,
                            resolved.poi,
                            resolved.metadataType,
                        )
                    } catch (e: Exception) {
                        Timber.e(e, "Metadata slot $slot resolver failed")
                        if (_uiState.value.currentMedia === media) {
                            setMetadata(slot, "", emptyMap(), MetadataType.STATIC)
                        }
                    }
                }
        }
    }

    private fun getMetadataPreferences(slot: OverlayType): MetadataResolver.Preferences =
        when (slot) {
            OverlayType.METADATA1 -> {
                MetadataResolver.Preferences(
                    videoSelection = GeneralPrefs.overlayMetadata1Videos,
                    videoFolderDepth = GeneralPrefs.overlayMetadata1VideosFolderLevel.toIntOrNull() ?: 1,
                    videoLocationType =
                        GeneralPrefs.overlayMetadata1VideosLocationType ?: LocationType.CITY_COUNTRY,
                    photoSelection = GeneralPrefs.overlayMetadata1Photos,
                    photoFolderDepth = GeneralPrefs.overlayMetadata1PhotosFolderLevel.toIntOrNull() ?: 1,
                    photoLocationType =
                        GeneralPrefs.overlayMetadata1PhotosLocationType ?: LocationType.CITY_COUNTRY,
                    photoDateType =
                        GeneralPrefs.overlayMetadata1PhotosDateType ?: DateType.COMPACT,
                    photoDateCustom = GeneralPrefs.overlayMetadata1PhotosDateCustom,
                )
            }

            OverlayType.METADATA2 -> {
                MetadataResolver.Preferences(
                    videoSelection = GeneralPrefs.overlayMetadata2Videos,
                    videoFolderDepth = GeneralPrefs.overlayMetadata2VideosFolderLevel.toIntOrNull() ?: 1,
                    videoLocationType =
                        GeneralPrefs.overlayMetadata2VideosLocationType ?: LocationType.CITY_COUNTRY,
                    photoSelection = GeneralPrefs.overlayMetadata2Photos,
                    photoFolderDepth = GeneralPrefs.overlayMetadata2PhotosFolderLevel.toIntOrNull() ?: 1,
                    photoLocationType =
                        GeneralPrefs.overlayMetadata2PhotosLocationType ?: LocationType.CITY_COUNTRY,
                    photoDateType =
                        GeneralPrefs.overlayMetadata2PhotosDateType ?: DateType.COMPACT,
                    photoDateCustom = GeneralPrefs.overlayMetadata2PhotosDateCustom,
                )
            }

            OverlayType.METADATA3 -> {
                MetadataResolver.Preferences(
                    videoSelection = GeneralPrefs.overlayMetadata3Videos,
                    videoFolderDepth = GeneralPrefs.overlayMetadata3VideosFolderLevel.toIntOrNull() ?: 1,
                    videoLocationType =
                        GeneralPrefs.overlayMetadata3VideosLocationType ?: LocationType.CITY_COUNTRY,
                    photoSelection = GeneralPrefs.overlayMetadata3Photos,
                    photoFolderDepth = GeneralPrefs.overlayMetadata3PhotosFolderLevel.toIntOrNull() ?: 1,
                    photoLocationType =
                        GeneralPrefs.overlayMetadata3PhotosLocationType ?: LocationType.CITY_COUNTRY,
                    photoDateType =
                        GeneralPrefs.overlayMetadata3PhotosDateType ?: DateType.COMPACT,
                    photoDateCustom = GeneralPrefs.overlayMetadata3PhotosDateCustom,
                )
            }

            else -> {
                MetadataResolver.Preferences(
                    videoSelection = GeneralPrefs.overlayMetadata4Videos,
                    videoFolderDepth = GeneralPrefs.overlayMetadata4VideosFolderLevel.toIntOrNull() ?: 1,
                    videoLocationType =
                        GeneralPrefs.overlayMetadata4VideosLocationType ?: LocationType.CITY_COUNTRY,
                    photoSelection = GeneralPrefs.overlayMetadata4Photos,
                    photoFolderDepth = GeneralPrefs.overlayMetadata4PhotosFolderLevel.toIntOrNull() ?: 1,
                    photoLocationType =
                        GeneralPrefs.overlayMetadata4PhotosLocationType ?: LocationType.CITY_COUNTRY,
                    photoDateType =
                        GeneralPrefs.overlayMetadata4PhotosDateType ?: DateType.COMPACT,
                    photoDateCustom = GeneralPrefs.overlayMetadata4PhotosDateCustom,
                )
            }
        }

    override fun skipItem(previous: Boolean) {
        if (previous) loadPreviousItem() else loadNextItem()
    }

    override fun nextTrack() {
        if (musicPlayer?.hasMusic() == true) {
            musicPlayer?.nextTrack()
            saveMusicTrackPosition()
        } else {
            nowPlayingService.nextTrack()
        }
    }

    override fun previousTrack() {
        if (musicPlayer?.hasMusic() == true) {
            musicPlayer?.previousTrack()
            saveMusicTrackPosition()
        } else {
            nowPlayingService.previousTrack()
        }
    }

    private fun scheduleSleepTimer() {
        sleepTimerJob?.cancel()
        val minutes = GeneralPrefs.sleepTimer.toLongOrNull() ?: 0L
        if (minutes <= 0L) return
        sleepTimerJob = viewModelScope.launch {
            delay(minutes * 60_000L)
            if (!_uiState.value.blackOutMode) {
                toggleBlackOutMode()
            }
        }
    }

    private fun savePlaybackPosition() {
        if (this::playlist.isInitialized && GeneralPrefs.playlistCache) {
            viewModelScope.launch {
                cacheRepository.saveMediaPosition(playlist.currentPosition)
            }
        }
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
        ktorServer?.stop()
        nowPlayingService.stop()
        weatherService.stop()
        musicPlayer?.pause()
        musicPlayer?.release()
        sleepTimerJob?.cancel()
        metadataJobs.values.forEach { it.cancel() }
        metadataJobs.clear()
    }

    // Pass-through methods for player controls that don't need state in VM yet
    // but could be moved if we want full business logic in VM.
    fun canSkip(can: Boolean) {
        _uiState.update { it.copy(canSkip = can) }
    }

    fun setMetadata(type: OverlayType, text: String, poi: Map<Int, String>, metadataType: MetadataType) {
        _uiState.update { 
            it.copy(overlayState = it.overlayState.copy(
                metadata = it.overlayState.metadata.toMutableMap().apply {
                    put(type, MetadataOverlayState(text, poi, metadataType))
                }
            ))
        }
    }

    // Delegate methods for InputHelper
    // Interaction methods
    override fun increaseSpeed() {
        _uiState.update { it.copy(playbackSpeedChangedEvent = System.currentTimeMillis()) }
    }

    override fun decreaseSpeed() {
        _uiState.update { it.copy(playbackSpeedChangedEvent = System.currentTimeMillis()) }
    }

    override fun seekForward() {
        _uiState.update { it.copy(seekForwardEvent = System.currentTimeMillis()) }
    }

    override fun seekBackward() {
        _uiState.update { it.copy(seekBackwardEvent = System.currentTimeMillis()) }
    }

    override fun increaseBrightness() = changeBrightness(true)
    override fun decreaseBrightness() = changeBrightness(false)

    private fun changeBrightness(increase: Boolean) {
        val currentBrightness = GeneralPrefs.videoBrightness
        val currentIndex = brightnessValues.indexOf(currentBrightness)
        if (currentIndex == -1) return
        if (increase && currentIndex == brightnessValues.size - 1) return
        if (!increase && currentIndex == 0) return

        val newIndex = if (increase) currentIndex + 1 else currentIndex - 1
        val newBrightness = brightnessValues[newIndex]
        GeneralPrefs.videoBrightness = newBrightness
        
        _uiState.update { it.copy(
            brightness = (newBrightness.toFloatOrNull() ?: 100f) / 100f,
            brightnessChangedEvent = System.currentTimeMillis()
        ) }
    }

    override fun showOverlays() {
        _uiState.update { it.copy(showOverlaysEvent = System.currentTimeMillis()) }
    }

    override fun toggleMute() {
        _uiState.update { it.copy(isMuted = !it.isMuted) }
    }

    override fun toggleLooping() {
        _uiState.update { it.copy(isLooping = !it.isLooping) }
    }

    override fun getBlackOutMode(): Boolean = _uiState.value.blackOutMode
}

interface ScreenInteractionHandler {
    fun skipItem(previous: Boolean = false)
    fun nextTrack()
    fun previousTrack()
    fun increaseSpeed()
    fun decreaseSpeed()
    fun seekForward()
    fun seekBackward()
    fun togglePause()
    fun toggleBlackOutMode()
    fun increaseBrightness()
    fun decreaseBrightness()
    fun showOverlays()
    fun toggleMute()
    fun toggleLooping()
    fun getBlackOutMode(): Boolean
}
