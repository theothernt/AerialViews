package com.neilturner.aerialviews.providers.custom

import android.content.Context
import androidx.core.net.toUri
import com.neilturner.aerialviews.models.enums.AerialMediaSource
import com.neilturner.aerialviews.models.enums.AerialMediaType
import com.neilturner.aerialviews.models.enums.ProviderSourceType
import com.neilturner.aerialviews.models.enums.SceneType
import com.neilturner.aerialviews.models.enums.TimeOfDay
import com.neilturner.aerialviews.models.enums.VideoQuality
import com.neilturner.aerialviews.models.prefs.CustomFeedPrefs
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.providers.MediaProvider
import com.neilturner.aerialviews.utils.JsonHelper
import com.neilturner.aerialviews.utils.UrlValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import timber.log.Timber
import java.util.concurrent.TimeUnit

class CustomFeedProvider(
    context: Context,
    private val prefs: CustomFeedPrefs,
) : MediaProvider(context) {
    override val type = ProviderSourceType.REMOTE
    val metadata = mutableMapOf<String, Pair<String, Map<Int, String>>>()
    val videos = mutableListOf<AerialMedia>()

    override val enabled: Boolean
        get() = prefs.enabled

    override suspend fun fetchMedia(): List<AerialMedia> {
        videos.clear()
        metadata.clear()

        if (prefs.urlsCache.isBlank()) {
            Timber.w("No valid URLs configured")
            return emptyList()
        }

        val quality = prefs.quality
        val validUrls =
            prefs.urlsCache
                .split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() }

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

        for (url in validUrls) {
            try {
                Timber.d("Processing URL: $url")

                // Check if this is an RTSP stream
                if (url.startsWith("rtsp://", ignoreCase = true)) {
                    processRtspStream(url)
                    continue
                }

                // Process as entries.json URL
                processEntriesUrl(customFeedApi, url, quality)
            } catch (e: Exception) {
                Timber.w(e, "Failed to process URL: $url")
            }
        }

        Timber.i("${metadata.count()} metadata items found")
        Timber.i("${videos.count()} $quality videos found")

        return videos
    }

    override suspend fun fetchTest(): String {
        // Step 1: Simple validation of URLs
        val simpleValidation = performSimpleValidation()
        if (simpleValidation.hasErrors) {
            prefs.urlsSummary = simpleValidation.summary
            return simpleValidation.message
        }

        // Step 2: Advanced validation - fetch and parse each URL
        val advancedValidation = performAdvancedValidation()
        prefs.urlsSummary = advancedValidation.summary
        return advancedValidation.message
    }

    private fun performSimpleValidation(): ValidationResult {
        if (prefs.urls.isBlank()) {
            return ValidationResult(
                hasErrors = true,
                message = "❌ No URLs configured",
                summary = "No URLs",
            )
        }

        val validationResults = UrlValidator.validateUrls(prefs.urls)
        val validUrls = validationResults.filter { it.first }
        val invalidUrls = validationResults.filter { !it.first }

        if (invalidUrls.isEmpty()) {
            // All URLs are valid format
            return ValidationResult(
                hasErrors = false,
                message =
                    buildString {
                        append("✅ All URLs have valid format (${validUrls.size})\n\n")
                        validUrls.forEach { (_, url) ->
                            append("✅ $url\n")
                        }
                    },
                summary = "${validUrls.size} valid URLs",
            )
        }

        // Some URLs are invalid
        val message =
            buildString {
                append("Invalid URL format detected:\n\n")
                if (validUrls.isNotEmpty()) {
                    append("Valid URLs (${validUrls.size}):\n")
                    validUrls.forEach { (_, url) ->
                        append("✅ $url\n")
                    }
                    append("\n")
                }
                append("Invalid URLs (${invalidUrls.size}):\n")
                invalidUrls.forEach { (_, url) ->
                    append("❌ $url\n")
                }
            }

        return ValidationResult(
            hasErrors = true,
            message = message,
            summary = "${invalidUrls.size} invalid, ${validUrls.size} valid",
        )
    }

    private suspend fun performAdvancedValidation(): ValidationResult {
        val urls = UrlValidator.parseUrls(prefs.urls)
        val validEntriesUrls = mutableListOf<String>()
        val validRtspUrls = mutableListOf<String>()
        val errorMessages = mutableMapOf<String, String>()

        val okHttpClient =
            OkHttpClient
                .Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()

        val retrofit =
            Retrofit
                .Builder()
                .baseUrl("http://example.com")
                .client(okHttpClient)
                .addConverterFactory(JsonHelper.buildSerializer())
                .build()

        val customFeedApi = retrofit.create(CustomFeedApi::class.java)

        for (url in urls) {
            try {
                Timber.d("Testing URL: $url")

                // Check if this is an RTSP stream
                if (url.startsWith("rtsp://", ignoreCase = true)) {
                    validRtspUrls.add(url)
                    Timber.i("Valid RTSP stream: $url")
                    continue
                }

                // Check if URL ends with entries.json - parse it directly for videos
                if (url.endsWith("entries.json", ignoreCase = true)) {
                    try {
                        val customVideos = customFeedApi.getVideos(url)
                        val videoCount = customVideos.assets?.size ?: 0
                        if (videoCount > 0) {
                            validEntriesUrls.add(url)
                            Timber.i("Found $videoCount videos in entries.json: $url")
                        } else {
                            errorMessages[url] = "entries.json contains no videos"
                        }
                    } catch (e: Exception) {
                        errorMessages[url] = "Failed to parse entries.json: ${e.message}"
                    }
                    continue
                }

                // For HTTP/HTTPS URLs without entries.json, append /manifest.json if needed
                val uri = url.toUri()
                val testUrl =
                    if (!uri.toString().endsWith("manifest.json", false)) {
                        "${url.trimEnd('/')}/manifest.json"
                    } else {
                        url
                    }

                // Try to parse as manifest to get entries.json URLs
                val entriesUrls = tryParseAsManifest(customFeedApi, testUrl)
                if (entriesUrls.isNotEmpty()) {
                    validEntriesUrls.addAll(entriesUrls)
                    Timber.i("Found ${entriesUrls.size} entries URLs from manifest: $testUrl")
                } else {
                    errorMessages[url] = "URL does not contain a valid manifest.json"
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to test URL: $url")
                errorMessages[url] = e.message ?: "Unknown error"
            }
        }

        // Count total videos from valid entries.json URLs
        var totalVideos = 0
        for (entriesUrl in validEntriesUrls) {
            try {
                val customVideos = customFeedApi.getVideos(entriesUrl)
                val videoCount = customVideos.assets?.size ?: 0
                totalVideos += videoCount
                Timber.d("Found $videoCount videos in $entriesUrl")
            } catch (e: Exception) {
                Timber.w(e, "Failed to count videos from: $entriesUrl")
            }
        }

        // Save valid URLs to cache
        val allValidUrls = (validEntriesUrls + validRtspUrls).joinToString(",")
        prefs.urlsCache = allValidUrls

        // Build result message
        if (validEntriesUrls.isNotEmpty() || validRtspUrls.isNotEmpty()) {
            val message =
                buildString {
                    append("✅ Validation successful!\n\n")
                    append("Summary:\n")
                    if (totalVideos > 0) {
                        append("• $totalVideos videos found\n")
                    }
                    if (validEntriesUrls.isNotEmpty()) {
                        append("• ${validEntriesUrls.size} video feeds\n")
                    }
                    if (validRtspUrls.isNotEmpty()) {
                        append("• ${validRtspUrls.size} RTSP streams\n")
                    }

                    if (errorMessages.isNotEmpty()) {
                        append("\n⚠️ Some URLs had issues (${errorMessages.size}):\n")
                        errorMessages.forEach { (url, error) ->
                            append("❌ $error\n   $url\n")
                        }
                    }
                }

            val totalUrls = validEntriesUrls.size + validRtspUrls.size
            val summary =
                buildString {
                    append("$totalUrls URL${if (totalUrls != 1) "s" else ""}: ")
                    val parts = mutableListOf<String>()
                    if (totalVideos > 0) parts.add("$totalVideos video${if (totalVideos != 1) "s" else ""}")
                    if (validRtspUrls.isNotEmpty()) parts.add("${validRtspUrls.size} stream${if (validRtspUrls.size != 1) "s" else ""}")
                    append(parts.joinToString(", "))
                }

            return ValidationResult(
                hasErrors = false,
                message = message,
                summary = summary,
            )
        } else {
            val message =
                buildString {
                    append("❌ No valid URLs found\n\n")
                    if (errorMessages.isNotEmpty()) {
                        append("Errors:\n")
                        errorMessages.forEach { (url, error) ->
                            append("❌ $error\n   $url\n\n")
                        }
                    }
                }

            return ValidationResult(
                hasErrors = true,
                message = message,
                summary = "No valid URLs",
            )
        }
    }

    private data class ValidationResult(
        val hasErrors: Boolean,
        val message: String,
        val summary: String,
    )

    override suspend fun fetchMetadata(): MutableMap<String, Pair<String, Map<Int, String>>> {
        // if (metadata.isEmpty()) buildVideoAndMetadata()
        return metadata
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
                    val timeOfDay = TimeOfDay.fromString(asset.timeOfDay)
                    val scene = SceneType.fromString(asset.scene)

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

    private suspend fun processRtspStream(url: String) {
        withContext(Dispatchers.IO) {
            try {
                Timber.i("Processing RTSP stream: $url")

                // Add RTSP stream directly as a video
                // RTSP streams don't have time of day or scene metadata, so we add them without filtering
                val uri = url.toUri()
                videos.add(
                    AerialMedia(
                        uri,
                        description = "",
                        type = AerialMediaType.VIDEO,
                        source = AerialMediaSource.RTSP,
                        timeOfDay = TimeOfDay.UNKNOWN,
                        scene = SceneType.UNKNOWN,
                    ),
                )

                // Add metadata for the RTSP stream
                val data =
                    Pair(
                        "RTSP Stream: $url",
                        emptyMap<Int, String>(),
                    )
                metadata[url] = data

                Timber.d("Added RTSP stream: $url")
            } catch (e: Exception) {
                Timber.w(e, "Failed to process RTSP stream: $url")
            }
        }
    }
}
