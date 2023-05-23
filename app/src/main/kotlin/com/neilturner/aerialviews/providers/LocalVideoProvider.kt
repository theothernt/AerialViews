package com.neilturner.aerialviews.providers

import android.content.Context
import android.net.Uri
import android.util.Log
import com.neilturner.aerialviews.models.SearchType
import com.neilturner.aerialviews.models.prefs.LocalVideoPrefs
import com.neilturner.aerialviews.models.videos.AerialVideo
import com.neilturner.aerialviews.utils.FileHelper
import com.neilturner.aerialviews.utils.toStringOrEmpty
import java.io.File

class LocalVideoProvider(context: Context, private val prefs: LocalVideoPrefs) : VideoProvider(context) {

    override fun fetchVideos(): List<AerialVideo> {
        return if (prefs.searchType == SearchType.MEDIASTORE) {
            mediaStoreFetch()
        } else {
            folderAccessFetch()
        }
    }

    private fun folderAccessFetch(): List<AerialVideo> {
        val videos = mutableListOf<AerialVideo>()

        if (prefs.legacy_volume.isEmpty() ||
            prefs.legacy_volume.isEmpty()
        ) {
            return videos
        }

        val externalStorageDir = "${prefs.legacy_volume}${prefs.legacy_folder}"
        val directory = File(externalStorageDir)
        if (directory.exists() && directory.isDirectory) {
            val files = directory.listFiles()
            if (files != null) {
                for (file in files) {
                    videos.add(AerialVideo(Uri.fromFile(file), ""))
                }
            }
        }

        Log.i(TAG, "Videos found by folder access: ${videos.size}")
        return videos
    }

    private fun mediaStoreFetch(): List<AerialVideo> {
        val videos = mutableListOf<AerialVideo>()
        val localVideos = FileHelper.findAllMedia(context)
        var excluded = 0
        var filtered = 0

        for (video in localVideos) {
            val uri = Uri.parse(video)
            val filename = uri.lastPathSegment.toStringOrEmpty()

            if (!FileHelper.isVideoFilename(filename)) {
                excluded++
                continue
            }

            if (prefs.filter_enabled && FileHelper.shouldFilter(uri, prefs.filter_folder_name)) {
                filtered++
                continue
            }

            videos.add(AerialVideo(uri, ""))
        }

        Log.i(TAG, "Videos found by Media Scanner: ${localVideos.size}")
        Log.i(TAG, "Videos with supported file extensions: ${localVideos.size - excluded}")
        Log.i(TAG, "Videos removed by filter: $filtered")
        Log.i(TAG, "Videos selected for playback: ${localVideos.size - (filtered + excluded)}")

        return videos
    }

    companion object {
        private const val TAG = "LocalVideoProvider"
    }
}
