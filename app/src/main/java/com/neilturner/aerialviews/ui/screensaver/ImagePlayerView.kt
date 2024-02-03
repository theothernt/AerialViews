package com.neilturner.aerialviews.ui.screensaver


class ImagePlayerView {

    init {

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

