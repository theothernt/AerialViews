package com.neilturner.aerialviews.ui.screensaver

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView

class ImagePlayerView: AppCompatImageView {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    init {

    }

    fun setUri(uri: Uri?) {
        if (uri == null) {
            return
        }
    }

    override fun setImageBitmap(bm: Bitmap?) {
        super.setImageBitmap(bm)
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

