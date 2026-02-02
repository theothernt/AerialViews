package com.neilturner.aerialviews.providers

import android.content.Context
import androidx.core.net.toUri
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.AerialMediaType
import com.neilturner.aerialviews.models.enums.ProviderMediaType
import com.neilturner.aerialviews.models.enums.ProviderSourceType
import com.neilturner.aerialviews.models.enums.SearchType
import com.neilturner.aerialviews.models.prefs.LocalProviderPreferences
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.utils.FileHelper
import com.neilturner.aerialviews.utils.StorageHelper
import com.neilturner.aerialviews.utils.filename
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class LocalMediaProvider(
	context: Context,
	private val prefs: LocalProviderPreferences,
) : MediaProvider(context) {
    override val type = ProviderSourceType.LOCAL

    override val enabled: Boolean
        get() = prefs.enabled

    override suspend fun fetchMedia(): List<AerialMedia> =
        if (prefs.searchType == SearchType.MEDIA_STORE) {
            mediaStoreFetch().first
        } else {
            folderAccessFetch().first
        }

    override suspend fun fetchTest(): String =
        if (prefs.searchType == SearchType.MEDIA_STORE) {
            mediaStoreFetch().second
        } else {
            folderAccessFetch().second
        }

    override suspend fun fetchMetadata(): MutableMap<String, Pair<String, Map<Int, String>>> = mutableMapOf()

    private suspend fun folderAccessFetch(): Pair<List<AerialMedia>, String> {
        val res = context.resources
        val selected = mutableListOf<String>()
        val media = mutableListOf<AerialMedia>()
        val excluded: Int
        val images: Int

        if (prefs.legacyVolume.isEmpty()) {
            return Pair(media, res.getString(R.string.local_videos_legacy_no_volume))
        }

        if (prefs.legacyFolder.isEmpty()) {
            return Pair(media, res.getString(R.string.local_videos_legacy_no_folder))
        }

        val files = folderAccessVideosAndImages()

        if (files.isEmpty()) {
            return Pair(media, res.getString(R.string.local_videos_legacy_no_files_found))
        }

        // Only pick videos
        if (prefs.mediaType != ProviderMediaType.PHOTOS) {
            selected.addAll(
                files.filter { file ->
                    FileHelper.isSupportedVideoType(file)
                },
            )
        }
        val videos = selected.size

        // Only pick images
        if (prefs.mediaType != ProviderMediaType.VIDEOS) {
            selected.addAll(
                files.filter { file ->
                    FileHelper.isSupportedImageType(file)
                },
            )
        }
        images = selected.size - videos
        excluded = files.size - selected.size

        // Create media list, adding media type
        for (file in selected) {
            val uri = file.toUri()
            val item = AerialMedia(uri)
            if (FileHelper.isSupportedVideoType(file)) {
                item.type = AerialMediaType.VIDEO
            } else if (FileHelper.isSupportedImageType(file)) {
                item.type = AerialMediaType.IMAGE
            }
            media.add(item)
        }

        var message = String.format(res.getString(R.string.local_media_test_summary1), files.size.toString()) + "\n"
        message += String.format(res.getString(R.string.local_media_test_summary2), excluded.toString()) + "\n"
        if (prefs.mediaType != ProviderMediaType.PHOTOS) {
            message += String.format(res.getString(R.string.local_media_test_summary3), videos.toString()) + "\n"
        }
        if (prefs.mediaType != ProviderMediaType.VIDEOS) {
            message += String.format(res.getString(R.string.local_media_test_summary4), images.toString()) + "\n"
        }
        message += String.format(res.getString(R.string.local_media_test_summary6), media.size.toString())
        return Pair(media, message)
    }

    private suspend fun folderAccessVideosAndImages(): List<String> =
        withContext(Dispatchers.IO) {
            val folders = mutableListOf<String>()
            val found = mutableListOf<File>()

            if (prefs.legacyVolume.contains("/all", false)) {
                val vols = StorageHelper.getStoragePaths(context)
                val values = vols.map { it.key }.toTypedArray()
                for (entry in values) {
                    folders.add("$entry${prefs.legacyFolder}")
                }
            } else {
                folders.add("${prefs.legacyVolume}${prefs.legacyFolder}")
            }

            for (folder in folders) {
                val directory = File(folder)
                if (!directory.exists() || !directory.isDirectory) {
                    continue
                }

                if (prefs.legacySearchSubfolders) {
                    found.addAll(listFilesAndFoldersRecursively(directory))
                } else {
                    val files = directory.listFiles()
                    if (!files.isNullOrEmpty()) {
                        found.addAll(
                            files.filter { file ->
                                val filename = file.name.split("/").last()
                                !FileHelper.isDotOrHiddenFile(filename)
                            },
                        )
                    }
                }
            }
            return@withContext found
                .sortedByDescending { it.lastModified() }
                .map { item -> item.absolutePath }
        }

    private fun listFilesAndFoldersRecursively(directory: File): List<File> {
        val files = mutableListOf<File>()
        directory.listFiles()?.forEach { file ->
            if (FileHelper.isDotOrHiddenFile(file.name)) {
                return@forEach
            }

            if (file.isDirectory) {
                files.addAll(listFilesAndFoldersRecursively(file))
            } else {
                files.add(file)
            }
        }
        return files
    }

    @Suppress("JoinDeclarationAndAssignment")
    private suspend fun mediaStoreFetch(): Pair<List<AerialMedia>, String> {
        val res = context.resources!!
        val selected = mutableListOf<String>()
        val media = mutableListOf<AerialMedia>()
        val excluded: Int
        val filtered: Int
        val images: Int
        val videos: Int

        if (prefs.filterFolder.isEmpty() &&
            prefs.filterEnabled
        ) {
            return Pair(media, res.getString(R.string.local_videos_media_store_no_folder))
        }

        val files = mediaStoreVideosAndImages()

        // Add video
        if (prefs.mediaType != ProviderMediaType.PHOTOS) {
            selected.addAll(
                files.filter { file ->
                    FileHelper.isSupportedVideoType(file)
                },
            )
        }
        videos = selected.size

        // Add images
        if (prefs.mediaType != ProviderMediaType.VIDEOS) {
            selected.addAll(
                files.filter { file ->
                    FileHelper.isSupportedImageType(file)
                },
            )
        }
        images = selected.size - videos
        excluded = files.size - selected.size

        // Apply folder filter
        for (file in selected) {
            val uri = file.toUri()
            if (prefs.filterEnabled && FileHelper.shouldFilter(uri, prefs.filterFolder)) {
                continue
            }
            // Set media type, should be refactored
            // Also, check all providers
            val item = AerialMedia(uri)
            if (FileHelper.isSupportedVideoType(uri.filename)) {
                item.type = AerialMediaType.VIDEO
            } else if (FileHelper.isSupportedImageType(uri.filename)) {
                item.type = AerialMediaType.IMAGE
            }
            media.add(item)
        }
        filtered = selected.size - media.size

        var message = String.format(res.getString(R.string.local_media_test_summary1), files.size.toString()) + "\n"
        message += String.format(res.getString(R.string.local_media_test_summary2), excluded.toString()) + "\n"
        if (prefs.mediaType != ProviderMediaType.PHOTOS) {
            message += String.format(res.getString(R.string.local_media_test_summary3), videos.toString()) + "\n"
        }
        if (prefs.mediaType != ProviderMediaType.VIDEOS) {
            message += String.format(res.getString(R.string.local_media_test_summary4), images.toString()) + "\n"
        }
        message += String.format(res.getString(R.string.local_media_test_summary5), filtered.toString()) + "\n"
        message += String.format(res.getString(R.string.local_media_test_summary6), media.size.toString())
        return Pair(media, message)
    }

    private suspend fun mediaStoreVideosAndImages(): List<String> =
        withContext(Dispatchers.IO) {
            val files =
                FileHelper.findLocalVideos(context) +
                    FileHelper.findLocalImages(context)

            return@withContext files
                .filter { item -> !FileHelper.isDotOrHiddenFile(item) }
                .map { File(it) }
                .sortedByDescending { it.lastModified() }
                .map { it.absolutePath }
        }
}
