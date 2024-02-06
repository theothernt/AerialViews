package com.neilturner.aerialviews.ui.core

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
    private var errorRunnable = Runnable { listener?.onImageError() }
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
            try {
                val bitmap = if (FileHelper.isSambaVideo(uri)) {
                    val byteArray = getSambaByteArray(uri)
                    BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
                } else {
                    BitmapFactory.decodeFile(uri.path)
                }
                super.setImageBitmap(bitmap)
            } catch (ex: Exception) {
                Log.e(TAG, "Exception while loading image: ${ex.message}")
                onPlayerError()
                return@launch
            }
            listener?.onImagePrepared()
            setupFinishedRunnable()
        }
    }

    private suspend fun getSambaByteArray(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
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

    private fun setupFinishedRunnable() {
        removeCallbacks(finishedRunnable)

        val delay: Long = 1000 * 8
        postDelayed(finishedRunnable, delay)
    }

    private fun onPlayerError() {
        removeCallbacks(finishedRunnable)
        postDelayed(errorRunnable, 2000)
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
