package com.neilturner.aerialviews.providers.immich

import android.content.Context
import com.neilturner.aerialviews.models.enums.ImmichAuthType
import com.neilturner.aerialviews.models.enums.ProviderSourceType
import com.neilturner.aerialviews.models.prefs.ImmichMediaPrefs
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.providers.MediaProvider
import com.neilturner.aerialviews.utils.UrlParser
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import timber.log.Timber
import java.net.ConnectException
import java.net.UnknownHostException

class ImmichMediaProvider(
    context: Context,
    private val prefs: ImmichMediaPrefs,
) : MediaProvider(context) {
    override val type = ProviderSourceType.REMOTE
    override val enabled: Boolean
        get() = prefs.enabled

    private val serverUrl by lazy { UrlParser.parseServerUrl(prefs.url) }
    private val urlBuilder = ImmichUrlBuilder(serverUrl, prefs)
    private val repository = ImmichRepository(prefs, urlBuilder)
    private val mapper = ImmichAssetMapper(prefs, urlBuilder)

    override suspend fun fetchMedia(): List<AerialMedia> = fetchImmichMedia().first

    override suspend fun fetchTest(): String = fetchImmichMedia().second

    override suspend fun fetchMetadata(): MutableMap<String, Pair<String, Map<Int, String>>> = mutableMapOf()

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

    private suspend fun fetchImmichMedia(): Pair<List<AerialMedia>, String> {
        val media = mutableListOf<AerialMedia>()

        // Validate input
        val validationError = validateInput()
        if (validationError != null) {
            return Pair(media, validationError)
        }

        // Fetch all assets from API
        val assetResults =
            try {
                fetchAllAssets()
            } catch (e: Exception) {
                Timber.e(e)
                return Pair(emptyList(), formatErrorMessage(e))
            }

        // Check if any assets were found
        if (assetResults.allAssets.isEmpty()) {
            return Pair(media, "No files found")
        }

        // Process assets and create media list
        val processResults = mapper.processAssets(assetResults.allAssets)
        media.addAll(processResults.media)

        // Build summary message
        val message = buildSummaryMessage(processResults, assetResults)

        Timber.i("Media found: ${media.size}")
        return Pair(media, message)
    }

    private fun validateInput(): String? {
        if (prefs.url.isEmpty()) {
            return "Hostname and port not specified"
        }

        if (prefs.authType == ImmichAuthType.SHARED_LINK) {
            if (prefs.pathName.isEmpty()) {
                return "Path name is empty"
            }
        } else {
            if (prefs.apiKey.isEmpty()) {
                return "API key is empty"
            }
        }

        return null
    }

    private suspend fun fetchAllAssets(): AssetFetchResults =
        coroutineScope {
            // Get primary assets (album or shared link)
            val primaryAlbum =
                if (prefs.authType == ImmichAuthType.SHARED_LINK) {
                    repository.getSharedAlbumFromAPI()
                } else {
                    repository.getSelectedAlbumFromAPI()
                }

            // Filter primary album assets by media type
            val filteredPrimaryAssets = mapper.filterAssetsByMediaType(primaryAlbum.assets)

            // Get optional asset sources (API key only) and filter each by media type
            val favoriteDeferred =
                async {
                    if (prefs.authType == ImmichAuthType.API_KEY && prefs.includeFavorites != "DISABLED") {
                        val rawAssets = fetchOptionalAssets("favorites") { repository.getFavoriteAssetsFromAPI() }
                        mapper.filterAssetsByMediaType(rawAssets)
                    } else {
                        emptyList()
                    }
                }

            val ratedDeferred =
                async {
                    if (prefs.authType == ImmichAuthType.API_KEY && prefs.includeRatings.isNotEmpty()) {
                        val rawAssets = fetchOptionalAssets("rated") { repository.getRatedAssetsFromAPI() }
                        mapper.filterAssetsByMediaType(rawAssets)
                    } else {
                        emptyList()
                    }
                }

            val randomDeferred =
                async {
                    if (prefs.authType == ImmichAuthType.API_KEY && prefs.includeRandom != "DISABLED") {
                        val rawAssets = fetchOptionalAssets("random") { repository.getRandomAssetsFromAPI() }
                        mapper.filterAssetsByMediaType(rawAssets)
                    } else {
                        emptyList()
                    }
                }

            val recentDeferred =
                async {
                    if (prefs.authType == ImmichAuthType.API_KEY && prefs.includeRecent != "DISABLED") {
                        val rawAssets = fetchOptionalAssets("recent") { repository.getRecentAssetsFromAPI() }
                        mapper.filterAssetsByMediaType(rawAssets)
                    } else {
                        emptyList()
                    }
                }

            val favoriteAssets = favoriteDeferred.await()
            val ratedAssets = ratedDeferred.await()
            val randomAssets = randomDeferred.await()
            val recentAssets = recentDeferred.await()

            // Combine and deduplicate all filtered assets
            val allAssets =
                (filteredPrimaryAssets + favoriteAssets + ratedAssets + randomAssets + recentAssets)
                    .distinctBy { it.id }

            return@coroutineScope AssetFetchResults(
                allAssets = allAssets,
                favoriteCount = favoriteAssets.size,
                ratedCount = ratedAssets.size,
                randomCount = randomAssets.size,
                recentCount = recentAssets.size,
            )
        }

    private suspend fun fetchOptionalAssets(
        sourceName: String,
        fetchFn: suspend () -> List<Asset>,
    ): List<Asset> =
        try {
            fetchFn()
        } catch (e: Exception) {
            Timber.w(e, "Failed to fetch $sourceName assets, continuing without them")
            emptyList()
        }

    private fun buildSummaryMessage(
        processResults: ImmichAssetMapper.ProcessResults,
        assetResults: AssetFetchResults,
    ): String {
        var message = ""

        // Show total assets fetched from albums/shared links
        message += "Album assets: ${assetResults.allAssets.size}\n"

        // Add information about different asset sources
        if (prefs.authType == ImmichAuthType.API_KEY) {
            if (prefs.includeRandom != "DISABLED" && assetResults.randomCount > 0) {
                message += "Random assets: ${assetResults.randomCount}\n"
            }
            if (prefs.includeRecent != "DISABLED" && assetResults.recentCount > 0) {
                message += "Recent assets: ${assetResults.recentCount}\n"
            }
            if (prefs.includeFavorites != "DISABLED" && assetResults.favoriteCount > 0) {
                message += "Favorite assets: ${assetResults.favoriteCount}\n"
            }
            if (prefs.includeRatings.isNotEmpty() && assetResults.ratedCount > 0) {
                message += "Rated assets: ${assetResults.ratedCount}\n"
            }
        }

        message += "\nTotal unique media: ${processResults.media.size}"

        return message
    }

    private data class AssetFetchResults(
        val allAssets: List<Asset>,
        val favoriteCount: Int,
        val ratedCount: Int,
        val randomCount: Int,
        val recentCount: Int,
    )

    suspend fun fetchAlbums(): Result<List<Album>> = repository.fetchAlbums()
}
