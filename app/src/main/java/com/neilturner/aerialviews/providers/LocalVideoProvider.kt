@file:Suppress("unused")

package com.neilturner.aerialviews.providers

import android.content.Context
import android.net.Uri
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.SearchType
import com.neilturner.aerialviews.models.prefs.LocalVideoPrefs
import com.neilturner.aerialviews.models.videos.AerialVideo
import com.neilturner.aerialviews.models.videos.VideoMetadata
import com.neilturner.aerialviews.utils.FileHelper
import com.neilturner.aerialviews.utils.StorageHelper
import com.neilturner.aerialviews.utils.toStringOrEmpty
import java.io.File

class LocalVideoProvider(context: Context, private val prefs: LocalVideoPrefs) : VideoProvider(context) {

    override val enabled: Boolean
        get() = prefs.enabled

    override fun fetchVideos(): List<AerialVideo> {
        return if (prefs.searchType == SearchType.MEDIA_STORE) {
            mediaStoreFetch().first
        } else {
            folderAccessFetch().first
        }
    }

    override fun fetchTest(): String {
        return if (prefs.searchType == SearchType.MEDIA_STORE) {
            mediaStoreFetch().second
        } else {
            folderAccessFetch().second
        }
    }

    override fun fetchMetadata(): List<VideoMetadata> {
        return emptyList()
    }

    private fun folderAccessFetch(): Pair<List<AerialVideo>, String> {
        val res = context.resources!!
        val videos = mutableListOf<AerialVideo>()
        val allFiles = mutableListOf<File>()
        var foldersFound = 0
        var filesFound = 0
        var excluded = 0

        if (prefs.legacy_volume.isEmpty()) {
            return Pair(videos, res.getString(R.string.local_videos_legacy_no_volume))
        }

        if (prefs.legacy_folder.isEmpty()) {
            return Pair(videos, res.getString(R.string.local_videos_legacy_no_folder))
        }

        val folders = mutableListOf<String>()
        if (prefs.legacy_volume.contains("/all", false)) {
            val vols = StorageHelper.getStoragePaths(context)
            val values = vols.map { it.key }.toTypedArray()
            for (entry in values) {
                folders.add("$entry${prefs.legacy_folder}")
            }
        } else {
            folders.add("${prefs.legacy_volume}${prefs.legacy_folder}")
        }

        for (folder in folders) {
            val directory = File(folder)
            if (!directory.exists() || !directory.isDirectory) {
                continue
            }
            foldersFound++
            val files = directory.listFiles()
            if (!files.isNullOrEmpty()
            ) {
                filesFound = +files.size
                allFiles.addAll(files)
            }
        }

        if (foldersFound == 0) {
            return Pair(videos, res.getString(R.string.local_videos_legacy_no_folder_found))
        }

        if (filesFound == 0) {
            return Pair(videos, res.getString(R.string.local_videos_legacy_no_folder_found))
        }

        for (file in allFiles) {
            // x/y/file.mp4
            if (FileHelper.isDotOrHiddenFile(file.name.split("/").last())) {
                continue
            }

            if (!FileHelper.isSupportedVideoType(file.name)) {
                excluded++
                continue
            }

            videos.add(AerialVideo(Uri.fromFile(file)))
        }

        var message = String.format(res.getString(R.string.local_videos_legacy_test_summary1), videos.size + excluded) + "\n"
        message += String.format(res.getString(R.string.local_videos_legacy_test_summary2), excluded) + "\n"
        message += String.format(res.getString(R.string.local_videos_legacy_test_summary3), videos.size)

        return Pair(videos, message)
    }

    private fun mediaStoreFetch(): Pair<List<AerialVideo>, String> {
        val res = context.resources!!
        val videos = mutableListOf<AerialVideo>()
        val localVideos = FileHelper.findAllMedia(context)
        var excluded = 0
        var filtered = 0

        if (prefs.filter_folder.isEmpty() &&
            prefs.filter_enabled
        ) {
            return Pair(videos, res.getString(R.string.local_videos_media_store_no_folder))
        }

        for (video in localVideos) {
            val uri = Uri.parse(video)
            val filename = uri.lastPathSegment.toStringOrEmpty()

            // file.mp4
            if (FileHelper.isDotOrHiddenFile(filename)) {
                continue
            }

            if (!FileHelper.isSupportedVideoType(filename)) {
                excluded++
                continue
            }

            if (prefs.filter_enabled && FileHelper.shouldFilter(uri, prefs.filter_folder)) {
                filtered++
                continue
            }

            videos.add(AerialVideo(uri))
        }

        var message = String.format(res.getString(R.string.local_videos_media_store_test_summary1), localVideos.size) + "\n"
        message += String.format(res.getString(R.string.local_videos_media_store_test_summary2), excluded) + "\n"
        message += String.format(res.getString(R.string.local_videos_media_store_test_summary3), filtered) + "\n"
        message += String.format(res.getString(R.string.local_videos_media_store_test_summary4), videos.size)

        return Pair(videos, message)
    }

    companion object {
        private const val TAG = "LocalVideoProvider"
    }
}
