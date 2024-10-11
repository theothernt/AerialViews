package com.neilturner.aerialviews.ui.core

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import coil.EventListener
import coil.ImageLoader
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.neilturner.aerialviews.models.enums.AerialMediaSource
import com.neilturner.aerialviews.models.enums.PhotoScale
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.ui.core.ImagePlayerHelper.buildImageLoader
import com.neilturner.aerialviews.ui.core.ImagePlayerHelper.loadImage
import com.neilturner.aerialviews.ui.core.ImagePlayerHelper.loadSambaImage
import com.neilturner.aerialviews.ui.core.ImagePlayerHelper.loadWebDavImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

class ImagePlayerView :
    AppCompatImageView,
    EventListener {
    private var listener: OnImagePlayerEventListener? = null
    private var finishedRunnable = Runnable { listener?.onImageFinished() }
    private var errorRunnable = Runnable { listener?.onImageError() }
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    private val imageLoader: ImageLoader

    init {
        imageLoader = buildImageLoader(context, this)

        val scaleType =
            try {
                ScaleType.valueOf(GeneralPrefs.photoScale.toString())
            } catch (e: Exception) {
                GeneralPrefs.photoScale = PhotoScale.CENTER_CROP
                ScaleType.valueOf(PhotoScale.CENTER_CROP.toString())
            }
        this.scaleType = scaleType
    }

    fun release() {
        removeCallbacks(finishedRunnable)
        removeCallbacks(errorRunnable)
        listener = null
    }

    override fun onSuccess(
        request: ImageRequest,
        result: SuccessResult,
    ) {
        setupFinishedRunnable()
    }

    override fun onError(
        request: ImageRequest,
        result: ErrorResult,
    ) {
        Timber.e(result.throwable, "Exception while loading image: ${result.throwable.message}")
        onPlayerError()
    }

    fun setImage(media: AerialMedia) {
        when (media.source) {
            AerialMediaSource.SAMBA -> {
                coroutineScope.launch { loadSambaImage(this@ImagePlayerView, context, listener, imageLoader, media.uri) }
            }
            AerialMediaSource.WEBDAV -> {
                coroutineScope.launch { loadWebDavImage(this@ImagePlayerView, context, listener, imageLoader,media.uri) }
            }
            else -> {
                coroutineScope.launch { loadImage(this@ImagePlayerView, context, listener, imageLoader,media.uri) }
            }
        }
    }

    fun stop() {
        removeCallbacks(finishedRunnable)
        setImageBitmap(null)
    }

    private fun setupFinishedRunnable() {
        removeCallbacks(finishedRunnable)
        listener?.onImagePrepared()
        // Add fade in/out times?
        val delay = GeneralPrefs.slideshowSpeed.toLong() * 1000
        postDelayed(finishedRunnable, delay)
    }

    private fun onPlayerError() {
        removeCallbacks(finishedRunnable)
        postDelayed(errorRunnable, ScreenController.ERROR_DELAY)
    }

    fun setOnPlayerListener(listener: ScreenController) {
        this.listener = listener
    }

    interface OnImagePlayerEventListener {
        fun onImageFinished()

        fun onImageError()

        fun onImagePrepared()
    }
}
