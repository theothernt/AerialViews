package com.neilturner.aerialviews.providers

import android.content.Context
import android.net.Uri
import com.neilturner.aerialviews.models.SearchType
import com.neilturner.aerialviews.models.prefs.LocalVideoPrefs
import com.neilturner.aerialviews.models.videos.AerialVideo
import com.neilturner.aerialviews.utils.FileHelper
import com.neilturner.aerialviews.utils.toStringOrEmpty
import java.io.File

class LocalVideoProvider(context: Context, private val prefs: LocalVideoPrefs) : VideoProvider(context) {

    override fun fetchVideos(): List<AerialVideo> {
        return if (prefs.searchType == SearchType.MEDIA_STORE) {
            mediaStoreFetch().first
        } else {
            folderAccessFetch().first
        }
    }

    fun fetchTest(): String {
        return if (prefs.searchType == SearchType.MEDIA_STORE) {
            mediaStoreFetch().second
        } else {
            folderAccessFetch().second
        }
    }

    private fun folderAccessFetch(): Pair<List<AerialVideo>, String> {
        val videos = mutableListOf<AerialVideo>()
        var excluded = 0

        if (prefs.legacy_volume.isEmpty() ||
            prefs.legacy_folder.isEmpty()
        ) {
            return Pair(videos, "Volume or folder is empty")
        }

        val externalStorageDir = "${prefs.legacy_volume}${prefs.legacy_folder}"
        val directory = File(externalStorageDir)

        if (!directory.exists() || !directory.isDirectory) {
            return Pair(videos, "Folder doesn't exist")
        }

        val files = directory.listFiles() ?: return Pair(videos, "No files found")

        for (file in files) {
            if (FileHelper.isVideoFilename(file.name)) {
                excluded++
                continue
            }

            videos.add(AerialVideo(Uri.fromFile(file), ""))
        }

        var message = "Videos found in folder: ${videos.size}\n"
        message += "Videos with supported file extensions: ${videos.size - excluded}\n"
        message += "Videos selected for playback: ${videos.size - excluded}"

        return Pair(videos, message)
    }

    private fun mediaStoreFetch(): Pair<List<AerialVideo>, String> {
        val videos = mutableListOf<AerialVideo>()
        val localVideos = FileHelper.findAllMedia(context)
        var excluded = 0
        var filtered = 0

        if (prefs.filter_folder.isEmpty() &&
            prefs.filter_enabled
        ) {
            return Pair(videos, "No folder specified for filter")
        }

        for (video in localVideos) {
            val uri = Uri.parse(video)
            val filename = uri.lastPathSegment.toStringOrEmpty()

            if (!FileHelper.isVideoFilename(filename)) {
                excluded++
                continue
            }

            if (prefs.filter_enabled && FileHelper.shouldFilter(uri, prefs.filter_folder)) {
                filtered++
                continue
            }

            videos.add(AerialVideo(uri, ""))
        }

        var message = "Videos found by media scanner: ${localVideos.size}\n"
        message += "Videos with supported file extensions: ${localVideos.size - excluded}\n"
        message += if (prefs.filter_enabled) {
            "Videos removed by filter: $filtered\n"
        } else {
            "Videos removed by filter: (disabled)\n"
        }
        message += "Videos selected for playback: ${localVideos.size - (filtered + excluded)}"

        return Pair(videos, message)
    }

    companion object {
        private const val TAG = "LocalVideoProvider"
    }
}
