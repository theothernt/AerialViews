package com.neilturner.aerialviews.services

import android.content.Context
import android.util.Log
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.VideoPlaylist
import com.neilturner.aerialviews.models.VideoQuality
import com.neilturner.aerialviews.models.prefs.AppleVideoPrefs
import com.neilturner.aerialviews.models.prefs.Comm1VideoPrefs
import com.neilturner.aerialviews.models.prefs.Comm2VideoPrefs
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.models.prefs.InterfacePrefs
import com.neilturner.aerialviews.models.prefs.LocalVideoPrefs
import com.neilturner.aerialviews.models.prefs.NetworkVideoPrefs
import com.neilturner.aerialviews.models.videos.AbstractVideo
import com.neilturner.aerialviews.models.videos.AerialVideo
import com.neilturner.aerialviews.providers.AppleVideoProvider
import com.neilturner.aerialviews.providers.Comm1VideoProvider
import com.neilturner.aerialviews.providers.Comm2VideoProvider
import com.neilturner.aerialviews.providers.LocalVideoProvider
import com.neilturner.aerialviews.providers.NetworkVideoProvider
import com.neilturner.aerialviews.providers.VideoProvider
import com.neilturner.aerialviews.utils.FileHelper
import com.neilturner.aerialviews.utils.JsonHelper
import com.neilturner.aerialviews.utils.toStringOrEmpty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VideoService(private val context: Context) {
    private val providers = mutableListOf<VideoProvider>()

    init {
        if (LocalVideoPrefs.enabled) {
            providers.add(LocalVideoProvider(context, LocalVideoPrefs))
        }

        if (NetworkVideoPrefs.enabled) {
            providers.add(NetworkVideoProvider(context, NetworkVideoPrefs))
        }

        if (Comm1VideoPrefs.enabled) {
            providers.add(Comm1VideoProvider(context, Comm1VideoPrefs))
        }

        if (Comm2VideoPrefs.enabled) {
            providers.add(Comm2VideoProvider(context, Comm2VideoPrefs))
        }

        // Remote videos added last so they'll be filtered out if duplicates are found
        if (AppleVideoPrefs.enabled) {
            providers.add(AppleVideoProvider(context, AppleVideoPrefs))
        }
    }

    suspend fun fetchVideos(): VideoPlaylist = withContext(Dispatchers.IO) {
        var videos = mutableListOf<AerialVideo>()

        // Find all videos from all providers/sources
        providers.forEach {
            val newVideos = try {
                it.fetchVideos()
            } catch (ex: Exception) {
                Log.e(TAG, "Exception in video provider", ex)
                emptyList()
            }
            videos.addAll(newVideos)
        }

        // Remove duplicates based on filename only
        if (GeneralPrefs.removeDuplicates) {
            val numVideos = videos.size
            videos = videos.distinctBy { it.uri.lastPathSegment.toString().lowercase() } as MutableList<AerialVideo>
            Log.i(TAG, "Duplicate videos removed based on filename: ${numVideos - videos.size}")
        }

        // Try to add location/POIs to local and network videos
        if (InterfacePrefs.showLocation) {
            val matches = matchVideosWithManifestData(videos)
            videos = matches.first.toMutableList()

//            if (GeneralPrefs.useAppleManifests) {
//                manifestVideos.addAll(allManifestVideos())
//            }

            if (!GeneralPrefs.ignoreNonManifestVideos) {
                videos.addAll(matches.second)
            }
        }

        // If there are videos with no location yet, use filename as location
        if (!GeneralPrefs.ignoreNonManifestVideos &&
            InterfacePrefs.showLocation &&
            GeneralPrefs.filenameAsLocation
        ) {
            videos.forEach { video ->
                if (video.location.isBlank()) {
                    val location = FileHelper.filenameToTitleCase(video.uri)
                    video.location = location
                }
            }
        }

        // Randomise video order
        if (videos.isNotEmpty() && GeneralPrefs.shuffleVideos) {
            videos.shuffle()
        }

        Log.i(TAG, "Total vids: ${videos.size}")
        VideoPlaylist(videos)
    }

    private fun matchVideosWithManifestData(videos: List<AerialVideo>): Pair<List<AerialVideo>, List<AerialVideo>> {
        val recognisedVideos = mutableListOf<AerialVideo>()
        val unrecognisedVideos = mutableListOf<AerialVideo>()

        val videos1 = videosWithTranslatedPointsOfInterest()
        val poiStrings = JsonHelper.parseJsonMap(context, R.raw.tvos15_strings)
        val result1 = findVideoLocationInManifest(videos, videos1, poiStrings)
        recognisedVideos.addAll(result1.first)

        val videos2 = videosWithPointsOfInterest()
        val result2 = findVideoLocationInManifest(result1.second, videos2, communityVideos = true)
        recognisedVideos.addAll(result2.first)

        val videos3 = videosWithLocations()
        val result3 = findVideoLocationInManifest(result2.second, videos3)
        recognisedVideos.addAll(result3.first)
        unrecognisedVideos.addAll(result3.second)

        // Log.i(TAG, "Found ${result1.first.count()} tvOS15 manifest videos")
        // Log.i(TAG, "Found ${result2.first.count()} community manifest videos")
        // Log.i(TAG, "Found ${result3.first.count()} legacy manifest videos")
        // Log.i(TAG, "Found ${result3.second.count()} unrecognised manifest videos")

        return Pair(recognisedVideos, unrecognisedVideos)
    }

    // Apple videos that map to translated POI strings
    private fun videosWithTranslatedPointsOfInterest(): List<AerialVideo> {
        val videos = mutableListOf<AerialVideo>()

        try {
            JsonHelper.parseJson(context, R.raw.tvos15, JsonHelper.Apple2018Videos::class.java)
                .assets?.forEach {
                    videos.addAll(allVideoQualities(it))
                }
        } catch (ex: Exception) {
            Log.e(TAG, "Exception while parsing tvOS 15 manifest", ex)
        }

        return videos
    }

    // Older apple videos with single location string
    // Can add all translations later?
    private fun videosWithLocations(): List<AerialVideo> {
        val videos = mutableListOf<AerialVideo>()

        try {
            JsonHelper.parseJson(context, R.raw.tvos12, JsonHelper.Apple2018Videos::class.java)
                .assets?.forEach {
                    videos.addAll(allVideoQualities(it))
                }
        } catch (ex: Exception) {
            Log.e(TAG, "Exception while parsing tvOS 12 manifest", ex)
        }

        try {
            JsonHelper.parseJson(context, R.raw.tvos13, JsonHelper.Apple2018Videos::class.java)
                .assets?.forEach {
                    videos.addAll(allVideoQualities(it))
                }
        } catch (ex: Exception) {
            Log.e(TAG, "Exception while parsing tvOS 13 manifest", ex)
        }

        // Some video filenames are found in multiple manifests
        return videos.distinctBy { it.uri.toString().lowercase() }
    }

    // Community videos with a single POI string
    private fun videosWithPointsOfInterest(): List<AerialVideo> {
        val videos = mutableListOf<AerialVideo>()

        try {
            JsonHelper.parseJson(context, R.raw.comm1, JsonHelper.Comm1Videos::class.java)
                .assets?.forEach {
                    videos.addAll(allVideoQualities(it))
                }
        } catch (ex: Exception) {
            Log.e(TAG, "Exception while parsing Community 1 manifest", ex)
        }

        try {
            JsonHelper.parseJson(context, R.raw.comm2, JsonHelper.Comm2Videos::class.java)
                .assets?.forEach {
                    videos.addAll(allVideoQualities(it))
                }
        } catch (ex: Exception) {
            Log.e(TAG, "Exception while parsing Community 2 manifest", ex)
        }

        // As there are no HDR videos, H.264 filenames are returned
        return videos.distinctBy { it.uri.toString().lowercase() }
    }

    private fun allVideoQualities(video: AbstractVideo): List<AerialVideo> {
        val videos = mutableListOf<AerialVideo>()

        VideoQuality.values().forEach { quality ->
            val uri = try {
                video.uri(quality)
            } catch (ex: Exception) {
                Log.e(TAG, "Exception while getting list of video quality URIs", ex)
                null
            }
            if (uri != null) {
                videos.add(AerialVideo(uri, video.location, video.pointsOfInterest))
            }
        }
        return videos
    }

    private fun findVideoLocationInManifest(providerVideos: List<AerialVideo>, manifestVideos: List<AerialVideo>, poiStrings: Map<String, String> = emptyMap(), communityVideos: Boolean = false): Pair<List<AerialVideo>, List<AerialVideo>> {
        val matched = mutableListOf<AerialVideo>()
        val unmatched = mutableListOf<AerialVideo>()

        for (video in providerVideos) {
            // Skip if remote/streaming video
            if (!FileHelper.isLocalVideo(video.uri)) {
                matched.add(video)
                continue
            }

            try {
                // Check if video filename exists in manifest video list
                val filename = video.uri.lastPathSegment.toStringOrEmpty().lowercase()
                val videoFound = manifestVideos.find {
                    val manifestFilename = it.uri.lastPathSegment.toStringOrEmpty().lowercase()
                    manifestFilename.contains(filename)
                }

                // Add manifest location/POI data to video
                if (videoFound != null && poiStrings.isNotEmpty()) {
                    matched.add(
                        AerialVideo(
                            video.uri,
                            videoFound.location,
                            videoFound.poi.mapValues { poi ->
                                poiStrings[poi.value] ?: videoFound.location
                            }
                        )
                    )
                }

                if (videoFound != null && poiStrings.isEmpty()) {
                    // Community videos have a single POI string
                    if (communityVideos) {
                        matched.add(
                            AerialVideo(
                                video.uri,
                                videoFound.location,
                                videoFound.poi
                            )
                        )
                    } else { // Older videos only get locations
                        AerialVideo(
                            video.uri,
                            videoFound.location
                        )
                    }
                }

                if (videoFound == null) {
                    unmatched.add(video)
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Exception while comparing provider and manifest videos", ex)
            }
        }
        return Pair(matched, unmatched)
    }
    companion object {
        private const val TAG = "VideoService"
    }
}
