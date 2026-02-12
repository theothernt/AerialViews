package com.neilturner.aerialviews.providers.immich

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.neilturner.aerialviews.models.enums.AerialMediaSource
import com.neilturner.aerialviews.models.enums.AerialMediaType
import com.neilturner.aerialviews.models.enums.ImmichAuthType
import com.neilturner.aerialviews.models.enums.ImmichImageType
import com.neilturner.aerialviews.models.enums.ImmichVideoType
import com.neilturner.aerialviews.models.enums.ProviderMediaType
import com.neilturner.aerialviews.models.enums.ProviderSourceType
import com.neilturner.aerialviews.models.prefs.ImmichMediaPrefs
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.providers.MediaProvider
import com.neilturner.aerialviews.utils.FileHelper
import com.neilturner.aerialviews.utils.JsonHelper.buildSerializer
import com.neilturner.aerialviews.utils.ServerConfig
import com.neilturner.aerialviews.utils.SslHelper
import com.neilturner.aerialviews.utils.UrlParser
import kotlinx.serialization.json.Json
import retrofit2.Retrofit
import timber.log.Timber

class ImmichMediaProvider(
    context: Context,
    private val prefs: ImmichMediaPrefs,
) : MediaProvider(context) {
    override val type = ProviderSourceType.REMOTE
    override val enabled: Boolean
        get() = prefs.enabled
    private lateinit var server: String
    private var resolvedSharedKey: String? = null

    private val immichClient by lazy {
        server = UrlParser.parseServerUrl(prefs.url)
        val serverConfig = ServerConfig(server, prefs.validateSsl)
        val okHttpClient = SslHelper().createOkHttpClient(serverConfig)
        Timber.i("Connecting to $server")

        Retrofit
            .Builder()
            .baseUrl(server)
            .client(okHttpClient)
            .addConverterFactory(buildSerializer())
            .build()
            .create(ImmichApi::class.java)
    }

    override suspend fun fetchMedia(): List<AerialMedia> = fetchImmichMedia().first

    override suspend fun fetchTest(): String = fetchImmichMedia().second

    override suspend fun fetchMetadata(): MutableMap<String, Pair<String, Map<Int, String>>> = mutableMapOf()

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
                return Pair(emptyList(), e.message.toString())
            }

        // Check if any assets were found
        if (assetResults.allAssets.isEmpty()) {
            return Pair(media, "No files found")
        }

        // Process assets and create media list
        val processResults = processAssets(assetResults.allAssets)
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

    private suspend fun fetchAllAssets(): AssetFetchResults {
        // Get primary assets (album or shared link)
        val primaryAlbum =
            if (prefs.authType == ImmichAuthType.SHARED_LINK) {
                getSharedAlbumFromAPI()
            } else {
                getSelectedAlbumFromAPI()
            }

        // Filter primary album assets by media type
        val filteredPrimaryAssets = filterAssetsByMediaType(primaryAlbum.assets)

        // Get optional asset sources (API key only) and filter each by media type
        val favoriteAssets =
            if (prefs.authType == ImmichAuthType.API_KEY && prefs.includeFavorites != "DISABLED") {
                val rawAssets = fetchOptionalAssets("favorites") { getFavoriteAssetsFromAPI() }
                filterAssetsByMediaType(rawAssets)
            } else {
                emptyList()
            }

        val ratedAssets =
            if (prefs.authType == ImmichAuthType.API_KEY && prefs.includeRatings.isNotEmpty()) {
                val rawAssets = fetchOptionalAssets("rated") { getRatedAssetsFromAPI() }
                filterAssetsByMediaType(rawAssets)
            } else {
                emptyList()
            }

        val randomAssets =
            if (prefs.authType == ImmichAuthType.API_KEY && prefs.includeRandom != "DISABLED") {
                val rawAssets = fetchOptionalAssets("random") { getRandomAssetsFromAPI() }
                filterAssetsByMediaType(rawAssets)
            } else {
                emptyList()
            }

        val recentAssets =
            if (prefs.authType == ImmichAuthType.API_KEY && prefs.includeRecent != "DISABLED") {
                val rawAssets = fetchOptionalAssets("recent") { getRecentAssetsFromAPI() }
                filterAssetsByMediaType(rawAssets)
            } else {
                emptyList()
            }

        // Combine and deduplicate all filtered assets
        val allAssets =
            (filteredPrimaryAssets + favoriteAssets + ratedAssets + randomAssets + recentAssets)
                .distinctBy { it.id }

        return AssetFetchResults(
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

    private fun filterAssetsByMediaType(assets: List<Asset>): List<Asset> =
        assets.filter { asset ->
            val filename = asset.originalPath
            when {
                FileHelper.isSupportedVideoType(filename) -> prefs.mediaType != ProviderMediaType.PHOTOS
                FileHelper.isSupportedImageType(filename) -> prefs.mediaType != ProviderMediaType.VIDEOS
                else -> false // Exclude unsupported files
            }
        }

    private fun processAssets(assets: List<Asset>): ProcessResults {
        val media = mutableListOf<AerialMedia>()
        var excluded = 0
        var videos = 0
        var images = 0

        assets.forEach { asset ->
            val poi = extractLocationPoi(asset)
            val description = asset.exifInfo?.description ?: ""
            val filename = asset.originalPath

            Timber.i("Description: $description, Filename: $filename")

            val isVideo = FileHelper.isSupportedVideoType(filename)
            val isImage = FileHelper.isSupportedImageType(filename)

            if (isVideo || isImage) {
                val uri = getAssetUri(asset.id, isVideo)
                val item =
                    AerialMedia(uri, description, poi).apply {
                        source = AerialMediaSource.IMMICH
                        type = if (isVideo) AerialMediaType.VIDEO else AerialMediaType.IMAGE
                    }

                if (isVideo) {
                    videos++
                    if (prefs.mediaType != ProviderMediaType.PHOTOS) {
                        media.add(item)
                    }
                } else {
                    images++
                    if (prefs.mediaType != ProviderMediaType.VIDEOS) {
                        media.add(item)
                    }
                }
            } else {
                excluded++
            }
        }

        return ProcessResults(
            media = media,
            excluded = excluded,
            videos = videos,
            images = images,
        )
    }

    private fun extractLocationPoi(asset: Asset): MutableMap<Int, String> {
        val poi = mutableMapOf<Int, String>()
        try {
            if (asset.exifInfo?.country != null && asset.exifInfo.country.isNotBlank()) {
                Timber.i("extractLocationPoi: ${asset.id} country = ${asset.exifInfo.country}")
                val location =
                    listOf(asset.exifInfo.city)
                        .filter { !it.isNullOrBlank() }
                        .joinToString(separator = ", ")
                if (location.isNotBlank()) {
                    poi[poi.size] = location
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error parsing location EXIF data")
        }
        return poi
    }

    private fun buildSummaryMessage(
        processResults: ProcessResults,
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

    private data class ProcessResults(
        val media: List<AerialMedia>,
        val excluded: Int,
        val videos: Int,
        val images: Int,
    )

    private suspend fun getSharedAlbumFromAPI(): Album {
        try {
            val path = prefs.pathName
            val cleaned = cleanSharedLinkKey(path)
            val useSlug = isSlugFormat(path)
            Timber.d("Fetching shared album with ${if (useSlug) "slug" else "key"}: $cleaned")
            val response =
                immichClient.getSharedAlbum(
                    key = if (useSlug) null else cleaned,
                    slug = if (useSlug) cleaned else null,
                    password = prefs.password.takeIf { it.isNotEmpty() },
                )
            Timber.d("Shared album API response: ${response.raw()}")
            if (response.isSuccessful) {
                val shared = response.body()
                Timber.d("Shared album fetched successfully: ${shared?.toString()}")
                if (shared == null) throw Exception("Empty response body")
                // Cache server-provided key for use in asset URLs
                resolvedSharedKey = shared.key

                // Handle different shared link types
                when (shared.type) {
                    "INDIVIDUAL" -> {
                        Timber.d("Shared link type is INDIVIDUAL, using assets directly")
                        return Album(
                            id = "shared-${shared.id}",
                            name = shared.description ?: "Shared Link",
                            description = shared.description ?: "",
                            assetCount = shared.assets.size,
                            assets = shared.assets,
                        )
                    }

                    "ALBUM" -> {
                        Timber.d("Shared link type is ALBUM, fetching album details")
                        if (shared.album == null || shared.album.id.isEmpty()) {
                            Timber.e("ALBUM type shared link but no album ID provided")
                            return Album(
                                id = "shared-${shared.id}",
                                name = shared.description ?: "Shared Link",
                                description = "Album information not available",
                                assetCount = 0,
                                assets = emptyList(),
                            )
                        }

                        // Fetch the full album with assets using the shared key
                        try {
                            val albumResponse =
                                immichClient.getSharedAlbumById(
                                    albumId = shared.album.id,
                                    key = shared.key,
                                    password = prefs.password.takeIf { it.isNotEmpty() },
                                )

                            if (albumResponse.isSuccessful) {
                                val album = albumResponse.body()
                                if (album != null) {
                                    Timber.d("Successfully fetched album: ${album.name}, assets: ${album.assets.size}")
                                    return album
                                } else {
                                    Timber.e("Received null album from successful response")
                                    return Album(
                                        id = "shared-${shared.id}",
                                        name = shared.description ?: "Shared Link",
                                        description = "Album data not available",
                                        assetCount = 0,
                                        assets = emptyList(),
                                    )
                                }
                            } else {
                                val errorBody = albumResponse.errorBody()?.string()
                                Timber.e("Failed to fetch album details. Code: ${albumResponse.code()}, Error: $errorBody")
                                return Album(
                                    id = "shared-${shared.id}",
                                    name = shared.description ?: "Shared Link",
                                    description = "Failed to load album",
                                    assetCount = 0,
                                    assets = emptyList(),
                                )
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Exception while fetching album details")
                            return Album(
                                id = "shared-${shared.id}",
                                name = shared.description ?: "Shared Link",
                                description = "Error loading album",
                                assetCount = 0,
                                assets = emptyList(),
                            )
                        }
                    }

                    else -> {
                        Timber.w("Unknown shared link type: ${shared.type}, falling back to legacy behavior")
                        // Fallback to legacy behavior for unknown types
                        val album =
                            shared.album
                                ?: Album(
                                    id = "shared-${shared.id}",
                                    name = shared.description ?: "Shared Link",
                                    description = shared.description ?: "",
                                    assetCount = shared.assets.size,
                                    assets = shared.assets,
                                )
                        return album
                    }
                }
            } else {
                val errorBody = response.errorBody()?.string()
                Timber.e("API error: ${response.code()} - ${response.message()}")
                Timber.e("Error body: $errorBody")
                throw Exception("API error: ${response.code()} - ${response.message()}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error fetching shared album: ${e.message}")
            throw e
        }
    }

    private suspend fun getSelectedAlbumFromAPI(): Album {
        try {
            val selectedAlbumIds = prefs.selectedAlbumIds
            if (selectedAlbumIds.isEmpty()) {
                return Album(
                    id = "combined", // Use a special ID for the combined album
                    name = "",
                    description = "No albums selected",
                )
            }

            Timber.d("Attempting to fetch ${selectedAlbumIds.size} selected albums")
            Timber.d("Selected Album IDs: $selectedAlbumIds")
            Timber.d("API Key (first 5 chars): ${prefs.apiKey.take(5)}...")

            val allAssets = mutableListOf<Asset>()
            var combinedAlbumName = ""

            for ((index, albumId) in selectedAlbumIds.withIndex()) {
                val response = immichClient.getAlbum(apiKey = prefs.apiKey, albumId = albumId)
                Timber.d("API Request for album $albumId - URL: ${response.raw().request.url}")

                if (response.isSuccessful) {
                    val album = response.body()
                    if (album != null) {
                        Timber.d("Successfully fetched album: ${album.name}, assets: ${album.assets.size}")
                        allAssets.addAll(album.assets)
                        combinedAlbumName += if (index == 0) album.name else ", ${album.name}"
                    } else {
                        Timber.e("Received null album from successful response for album ID: $albumId")
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    Timber.e("Failed to fetch album $albumId. Code: ${response.code()}, Error: $errorBody")
                    // Continue with other albums instead of failing completely
                }
            }

            if (allAssets.isEmpty()) {
                throw Exception("No assets found in any of the selected albums")
            }

            // Remove duplicate assets based on ID
            val uniqueAssets = allAssets.distinctBy { it.id }
            Timber.d("Combined ${allAssets.size} assets from ${selectedAlbumIds.size} albums, ${uniqueAssets.size} unique assets")

            // Return a combined album with all assets
            return Album(
                id = "combined", // Use a special ID for the combined album
                name = combinedAlbumName,
                description = "Combined album from ${selectedAlbumIds.size} selected albums",
                assetCount = uniqueAssets.size,
                assets = uniqueAssets,
            )
        } catch (e: Exception) {
            Timber.e(e, "Exception while fetching selected albums")
            throw Exception("Failed to fetch selected albums", e)
        }
    }

    private suspend fun getFavoriteAssetsFromAPI(): List<Asset> {
        try {
            val count = prefs.includeFavorites.toIntOrNull() ?: return emptyList()
            Timber.d("Fetching up to $count favorite assets")
            val searchRequest = SearchMetadataRequest(isFavorite = true)
            val response = immichClient.getFavoriteAssets(apiKey = prefs.apiKey, searchRequest = searchRequest)
            if (response.isSuccessful) {
                val searchResponse = response.body()
                val allAssets = searchResponse?.assets?.items ?: emptyList()
                val limitedAssets = allAssets.take(count)
                Timber.d("Successfully fetched ${limitedAssets.size} favorite assets (from ${allAssets.size} total)")
                return limitedAssets
            } else {
                val errorBody = response.errorBody()?.string()
                Timber.e("Failed to fetch favorites. Code: ${response.code()}, Error: $errorBody")
                throw Exception("Failed to fetch favorite assets: ${response.code()} - ${response.message()}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Exception while fetching favorite assets")
            throw Exception("Failed to fetch favorite assets", e)
        }
    }

    private suspend fun getRatedAssetsFromAPI(): List<Asset> {
        val ratedAssets = mutableListOf<Asset>()
        try {
            val ratings = prefs.includeRatings
            if (ratings.isNotEmpty()) {
                for (rating in ratings) {
                    Timber.d("Fetching rated assets with rating: $rating")
                    val searchRequest = SearchMetadataRequest(rating = rating.toInt())
                    val response = immichClient.getFavoriteAssets(apiKey = prefs.apiKey, searchRequest = searchRequest)
                    if (response.isSuccessful) {
                        val searchResponse = response.body()
                        val assets = searchResponse?.assets?.items ?: emptyList()
                        Timber.d("Successfully fetched ${assets.size} rated assets")
                        ratedAssets.addAll(assets)
                    } else {
                        val errorBody = response.errorBody()?.string()
                        Timber.e("Failed to fetch rated assets. Code: ${response.code()}, Error: $errorBody")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Exception while fetching rated assets")
        }
        return ratedAssets
    }

    private suspend fun getRandomAssetsFromAPI(): List<Asset> {
        try {
            val count = prefs.includeRandom.toIntOrNull() ?: return emptyList()
            Timber.d("Fetching $count random assets")
            val searchRequest = SearchMetadataRequest(size = count)
            val response = immichClient.getRandomAssets(apiKey = prefs.apiKey, searchRequest = searchRequest)
            if (response.isSuccessful) {
                val assets = response.body() ?: emptyList()
                Timber.d("Successfully fetched ${assets.size} random assets")
                return assets
            } else {
                val errorBody = response.errorBody()?.string()
                Timber.e("Failed to fetch random assets. Code: ${response.code()}, Error: $errorBody")
                throw Exception("Failed to fetch random assets: ${response.code()} - ${response.message()}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Exception while fetching random assets")
            throw Exception("Failed to fetch random assets", e)
        }
    }

    private suspend fun getRecentAssetsFromAPI(): List<Asset> {
        try {
            val count = prefs.includeRecent.toIntOrNull() ?: return emptyList()
            Timber.d("Fetching $count recent assets")
            val searchRequest = SearchMetadataRequest(size = count, order = "desc")
            val response = immichClient.getRecentAssets(apiKey = prefs.apiKey, searchRequest = searchRequest)
            if (response.isSuccessful) {
                val searchResponse = response.body()
                val assets = searchResponse?.assets?.items ?: emptyList()
                Timber.d("Successfully fetched ${assets.size} recent assets")
                return assets
            } else {
                val errorBody = response.errorBody()?.string()
                Timber.e("Failed to fetch recent assets. Code: ${response.code()}, Error: $errorBody")
                throw Exception("Failed to fetch recent assets: ${response.code()} - ${response.message()}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Exception while fetching recent assets")
            throw Exception("Failed to fetch recent assets", e)
        }
    }

    suspend fun fetchAlbums(): Result<List<Album>> =
        try {
            // Fetch regular albums
            val regularResponse = immichClient.getAlbums(apiKey = prefs.apiKey)
            val regularAlbums =
                if (regularResponse.isSuccessful) {
                    regularResponse.body() ?: emptyList()
                } else {
                    val errorBody = regularResponse.errorBody()?.string() ?: ""
                    val errorMessage =
                        try {
                            Json.decodeFromString<ErrorResponse>(errorBody).message
                        } catch (e: Exception) {
                            Timber.e(e, "Error parsing error body: $errorBody")
                            regularResponse.message()
                        }
                    return Result.failure(Exception("${regularResponse.code()} - $errorMessage"))
                }

            // Fetch shared albums
            val sharedResponse = immichClient.getAlbums(apiKey = prefs.apiKey, shared = true)
            val sharedAlbums =
                if (sharedResponse.isSuccessful) {
                    sharedResponse.body() ?: emptyList()
                } else {
                    // If shared albums fetch fails, log warning but continue with regular albums only
                    Timber.w("Failed to fetch shared albums: ${sharedResponse.code()} - ${sharedResponse.message()}")
                    emptyList()
                }

            // Combine and deduplicate albums by ID
            val allAlbums = (regularAlbums + sharedAlbums).distinctBy { it.id }
            Timber.d("Fetched ${regularAlbums.size} regular albums and ${sharedAlbums.size} shared albums (${allAlbums.size} total unique)")

            Result.success(allAlbums)
        } catch (e: Exception) {
            Result.failure(e)
        }

    private fun cleanSharedLinkKey(input: String): String {
        return input
            .trim()
            .replace(Regex("^/+|/+$"), "") // Remove leading and trailing slashes
            .replace(Regex("^(share|s)/"), "") // Support both "/share/<key>" and "/s/<slug>" formats
    }

    private fun isSlugFormat(input: String): Boolean {
        val trimmed = input.trim().replace(Regex("^/+"), "")
        return trimmed.startsWith("s/")
    }

    private fun getAssetUri(
        id: String,
        isVideo: Boolean,
    ): Uri {
        val cleanedKey = resolvedSharedKey ?: cleanSharedLinkKey(prefs.pathName)
        return when (prefs.authType) {
            ImmichAuthType.SHARED_LINK -> {
                val base =
                    if (isVideo) {
                        if (prefs.videoType == ImmichVideoType.TRANSCODED) {
                            "$server/api/assets/$id/video/playback?key=$cleanedKey"
                        } else {
                            "$server/api/assets/$id/original?key=$cleanedKey"
                        }
                    } else {
                        if (prefs.imageType == ImmichImageType.ORIGINAL) {
                            "$server/api/assets/$id/original?key=$cleanedKey"
                        } else {
                            val size = if (prefs.imageType == ImmichImageType.FULLSIZE) "fullsize" else "preview"
                            "$server/api/assets/$id/thumbnail?size=$size&key=$cleanedKey"
                        }
                    }
                val url = if (prefs.password.isNotEmpty()) "$base&password=${prefs.password}" else base
                url.toUri()
            }

            // "fullsize" will use fullsize or reencoded pic as configured within Immich
            // "preview" will use preview-reencoded pic as configured within Immich, 1440p by default
            ImmichAuthType.API_KEY -> {
                if (isVideo) {
                    if (prefs.videoType == ImmichVideoType.TRANSCODED) {
                        "$server/api/assets/$id/video/playback".toUri()
                    } else {
                        "$server/api/assets/$id/original".toUri()
                    }
                } else {
                    if (prefs.imageType == ImmichImageType.ORIGINAL) {
                        "$server/api/assets/$id/original".toUri()
                    } else {
                        val size = if (prefs.imageType == ImmichImageType.FULLSIZE) "fullsize" else "preview"
                        "$server/api/assets/$id/thumbnail?size=$size".toUri()
                    }
                }
            }

            null -> {
                throw IllegalStateException("Invalid authentication type")
            }
        }
    }
}
