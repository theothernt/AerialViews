package com.neilturner.aerialviews.ui.core

import android.content.Context
import android.net.Uri
import android.util.AttributeSet
import android.util.Log
import androidx.appcompat.widget.AppCompatImageView
import coil.EventListener
import coil.ImageLoader
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.share.DiskShare
import com.neilturner.aerialviews.models.enums.PhotoScale
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.models.prefs.SambaMediaPrefs
import com.neilturner.aerialviews.utils.FileHelper
import com.neilturner.aerialviews.utils.SambaHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.EnumSet

class ImagePlayerView : AppCompatImageView {
    private var imageLoader: ImageLoader
    private var listener: OnImagePlayerEventListener? = null
    private var finishedRunnable = Runnable { listener?.onImageFinished() }
    private var errorRunnable = Runnable { listener?.onImageError() }
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    init {
        imageLoader = ImageLoader.Builder(context)
            .eventListener(object : EventListener {
                override fun onSuccess(request: ImageRequest, result: SuccessResult) {
                    setupFinishedRunnable()
                }

                override fun onError(request: ImageRequest, result: ErrorResult) {
                    Log.e(TAG, "Exception while loading image: ${result.throwable.message}")
                    onPlayerError()
                }
            }).build()

        val scaleType = try {
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
    fun setUri(uri: Uri?) {
        if (uri == null) {
            return
        }

        coroutineScope.launch {
            loadImage(uri)
        }
    }

    private suspend fun loadImage(uri: Uri) {
        val request = ImageRequest.Builder(context)
            .target(this)

        if (FileHelper.isSambaVideo(uri)) {
            try {
                val byteArray = byteArrayFromSambaFile(uri)
                request.data(byteArray)
            } catch (ex: Exception) {
                Log.e(TAG, "Exception while getting byte array from SMB share: ${ex.message}")
                listener?.onImageError()
                return
            }
        } else {
            request.data(uri)
        }
        imageLoader.execute(request.build())
    }

    fun stop() {
        removeCallbacks(finishedRunnable)
        setImageBitmap(null)
    }

    private suspend fun byteArrayFromSambaFile(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
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
            val file = share.openFile(
                path,
                EnumSet.of(AccessMask.GENERIC_READ),
                null,
                shareAccess,
                SMB2CreateDisposition.FILE_OPEN,
                null
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
