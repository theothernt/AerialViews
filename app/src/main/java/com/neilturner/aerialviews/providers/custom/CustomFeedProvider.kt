package com.neilturner.aerialviews.providers.custom

import android.content.Context
import com.neilturner.aerialviews.models.enums.AerialMediaSource
import com.neilturner.aerialviews.models.enums.AerialMediaType
import com.neilturner.aerialviews.models.enums.ProviderSourceType
import com.neilturner.aerialviews.models.enums.SceneType
import com.neilturner.aerialviews.models.enums.TimeOfDay
import com.neilturner.aerialviews.models.enums.VideoQuality
import com.neilturner.aerialviews.models.prefs.CustomMediaPrefs
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.providers.MediaProvider
import com.neilturner.aerialviews.utils.JsonHelper
import com.neilturner.aerialviews.utils.UrlValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import timber.log.Timber
import java.util.concurrent.TimeUnit

class CustomFeedProvider(
    context: Context,
    private val prefs: CustomMediaPrefs,
) : MediaProvider(context) {
    override val type = ProviderSourceType.REMOTE
    val metadata = mutableMapOf<String, Pair<String, Map<Int, String>>>()
    val videos = mutableListOf<AerialMedia>()

    override val enabled: Boolean
        get() = prefs.enabled

    override suspend fun fetchMedia(): List<AerialMedia> {
        if (metadata.isEmpty()) buildVideoAndMetadata()
        return videos
    }

    override suspend fun fetchTest(): String {
        val validationResults = UrlValidator.validateUrlsWithNetworkTest(prefs.urls)
        val validUrls = validationResults.filter { it.value.isValid && it.value.isAccessible && it.value.containsJson }.keys.toList()

        return if (validUrls.isNotEmpty()) {
            buildVideoAndMetadata()
            "${videos.size} videos found from ${validUrls.size} valid URLs."
        } else {
            val errorSummary =
                validationResults.entries.joinToString("\n") { (url, result) ->
                    "${result.error ?: "Unknown error"}\n\n$url"
                }
            "No valid URLs found.\n\n$errorSummary"
        }
    }

    override suspend fun fetchMetadata(): MutableMap<String, Pair<String, Map<Int, String>>> {
        if (metadata.isEmpty()) buildVideoAndMetadata()
        return metadata
    }

    private suspend fun buildVideoAndMetadata() {
        val quality = prefs.quality
        val urls = UrlValidator.parseUrls(prefs.urls)

        if (urls.isEmpty()) {
            Timber.w("No valid URLs found in custom media preferences")
            return
        }

        val logging =
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }

        val okHttpClient =
            OkHttpClient
                .Builder()
                // .addInterceptor(logging)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()

        val retrofit =
            Retrofit
                .Builder()
                .baseUrl("http://example.com") // Base URL required but not used for @Url
                .client(okHttpClient)
                .addConverterFactory(JsonHelper.buildSerializer())
                .build()

        val customFeedApi = retrofit.create(CustomFeedApi::class.java)

        for (url in urls) {
            try {
                Timber.d("Processing URL: $url")

                // First try to parse as manifest format
                val manifestUrls = tryParseAsManifest(customFeedApi, url)
                if (manifestUrls.isNotEmpty()) {
                    // Process each entries URL from the manifest
                    for (entriesUrl in manifestUrls) {
                        processEntriesUrl(customFeedApi, entriesUrl, quality)
                    }
                } else {
                    // Try to parse directly as entries format
                    processEntriesUrl(customFeedApi, url, quality)
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to process URL: $url")
            }
        }

        Timber.i("${metadata.count()} metadata items found")
        Timber.i("${videos.count()} $quality videos found")
    }

    private suspend fun tryParseAsManifest(
        apiService: CustomFeedApi,
        url: String,
    ): List<String> =
        withContext(Dispatchers.IO) {
            val manifestUrls = mutableListOf<String>()
            try {
                val manifest = apiService.getManifest(url)
                Timber.i("Manifest name: ${manifest.name}")
                manifestUrls.add(manifest.manifestUrl)
            } catch (e: Exception) {
                Timber.d("URL is not a manifest format: $url - ${e.message}")
            }

            try {
                val manifests = apiService.getManifests(url)
                val entriesUrls = mutableListOf<String>()
                manifests.sources.forEach { source ->
                    if (source.manifestUrl.isNotBlank()) {
                        entriesUrls.add(source.manifestUrl)
                        Timber.d("Found manifest entry: ${source.name} -> ${source.manifestUrl}")
                    }
                }

                manifestUrls.addAll(entriesUrls)
            } catch (e: Exception) {
                Timber.d("URL is not a manifest list format: $url - ${e.message}")
            }

            return@withContext manifestUrls
        }

    private suspend fun processEntriesUrl(
        apiService: CustomFeedApi,
        url: String,
        quality: VideoQuality?,
    ) {
        withContext(Dispatchers.IO) {
            try {
                val customVideos = apiService.getVideos(url)

                customVideos.assets?.forEach { asset ->
                    val timeOfDay = TimeOfDay.Companion.fromString(asset.timeOfDay)
                    val scene = SceneType.Companion.fromString(asset.scene)

                    val timeOfDayMatches = prefs.timeOfDay.contains(timeOfDay.toString())
                    val sceneMatches = prefs.scene.contains(scene.toString())

                    if (timeOfDayMatches && sceneMatches && prefs.enabled) {
                        videos.add(
                            AerialMedia(
                                asset.uriAtQuality(quality),
                                type = AerialMediaType.VIDEO,
                                source = AerialMediaSource.CUSTOM,
                                timeOfDay = timeOfDay,
                                scene = scene,
                            ),
                        )
                    } else if (prefs.enabled) {
                        Timber.d("Filtering out video: ${asset.description}")
                    }

                    val data =
                        Pair(
                            asset.description,
                            asset.pointsOfInterest.mapValues { poi ->
                                poi.value // No string mapping for custom videos
                            },
                        )
                    asset.allUrls().forEach { videoUrl ->
                        metadata[videoUrl] = data
                    }
                }

                Timber.d("Processed ${customVideos.assets?.size ?: 0} videos from $url")
            } catch (e: Exception) {
                Timber.w(e, "Failed to parse entries from URL: $url")
            }
        }
    }
}
