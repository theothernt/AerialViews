package com.neilturner.aerialviews.ui.screensaver

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.AttributeSet
import android.util.Log
import androidx.appcompat.widget.AppCompatImageView
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.File
import com.neilturner.aerialviews.models.prefs.SambaVideoPrefs
import com.neilturner.aerialviews.utils.FileHelper
import com.neilturner.aerialviews.utils.SambaHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.EnumSet

class ImagePlayerView : AppCompatImageView {
    private var listener: OnImagePlayerEventListener? = null
    private var finishedRunnable = Runnable { listener?.onImageFinished() }
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    init {
        this.scaleType = ScaleType.FIT_XY
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
    }

    fun setUri(uri: Uri?) {
        if (uri == null) {
            return
        }

        // Only SMB for the moment
        if (!FileHelper.isSambaVideo(uri)) {
            return
        }

        coroutineScope.launch {
            val inputStream = openSambaFile(uri).inputStream
            val byteArray = inputStream.readBytes()
            val bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
            super.setImageBitmap(bitmap)

            listener?.onImagePrepared()
            setupFinishedRunnable()
            Log.i(TAG, "Image loaded, listeners setup...")
        }
    }

    private suspend fun openSambaFile(uri: Uri): File = withContext(Dispatchers.IO) {
        val shareNameAndPath = SambaHelper.parseShareAndPathName(uri)
        val shareName = shareNameAndPath.first
        val path = shareNameAndPath.second

        val config = SambaHelper.buildSmbConfig()
        val smbClient = SMBClient(config)
        val connection = smbClient.connect(SambaVideoPrefs.hostName)
        val authContext = SambaHelper.buildAuthContext(SambaVideoPrefs.userName, SambaVideoPrefs.password, SambaVideoPrefs.domainName)
        val session = connection?.authenticate(authContext)
        val share = session?.connectShare(shareName) as DiskShare

        val shareAccess = hashSetOf<SMB2ShareAccess>()
        shareAccess.add(SMB2ShareAccess.ALL.iterator().next())

        return@withContext share.openFile(
            path,
            EnumSet.of(AccessMask.GENERIC_READ),
            null,
            shareAccess,
            SMB2CreateDisposition.FILE_OPEN,
            null
        )
    }

    private fun setupFinishedRunnable() {
        removeCallbacks(finishedRunnable)

        val delay: Long = 1000 * 5
        postDelayed(finishedRunnable, delay)
    }

    fun setOnPlayerListener(listener: VideoController) {
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
