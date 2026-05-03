package com.neilturner.aerialviews.ui.core

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neilturner.aerialviews.data.PlaylistCacheRepository
import com.neilturner.aerialviews.models.LoadingStatus
import com.neilturner.aerialviews.models.MediaPlaylist
import com.neilturner.aerialviews.models.music.MusicPlaylist
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.models.enums.MetadataType
import com.neilturner.aerialviews.models.enums.OverlayType
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
    val overlayState: OverlayUiState = OverlayUiState()
)

class ScreenViewModel(
    private val context: Context,
    private val mediaService: MediaService,
    private val nowPlayingService: NowPlayingService,
    private val weatherService: WeatherService,
    private val cacheRepository: PlaylistCacheRepository,
    private val messageRepository: MessageRepository,
    private val progressRepository: PlaybackProgressRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScreenUiState())
    val uiState: StateFlow<ScreenUiState> = _uiState.asStateFlow()

    private lateinit var playlist: MediaPlaylist
    private var musicPlayer: MusicPlayer? = null
    private var ktorServer: KtorServer? = null
    private var sleepTimerJob: Job? = null
    
    private val shouldAlternateOverlays = GeneralPrefs.alternateTextPosition

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
                
                // Initialize services that were previously in ScreenController init/launch
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
            savePlaybackPosition()
        }
    }

    fun togglePause() {
        _uiState.update { it.copy(isPaused = !it.isPaused) }
    }

    fun toggleBlackOutMode() {
        val newMode = !_uiState.value.blackOutMode
        _uiState.update { it.copy(blackOutMode = newMode) }
        if (newMode) {
            sleepTimerJob?.cancel()
        } else {
            loadNextItem()
            scheduleSleepTimer()
        }
    }

    fun skipItem(previous: Boolean = false) {
        if (previous) loadPreviousItem() else loadNextItem()
    }

    fun nextTrack() {
        if (musicPlayer?.hasMusic() == true) {
            musicPlayer?.nextTrack()
            saveMusicTrackPosition()
        } else {
            nowPlayingService.nextTrack()
        }
    }

    fun previousTrack() {
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
    fun increaseSpeed() { /* TODO: Implement in VM if needed */ }
    fun decreaseSpeed() { /* TODO: Implement in VM if needed */ }
    fun seekForward() { /* TODO: Implement in VM if needed */ }
    fun seekBackward() { /* TODO: Implement in VM if needed */ }
    fun increaseBrightness() { /* TODO: Implement in VM if needed */ }
    fun decreaseBrightness() { /* TODO: Implement in VM if needed */ }
    fun showOverlays() { /* TODO: Implement in VM if needed */ }
    fun toggleMute() { /* TODO: Implement in VM if needed */ }
    fun toggleLooping() { /* TODO: Implement in VM if needed */ }
}
