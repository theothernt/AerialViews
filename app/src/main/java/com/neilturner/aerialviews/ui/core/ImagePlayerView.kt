package com.neilturner.aerialviews.ui.core

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import androidx.appcompat.widget.AppCompatImageView
import coil.EventListener
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.share.DiskShare
import com.neilturner.aerialviews.models.enums.AerialMediaSource
import com.neilturner.aerialviews.models.enums.PhotoScale
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.models.prefs.SambaMediaPrefs
import com.neilturner.aerialviews.models.prefs.WebDavMediaPrefs
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.utils.SambaHelper
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.EnumSet

class ImagePlayerView : AppCompatImageView,
    EventListener {
    private var imageLoader: ImageLoader
    private var listener: OnImagePlayerEventListener? = null
    private var finishedRunnable = Runnable { listener?.onImageFinished() }
    private var errorRunnable = Runnable { listener?.onImageError() }
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    init {
        imageLoader =
            ImageLoader
                .Builder(context)
                .eventListener(this)
                .components {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        add(ImageDecoderDecoder.Factory())
                    } else {
                        add(GifDecoder.Factory())
                    }
                }
                .build()

        val scaleType =
            try {
                ScaleType.valueOf(GeneralPrefs.photoScale.toString())
            } catch (ex: Exception) {
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
        Log.e(TAG, "Exception while loading image: ${result.throwable.message}")
        onPlayerError()
    }

    fun setImage(media: AerialMedia) {
        when (media.source) {
            AerialMediaSource.SAMBA -> {
                coroutineScope.launch { loadSambaImage(media.uri) }
            }
            AerialMediaSource.WEBDAV -> {
                coroutineScope.launch { loadWebDavImage(media.uri) }
            }
            else -> {
                coroutineScope.launch { loadImage(media.uri) }
            }
        }
    }

    private suspend fun loadImage(uri: Uri) {
        val request =
            ImageRequest.Builder(context)
                .target(this)
        request.data(uri)
        imageLoader.execute(request.build())
    }

    private suspend fun loadSambaImage(uri: Uri) {
        val request =
            ImageRequest
                .Builder(context)
                .target(this)

        try {
            val byteArray = byteArrayFromSambaFile(uri)
            request.data(byteArray)
        } catch (ex: Exception) {
            Log.e(TAG, "Exception while getting byte array from SMB share: ${ex.message}")
            listener?.onImageError()
            return
        }

        imageLoader.execute(request.build())
    }

    private suspend fun loadWebDavImage(uri: Uri) {
        val request =
            ImageRequest
                .Builder(context)
                .target(this)

        try {
            val byteArray = byteArrayFromWebDavFile(uri)
            request.data(byteArray)
        } catch (ex: Exception) {
            Log.e(TAG, "Exception while getting byte array from WebDAV resource: ${ex.message}")
            listener?.onImageError()
            return
        }

        imageLoader.execute(request.build())
    }

    fun stop() {
        removeCallbacks(finishedRunnable)
        setImageBitmap(null)
    }

    private suspend fun byteArrayFromWebDavFile(uri: Uri): ByteArray =
        withContext(Dispatchers.IO) {
            val client = OkHttpSardine()
            client.setCredentials(WebDavMediaPrefs.userName, WebDavMediaPrefs.password)
            return@withContext client.get(uri.toString()).readBytes()
        }

    private suspend fun byteArrayFromSambaFile(uri: Uri): ByteArray =
        withContext(Dispatchers.IO) {
            val shareNameAndPath = SambaHelper.parseShareAndPathName(uri)
            val shareName = shareNameAndPath.first
            val path = shareNameAndPath.second

            val config = SambaHelper.buildSmbConfig()
            val smbClient = SMBClient(config)
            val authContext = SambaHelper.buildAuthContext(SambaMediaPrefs.userName, SambaMediaPrefs.password, SambaMediaPrefs.domainName)

            smbClient.connect(SambaMediaPrefs.hostName).use { connection ->
                val session = connection?.authenticate(authContext)
                val share = session?.connectShare(shareName) as DiskShare
                val shareAccess = hashSetOf<SMB2ShareAccess>()
                shareAccess.add(SMB2ShareAccess.ALL.iterator().next())
                val file =
                    share.openFile(
                        path,
                        EnumSet.of(AccessMask.GENERIC_READ),
                        null,
                        shareAccess,
                        SMB2CreateDisposition.FILE_OPEN,
                        null,
                    )
                return@withContext file.inputStream.readBytes()
            }
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

    companion object {
        private const val TAG = "ImagePlayerView"
    }

    interface OnImagePlayerEventListener {
        fun onImageFinished()

        fun onImageError()

        fun onImagePrepared()
    }
}
