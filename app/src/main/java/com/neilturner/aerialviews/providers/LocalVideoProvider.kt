package com.neilturner.aerialviews.providers

import android.content.Context
import android.net.Uri
import com.neilturner.aerialviews.models.SearchType
import com.neilturner.aerialviews.models.prefs.LocalVideoPrefs
import com.neilturner.aerialviews.models.videos.AerialVideo
import com.neilturner.aerialviews.models.videos.VideoMetadata
import com.neilturner.aerialviews.utils.FileHelper
import com.neilturner.aerialviews.utils.StorageHelper
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
        val videos = mutableListOf<AerialVideo>()
        val allFiles = mutableListOf<File>()
        var foldersFound = 0
        var filesFound = 0
        var excluded = 0

        if (prefs.legacy_volume.isEmpty()) {
            return Pair(videos, "Volume not specified")
        }

        if (prefs.legacy_folder.isEmpty()) {
            return Pair(videos, "Folder not specified")
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
            if (files != null &&
                files.isNotEmpty()
            ) {
                filesFound = +files.size
                allFiles.addAll(files)
            }
        }

        if (foldersFound == 0) {
            return Pair(videos, "Folder does not exist")
        }

        if (filesFound == 0) {
            return Pair(videos, "No files found")
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

        var message = "Videos found in folder: ${videos.size + excluded}\n"
        message += "Videos with unsupported file extensions: $excluded\n"
        message += "Videos selected for playback: ${videos.size}"

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

        var message = "Videos found by media scanner: ${localVideos.size}\n"
        message += "Videos with unsupported file extensions: $excluded\n"
        message += if (prefs.filter_enabled) {
            "Videos removed by filter: $filtered\n"
        } else {
            "Videos removed by filter: (disabled)\n"
        }
        message += "Videos selected for playback: ${videos.size}"

        return Pair(videos, message)
    }

    companion object {
        private const val TAG = "LocalVideoProvider"
    }
}
