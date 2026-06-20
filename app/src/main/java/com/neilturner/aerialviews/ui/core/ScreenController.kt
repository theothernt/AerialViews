package com.neilturner.aerialviews.ui.core

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.data.PlaylistCacheRepository
import com.neilturner.aerialviews.models.LoadingStatus
import com.neilturner.aerialviews.models.MediaPlaylist
import com.neilturner.aerialviews.models.enums.AerialMediaType
import com.neilturner.aerialviews.models.enums.ProgressBarLocation
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.services.MediaService
import com.neilturner.aerialviews.services.MusicPlayer
import com.neilturner.aerialviews.ui.controls.ProgressBarEvent
import com.neilturner.aerialviews.ui.controls.ProgressState
import com.neilturner.aerialviews.ui.core.ImagePlayerView.OnImagePlayerEventListener
import com.neilturner.aerialviews.ui.core.VideoPlayerView.OnVideoPlayerEventListener
import com.neilturner.aerialviews.ui.helpers.ColourHelper
import com.neilturner.aerialviews.ui.helpers.RefreshRateHelper
import com.neilturner.aerialviews.ui.helpers.ToastHelper
import com.neilturner.aerialviews.ui.helpers.WindowHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.kosert.flowbus.GlobalBus
import timber.log.Timber

class ScreenController(
    val context: Context,
) : OnVideoPlayerEventListener,
    OnImagePlayerEventListener {
    private val mainScope = CoroutineScope(Dispatchers.Main)
    private lateinit var playlist: MediaPlaylist
    private val resources by lazy { context.resources }
    private var isStopped = false

    private var musicPlayer: MusicPlayer? = null
    private val playlistCacheRepository =
        if (GeneralPrefs.playlistCache) PlaylistCacheRepository(context) else null

    var onMetadataUpdate: ((AerialMedia) -> Unit)? = null
    var onOverlayReset: (() -> Unit)? = null
    var onLoadingStateUpdate: ((Boolean, String, Boolean) -> Unit)? = null

    private var previousItem = false
    private var canSkip = false
    private var isPaused = false
    private var pauseStartTime: Long = 0
    private var currentMedia: AerialMedia? = null

    val videoPlayer: VideoPlayerView
    val imagePlayer: ImagePlayerView
    val view: View

    var blackOutMode = false
        private set

    init {
        val backgroundVideos = ColourHelper.colourFromString(GeneralPrefs.backgroundVideos)
        val backgroundPhotos = ColourHelper.colourFromString(GeneralPrefs.backgroundPhotos)

        val rootLayout = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            keepScreenOn = true
        }
        view = rootLayout

        val videoLayoutRes =
            if (GeneralPrefs.useTextureViewForVideo) {
                R.layout.video_view_texture
            } else {
                R.layout.video_view
            }
        val videoContainer = LayoutInflater.from(context).inflate(videoLayoutRes, rootLayout, false) as FrameLayout
        videoContainer.setBackgroundColor(backgroundVideos)
        videoPlayer = videoContainer.findViewById(R.id.video_player)
        videoPlayer.setOnPlayerListener(this)
        rootLayout.addView(videoContainer)

        val imageContainer = LayoutInflater.from(context).inflate(R.layout.image_view, rootLayout, false) as FrameLayout
        imageContainer.setBackgroundColor(backgroundPhotos)
        imagePlayer = imageContainer.findViewById(R.id.image_player)
        imagePlayer.setOnPlayerListener(this)
        rootLayout.addView(imageContainer)

        if (GeneralPrefs.ignoreAnimationScale) {
            WindowHelper.resetSystemAnimationDuration(context)
        }

        mainScope.launch {
            val mediaResult =
                MediaService(context).fetchMedia { status ->
                    mainScope.launch {
                        val text =
                            when (status) {
                                LoadingStatus.RESUMING -> resources.getString(R.string.loading_resuming)
                                LoadingStatus.BUILDING -> resources.getString(R.string.loading_building)
                                LoadingStatus.LOADING -> resources.getString(R.string.loading_title)
                            }
                        onLoadingStateUpdate?.invoke(true, text, true)
                    }
                }
            playlist = mediaResult.mediaPlaylist
            playlist.onPositionChanged = { position ->
                playlistCacheRepository?.let { repo ->
                    mainScope.launch { repo.saveMediaPosition(position) }
                }
            }
            if (playlist.size > 0) {
                Timber.i("Playlist size: ${playlist.size}")
                loadNextItem()
            } else {
                val errorText = resources.getString(R.string.loading_error)
                onLoadingStateUpdate?.invoke(true, errorText, false)
            }

            setupMusicPlayer(mediaResult.musicPlaylist, mediaResult.musicResumeIndex)
        }
    }

    private fun setupMusicPlayer(
        musicPlaylist: com.neilturner.aerialviews.models.music.MusicPlaylist?,
        resumeIndex: Int = 0,
    ) {
        val backgroundMusicSelected = GeneralPrefs.playsBackgroundMusic
        videoPlayer.setForcedMute(backgroundMusicSelected)

        if (!backgroundMusicSelected) {
            Timber.i("MusicPlayer: background music not selected, skipping")
            return
        }

        if (musicPlaylist == null || musicPlaylist.size == 0) {
            Timber.i("MusicPlayer: no music playlist available, skipping")
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
        val media =
            if (previous) {
                playlist.previousItem()
            } else {
                playlist.nextItem()
            }
        loadItem(media)
    }

    private fun loadItem(media: AerialMedia) {
        isPaused = false
        pauseStartTime = 0
        currentMedia = media
        onOverlayReset?.invoke()

        if (media.uri.toString().contains("smb://")) {
            val pattern = Regex("(smb://)([^:]+):([^@]+)@([\\d.]+)/")
            val replacement = "$1****:****@****/"
            val url = pattern.replace(media.uri.toString(), replacement)
            Timber.i("Loading: ${media.metadata.shortDescription} - $url (${media.metadata.pointsOfInterest})")
        } else {
            Timber.i("Loading: ${media.metadata.shortDescription} - ${media.uri} (${media.metadata.pointsOfInterest})")
        }

        onMetadataUpdate?.invoke(media)

        if (media.type == AerialMediaType.VIDEO) {
            videoPlayer.setVideo(media)
        }

        if (media.type == AerialMediaType.IMAGE) {
            imagePlayer.setImage(media)
        }

        if (GeneralPrefs.progressBarLocation != ProgressBarLocation.DISABLED) {
            GlobalBus.post(ProgressBarEvent(ProgressState.RESET))
        }

        videoPlayer.start()
    }

    private fun fadeInNextItem() {
        onLoadingStateUpdate?.invoke(false, "", false)
        canSkip = true
    }

    private fun fadeOutCurrentItem() {
        if (!canSkip) return
        canSkip = false

        if (currentMedia?.type == AerialMediaType.VIDEO) {
            videoPlayer.fadeOutAudio(GeneralPrefs.mediaFadeOutDuration.toLong())
        }

        onLoadingStateUpdate?.invoke(true, "", false)

        // Wait for loading overlay to fully fade in before swapping content
        val fadeOutDuration = GeneralPrefs.mediaFadeOutDuration.toLongOrNull() ?: 800L
        mainScope.launch {
            delay(fadeOutDuration)

            videoPlayer.stop()
            imagePlayer.stop()

            isPaused = false
            pauseStartTime = 0

            if (!blackOutMode) {
                val loadPreviousItem = previousItem
                previousItem = false
                loadNextItem(loadPreviousItem)
            } else {
                previousItem = false
            }
        }
    }

    fun detachViewsForCompose() {
        (videoPlayer.parent as? ViewGroup)?.removeView(videoPlayer)
        (imagePlayer.parent as? ViewGroup)?.removeView(imagePlayer)
    }

    fun stop() {
        if (isStopped) return
        isStopped = true
        RefreshRateHelper.restoreOriginalMode(context)
        videoPlayer.release()
        imagePlayer.release()
        musicPlayer?.pause()
        musicPlayer?.release()
        mainScope.cancel()
    }

    fun skipItem(previous: Boolean = false) {
        previousItem = previous
        fadeOutCurrentItem()
    }

    fun toggleBlackOutMode() {
        if (!this::playlist.isInitialized || playlist.size == 0) {
            return
        }

        if (!blackOutMode) {
            blackOutMode = true
            fadeOutCurrentItem()
        } else {
            blackOutMode = false
            loadNextItem()
        }
    }

    fun nextTrack() {
        musicPlayer?.let {
            if (it.hasMusic()) {
                it.nextTrack()
            }
        }
    }

    fun previousTrack() {
        musicPlayer?.let {
            if (it.hasMusic()) {
                it.previousTrack()
            }
        }
    }

    fun increaseSpeed() {
        if (blackOutMode) return
        videoPlayer.increaseSpeed()
    }

    fun decreaseSpeed() {
        if (blackOutMode) return
        videoPlayer.decreaseSpeed()
    }

    fun seekForward() {
        if (blackOutMode) return
        videoPlayer.seekForward()
    }

    fun seekBackward() {
        if (blackOutMode) return
        videoPlayer.seekBackward()
    }

    fun togglePause() {
        if (isPaused) {
            resumeMedia()
        } else {
            pauseMedia()
        }
    }

    fun toggleLooping() {
        videoPlayer.toggleLooping()
    }

    fun increaseBrightness() = changeBrightness(true)

    fun decreaseBrightness() = changeBrightness(false)

    private fun changeBrightness(increase: Boolean) {
        if (blackOutMode) return

        val brightnessValues = resources.getStringArray(R.array.percentage1_values)
        val currentBrightness = GeneralPrefs.videoBrightness
        val currentIndex = brightnessValues.indexOf(currentBrightness)

        if (currentIndex == -1) return
        if (increase && currentIndex == brightnessValues.size - 1) return
        if (!increase && currentIndex == 0) return

        val newIndex = if (increase) currentIndex + 1 else currentIndex - 1
        val newBrightness = brightnessValues[newIndex]
        GeneralPrefs.videoBrightness = newBrightness

        mainScope.launch {
            ToastHelper.show(context, "Brightness: $newBrightness%")
        }
    }

    fun toggleMute() {
        videoPlayer.toggleMute()
    }

    private fun pauseMedia() {
        if (isPaused) return
        isPaused = true
        pauseStartTime = System.currentTimeMillis()
        videoPlayer.pause()
        imagePlayer.pauseTimer()

        if (GeneralPrefs.progressBarLocation != ProgressBarLocation.DISABLED) {
            GlobalBus.post(ProgressBarEvent(ProgressState.PAUSE))
        }
    }

    private fun resumeMedia() {
        if (!isPaused) return
        isPaused = false
        val pauseDuration = System.currentTimeMillis() - pauseStartTime
        videoPlayer.resume()
        imagePlayer.resumeTimer(pauseDuration)

        if (GeneralPrefs.progressBarLocation != ProgressBarLocation.DISABLED) {
            GlobalBus.post(ProgressBarEvent(ProgressState.RESUME))
        }
    }

    private fun handleError() {
        mainScope.launch {
            delay(ERROR_DELAY)
            loadNextItem()
        }
    }

    override fun onVideoPlaybackSpeedChanged() {
        val message = resources.getString(R.string.playlist_playback_speed_changed, GeneralPrefs.playbackSpeed + "x")
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    override fun onVideoAlmostFinished() = fadeOutCurrentItem()

    override fun onVideoPrepared() = fadeInNextItem()

    override fun onVideoError() = handleError()

    override fun onVideoMetadataExtracted(metadata: ExtractedVideoMetadata) {
        val media = currentMedia ?: return
        Timber.i("Video metadata: %s", formatVideoMetadataForLog(metadata))
        applyVideoMetadataToMedia(media, metadata)
        onMetadataUpdate?.invoke(media)
    }

    override fun onImageFinished() = fadeOutCurrentItem()

    override fun onImageError() = handleError()

    override fun onImagePrepared() {
        Timber.d("onImagePrepared")
        currentMedia
            ?.takeIf { it.type == AerialMediaType.IMAGE }
            ?.let { onMetadataUpdate?.invoke(it) }
        fadeInNextItem()
    }

    fun showOverlays() {
        // No-op: Compose handles overlay visibility via OverlayLayout
    }

    companion object {
        const val ERROR_DELAY: Long = 2000
    }
}
