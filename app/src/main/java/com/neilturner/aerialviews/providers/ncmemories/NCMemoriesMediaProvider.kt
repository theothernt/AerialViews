package com.neilturner.aerialviews.providers.ncmemories

import android.content.Context
import com.neilturner.aerialviews.data.network.UrlParser
import com.neilturner.aerialviews.models.enums.ProviderSourceType
import com.neilturner.aerialviews.models.prefs.NCMemoriesMediaPrefs
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.providers.MediaProvider
import com.neilturner.aerialviews.providers.ProviderFetchResult
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import timber.log.Timber
import java.net.ConnectException
import java.net.UnknownHostException

class NCMemoriesMediaProvider(
    context: Context,
    private val prefs: NCMemoriesMediaPrefs,
) : MediaProvider(context) {
    override val type = ProviderSourceType.REMOTE
    override val enabled: Boolean
        get() = prefs.enabled

    private val serverUrl by lazy { UrlParser.parseServerUrl(prefs.url) }
    private val urlBuilder = NCMemoriesUrlBuilder(serverUrl, prefs)
    private val repository = NCMemoriesRepository(prefs)
    private val mapper = NCMemoriesImageMapper(prefs, urlBuilder)

    override suspend fun fetch(): ProviderFetchResult {
        val result = fetchAllMedia()
        return ProviderFetchResult.Success(media = result.first, summary = result.second)
    }

    override suspend fun fetchMetadata(media: List<AerialMedia>): List<AerialMedia> = media

    /**
     * Cleans up exception messages for display to users, making them more readable.
     */
    private fun formatErrorMessage(e: Exception): String {
        val originalMessage = e.message ?: "Unknown error"

        return when (e) {
            is ConnectException -> {
                // Extract host:port from messages like:
                // "Failed to connect to /192.168.1.3:2283" or
                // "failed to connect to /10.0.2.16 (port 45578) from /192.168.1.3 (port 2283)"
                val hostPortRegex = Regex("""/([\d.]+):(\d+)""")
                val match = hostPortRegex.find(originalMessage)
                if (match != null) {
                    val host = match.groupValues[1]
                    val port = match.groupValues[2]
                    "Cannot connect to $host:$port - Connection refused. Is the server running?"
                } else {
                    "Cannot connect to server - Connection refused. Is the server running?"
                }
            }

            is UnknownHostException -> {
                "Cannot resolve server hostname. Please check the address."
            }

            else -> {
                // For other errors, show the original message but clean up any socket address formatting
                originalMessage.replace(Regex("""/([\d.]+):(\d+)"""), "$1:$2")
            }
        }
    }

    private suspend fun fetchAllMedia(): Pair<List<AerialMedia>, String> {
        val media = mutableListOf<AerialMedia>()

        // Validate input
        val validationError = validateInput()
        if (validationError != null) {
            return Pair(media, validationError)
        }

        // Fetch all assets from API
        val imageResults =
            try {
                fetchAllImages()
            } catch (e: Exception) {
                Timber.e(e)
                return Pair(emptyList(), formatErrorMessage(e))
            }

        // Check if any files were found
        if (imageResults.allImages.isEmpty()) {
            return Pair(media, "No files found")
        }

        // Process images and create media list
        val processResults = mapper.processImages(imageResults.allImages)
        media.addAll(processResults.media)

        // Build summary message
        val message = buildSummaryMessage(processResults, imageResults)

        Timber.i("Media found: ${media.size}")
        return Pair(media, message)
    }

    private fun validateInput(): String? {
        if (prefs.url.isEmpty()) {
            return "Hostname and port not specified"
        }

        if (prefs.username.isEmpty()) {
            return "Username not specified"
        }

        if (prefs.password.isEmpty()) {
            return "Password not specified"
        }

        return null
    }

    private data class ImageFetchResults(
        val allImages: List<Image>,
        val favoriteCount: Int,
        val recentCount: Int,
    )

    private suspend fun fetchAllImages(): ImageFetchResults =
        coroutineScope {
            // Get images from selected albums
            val selectAlbumsImages = repository.getSelectedAlbums()

            // Filter album images by media type
            val filteredAlbumsImages = mapper.filterImagesByMediaType(selectAlbumsImages)

            // Get optional image sources and filter by media type
            val favoriteImagesQueryDeferred =
                async {
                    if (prefs.includeFavorites != "DISABLED") {
                        val sourceName = prefs.favoritesName
                        val rawAssets =
                            fetchOptionalImages(sourceName) { repository.getOptionalImages(
                                imageSourceName = sourceName,
                                count = prefs.includeFavorites.toIntOrNull()
                            ) }
                        mapper.filterImagesByMediaType(rawAssets)
                    } else {
                        emptyList()
                    }
                }

            val recentImagesQueryDeferred =
                async {
                    if (prefs.includeRecent != "DISABLED") {
                        val sourceName = prefs.recentName
                        val rawAssets =
                            fetchOptionalImages(sourceName) { repository.getOptionalImages(
                                imageSourceName = sourceName,
                                count = prefs.includeRecent.toIntOrNull()
                            ) }
                        mapper.filterImagesByMediaType(rawAssets)
                    } else {
                        emptyList()
                    }
                }

            val favoriteImages = favoriteImagesQueryDeferred.await()
            val recentImages = recentImagesQueryDeferred.await()

            // Combine and deduplicate all filtered images
            val allImages =
                (filteredAlbumsImages + favoriteImages + recentImages)
                    .distinctBy { it.fileId }

            return@coroutineScope ImageFetchResults(
                allImages = allImages,
                favoriteCount = favoriteImages.size,
                recentCount = recentImages.size,
            )
        }

    private suspend fun fetchOptionalImages(
        sourceName: String,
        fetchFn: suspend () -> List<Image>,
    ): List<Image> =
        try {
            fetchFn()
        } catch (e: Exception) {
            Timber.w(e, "Failed to fetch $sourceName images, continuing without them")
            emptyList()
        }

    private fun buildSummaryMessage(
        processResults: NCMemoriesImageMapper.ProcessResults,
        imageResults: ImageFetchResults,
    ): String {
        var message = ""

        // Show total images fetched from albums
        message += "Album images: ${imageResults.allImages.size}\n"

        // Add information about different image sources
        if (prefs.includeFavorites != "DISABLED" && imageResults.favoriteCount > 0) {
            message += "Favorite images: ${imageResults.favoriteCount}\n"
        }
        if (prefs.includeRecent != "DISABLED" && imageResults.recentCount > 0) {
            message += "Recent images: ${imageResults.recentCount}\n"
        }

        message += "\nTotal unique media: ${processResults.media.size}"

        return message
    }

    suspend fun fetchAlbums(): Result<List<Album>> = repository.fetchAlbumList()
}
