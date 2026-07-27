package com.neilturner.aerialviews.providers.immich

import com.neilturner.aerialviews.data.network.JsonHelper.buildSerializer
import com.neilturner.aerialviews.data.network.ServerConfig
import com.neilturner.aerialviews.data.network.SslHelper
import com.neilturner.aerialviews.data.network.UrlParser
import com.neilturner.aerialviews.models.enums.ProviderMediaType
import com.neilturner.aerialviews.models.prefs.ImmichRepositoryPrefs
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import retrofit2.Retrofit
import timber.log.Timber

class ImmichRepository(
    private val prefs: ImmichRepositoryPrefs,
    private val urlBuilder: ImmichUrlBuilder,
    apiOverride: ImmichApi? = null,
) {
    lateinit var server: String

    private val apiKey: String get() = prefs.apiKey.trim()

    private val immichClient by lazy {
        apiOverride ?: run {
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
    }

    /** Cached server major version. Null means not yet fetched. */
    private var cachedServerMajorVersion: Int? = null

    /**
     * Returns the major version number of the connected Immich server (e.g. 2 or 3).
     * The result is cached after the first successful call.
     * Falls back to v2 behaviour on failure so existing users are unaffected.
     */
    suspend fun getServerVersion(): Int {
        cachedServerMajorVersion?.let { return it }
        return try {
            val response = immichClient.getServerVersion()
            if (response.isSuccessful) {
                val major = response.body()?.major ?: 2
                Timber.d("Immich server version: major=$major")
                cachedServerMajorVersion = major
                urlBuilder.setServerV3(major >= 3)
                major
            } else {
                Timber.w("Failed to fetch server version (${response.code()}), assuming v2")
                cachedServerMajorVersion = 2
                2
            }
        } catch (e: Exception) {
            Timber.w(e, "Exception fetching server version, assuming v2")
            cachedServerMajorVersion = 2
            2
        }
    }

    suspend fun getSharedAlbumFromAPI(): Album {
        try {
            val path = prefs.pathName
            val cleaned = cleanSharedLinkKey(path)
            val useSlug = isSlugFormat(path)
            Timber.d("Fetching shared album with ${if (useSlug) "slug" else "key"}: $cleaned")

            val serverVersion = getServerVersion()

            val response =
                if (serverVersion >= 3) {
                    // v3: password via query param is no longer accepted — drop it
                    immichClient.getSharedAlbumV3(
                        key = if (useSlug) null else cleaned,
                        slug = if (useSlug) cleaned else null,
                    )
                } else {
                    immichClient.getSharedAlbum(
                        key = if (useSlug) null else cleaned,
                        slug = if (useSlug) cleaned else null,
                        password = prefs.password.takeIf { it.isNotEmpty() },
                    )
                }

            Timber.d("Shared album API response: ${response.raw()}")
            if (response.isSuccessful) {
                val shared = response.body()
                Timber.d("Shared album fetched successfully: ${shared?.toString()}")
                if (shared == null) throw Exception("Empty response body")
                // Cache server-provided key for use in asset URLs
                urlBuilder.setResolvedSharedKey(shared.key)
                if (!shared.showMetadata) {
                    Timber.w("Immich shared link has showMetadata=false; EXIF/metadata may be absent in API responses")
                }

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

                        // Fetch the full album (metadata only on v3; assets need separate call)
                        try {
                            if (serverVersion >= 3) {
                                // v3: fetch album metadata then fetch assets via search
                                val albumResponse =
                                    immichClient.getSharedAlbumByIdV3(
                                        albumId = shared.album.id,
                                        key = shared.key,
                                    )

                                if (albumResponse.isSuccessful) {
                                    val album = albumResponse.body()
                                    if (album != null) {
                                        val assets = fetchAlbumAssetsSharedV3(shared.album.id, shared.key, album.name)
                                        Timber.d("Successfully fetched shared album (v3): ${album.name}, assets: ${assets.size}")
                                        return album.copy(
                                            assetCount = assets.size,
                                            assets = assets,
                                        )
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
                                    Timber.e("Failed to fetch album details (v3). Code: ${albumResponse.code()}, Error: $errorBody")
                                    return Album(
                                        id = "shared-${shared.id}",
                                        name = shared.description ?: "Shared Link",
                                        description = "Failed to load album",
                                        assetCount = 0,
                                        assets = emptyList(),
                                    )
                                }
                            } else {
                                // v2: assets are inline in the album response
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
                                        return album.copy(
                                            assets = album.assets.map { it.copy(albumName = album.name) },
                                        )
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
                        return if (album.name.isNotBlank()) {
                            album.copy(assets = album.assets.map { it.copy(albumName = album.name) })
                        } else {
                            album
                        }
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

    /**
     * Fetches all assets for a shared album on Immich v3 using POST /api/search/metadata.
     * Paginates with page size 500 until all assets are retrieved.
     */
    private suspend fun fetchAlbumAssetsSharedV3(
        albumId: String,
        sharedKey: String,
        albumName: String,
    ): List<Asset> {
        val allAssets = mutableListOf<Asset>()
        var page = 1
        val pageSize = 500
        while (true) {
            val request =
                SearchMetadataRequest(
                    albumIds = listOf(albumId),
                    withExif = true,
                    size = pageSize,
                    page = page,
                    type = getTypeFilter(),
                )
            val response = immichClient.getSharedAlbumAssets(key = sharedKey, searchRequest = request)
            if (response.isSuccessful) {
                val items = response.body()?.assets?.items ?: break
                allAssets.addAll(items.map { it.copy(albumName = albumName) })
                if (items.size < pageSize) break // last page
                page++
            } else {
                Timber.e("Failed to fetch shared album assets (v3, page $page). Code: ${response.code()}")
                break
            }
        }
        return allAssets
    }

    /**
     * Fetches all assets for a given album on Immich v3 using POST /api/search/metadata.
     * Paginates with page size 500 until all assets are retrieved.
     */
    private suspend fun fetchAlbumAssetsV3(
        albumId: String,
        albumName: String,
    ): List<Asset> {
        val allAssets = mutableListOf<Asset>()
        var page = 1
        val pageSize = 500
        while (true) {
            val request =
                SearchMetadataRequest(
                    albumIds = listOf(albumId),
                    withExif = true,
                    size = pageSize,
                    page = page,
                    type = getTypeFilter(),
                )
            val response = immichClient.getAlbumAssets(apiKey = apiKey, searchRequest = request)
            if (response.isSuccessful) {
                val items = response.body()?.assets?.items ?: break
                allAssets.addAll(items.map { it.copy(albumName = albumName) })
                if (items.size < pageSize) break // last page
                page++
            } else {
                Timber.e("Failed to fetch album assets (v3, page $page). Code: ${response.code()}")
                break
            }
        }
        return allAssets
    }

    suspend fun getSelectedAlbumFromAPI(): Album =
        coroutineScope {
            try {
                val selectedAlbumIds = prefs.selectedAlbumIds
                if (selectedAlbumIds.isEmpty()) {
                    return@coroutineScope Album(
                        id = "combined", // Use a special ID for the combined album
                        name = "",
                        description = "No albums selected",
                    )
                }

                Timber.d("Attempting to fetch ${selectedAlbumIds.size} selected albums")
                Timber.d("Selected Album IDs: $selectedAlbumIds")
                Timber.d("API Key (first 5 chars): ${apiKey.take(5)}...")

                val serverVersion = getServerVersion()

                val allAssets = mutableListOf<Asset>()
                val successfulAlbumNames = mutableListOf<String>()
                val albumNamesByAssetId = mutableMapOf<String, MutableSet<String>>()

                val albumDeferreds =
                    selectedAlbumIds.map { albumId ->
                        async {
                            Pair(albumId, immichClient.getAlbum(apiKey = apiKey, albumId = albumId))
                        }
                    }

                val albumResponses = albumDeferreds.awaitAll()

                for (albumResponse in albumResponses) {
                    val albumId = albumResponse.first
                    val response = albumResponse.second
                    Timber.d("API Request for album $albumId - URL: ${response.raw().request.url}")

                    if (response.isSuccessful) {
                        val album = response.body()
                        if (album != null) {
                            successfulAlbumNames.add(album.name)

                            if (serverVersion >= 3) {
                                // v3: assets are not inline; fetch via search/metadata
                                Timber.d("Fetching assets for album ${album.name} via search (v3)")
                                val assets = fetchAlbumAssetsV3(albumId, album.name)
                                Timber.d("Fetched ${assets.size} assets for album: ${album.name}")
                                allAssets.addAll(assets)
                                assets.forEach { asset ->
                                    albumNamesByAssetId.getOrPut(asset.id) { mutableSetOf() }.add(album.name)
                                }
                            } else {
                                // v2: assets are inline
                                Timber.d("Successfully fetched album: ${album.name}, assets: ${album.assets.size}")
                                val albumAssets = album.assets.map { it.copy(albumName = album.name) }
                                allAssets.addAll(albumAssets)
                                albumAssets.forEach { asset ->
                                    albumNamesByAssetId.getOrPut(asset.id) { mutableSetOf() }.add(album.name)
                                }
                            }
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
                val successfulAlbumCount = successfulAlbumNames.size
                val isSingleAlbumSelection = successfulAlbumCount == 1
                val uniqueAssets =
                    allAssets
                        .distinctBy { it.id }
                        .map { asset ->
                            val albumNames = albumNamesByAssetId[asset.id].orEmpty()
                            val resolvedAlbumName =
                                when {
                                    isSingleAlbumSelection -> albumNames.singleOrNull()
                                    albumNames.size == 1 -> albumNames.first()
                                    else -> null
                                }
                            asset.copy(albumName = resolvedAlbumName)
                        }
                Timber.d(
                    "Combined ${allAssets.size} assets from $successfulAlbumCount successful albums " +
                        "(${selectedAlbumIds.size} selected), ${uniqueAssets.size} unique assets",
                )

                // Return a combined album with all assets
                return@coroutineScope Album(
                    id = "combined", // Use a special ID for the combined album
                    name = successfulAlbumNames.joinToString(", "),
                    description = "Combined album from $successfulAlbumCount selected albums",
                    assetCount = uniqueAssets.size,
                    assets = uniqueAssets,
                )
            } catch (e: Exception) {
                Timber.e(e, "Exception while fetching selected albums")
                throw Exception("Failed to fetch selected albums", e)
            }
        }

    private fun getTypeFilter(): String? =
        when (prefs.mediaType) {
            ProviderMediaType.VIDEOS -> "VIDEO"
            ProviderMediaType.PHOTOS -> "IMAGE"
            ProviderMediaType.VIDEOS_PHOTOS -> null
            else -> null
        }

    suspend fun getFavoriteAssetsFromAPI(): List<Asset> {
        try {
            val count = prefs.includeFavorites.toIntOrNull() ?: return emptyList()
            Timber.d("Fetching up to $count favorite assets")
            val searchRequest =
                SearchMetadataRequest(
                    isFavorite = true,
                    withExif = true,
                    type = getTypeFilter(),
                )
            val response = immichClient.getFavoriteAssets(apiKey = apiKey, searchRequest = searchRequest)
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

    suspend fun getRatedAssetsFromAPI(): List<Asset> =
        coroutineScope {
            val ratedAssets = mutableListOf<Asset>()
            try {
                val ratings = prefs.includeRatings
                if (ratings.isNotEmpty()) {
                    val ratedDeferreds =
                        ratings.map { rating ->
                            async {
                                Timber.d("Fetching rated assets with rating: $rating")
                                val searchRequest =
                                    SearchMetadataRequest(
                                        rating = rating.toInt(),
                                        withExif = true,
                                        type = getTypeFilter(),
                                    )
                                immichClient.getFavoriteAssets(apiKey = apiKey, searchRequest = searchRequest)
                            }
                        }

                    for (deferred in ratedDeferreds) {
                        val response = deferred.await()
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
            return@coroutineScope ratedAssets
        }

    suspend fun getRandomAssetsFromAPI(): List<Asset> {
        try {
            val count = prefs.includeRandom.toIntOrNull() ?: return emptyList()
            Timber.d("Fetching $count random assets")
            val searchRequest =
                SearchMetadataRequest(
                    size = count,
                    withExif = true,
                    type = getTypeFilter(),
                )
            val response = immichClient.getRandomAssets(apiKey = apiKey, searchRequest = searchRequest)
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

    suspend fun getRecentAssetsFromAPI(): List<Asset> {
        try {
            val count = prefs.includeRecent.toIntOrNull() ?: return emptyList()
            Timber.d("Fetching $count recent assets")
            val searchRequest =
                SearchMetadataRequest(
                    size = count,
                    order = "desc",
                    withExif = true,
                    type = getTypeFilter(),
                )
            val response = immichClient.getRecentAssets(apiKey = apiKey, searchRequest = searchRequest)
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
        coroutineScope {
            try {
                val serverVersion = getServerVersion()

                val regularDeferred =
                    async {
                        if (serverVersion >= 3) {
                            immichClient.getAlbumsV3(apiKey = apiKey)
                        } else {
                            immichClient.getAlbums(apiKey = apiKey)
                        }
                    }
                val sharedDeferred =
                    async {
                        if (serverVersion >= 3) {
                            immichClient.getAlbumsV3(apiKey = apiKey, isShared = true)
                        } else {
                            immichClient.getAlbums(apiKey = apiKey, shared = true)
                        }
                    }

                // Fetch regular albums
                val regularResponse = regularDeferred.await()
                val regularAlbums =
                    if (regularResponse.isSuccessful) {
                        regularResponse.body() ?: emptyList()
                    } else {
                        val errorBody = regularResponse.errorBody()?.string() ?: ""
                        val errorMessage =
                            try {
                                val errorResponse = Json.decodeFromString<ErrorResponse>(errorBody)
                                // v3 may have an `errors` array; fall back to `message` for v2
                                errorResponse.errors.firstOrNull() ?: errorResponse.message
                            } catch (e: Exception) {
                                Timber.e(e, "Error parsing error body: $errorBody")
                                regularResponse.message()
                            }
                        return@coroutineScope Result.failure(Exception("${regularResponse.code()} - $errorMessage"))
                    }

                // Fetch shared albums
                val sharedResponse = sharedDeferred.await()
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
                Timber.d(
                    "Fetched ${regularAlbums.size} regular albums and ${sharedAlbums.size} shared albums (${allAlbums.size} total unique)",
                )

                Result.success(allAlbums)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
