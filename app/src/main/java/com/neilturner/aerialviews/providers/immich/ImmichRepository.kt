package com.neilturner.aerialviews.providers.immich

import com.neilturner.aerialviews.models.enums.ImmichAuthType
import com.neilturner.aerialviews.models.enums.ProviderMediaType
import com.neilturner.aerialviews.models.prefs.ImmichMediaPrefs
import com.neilturner.aerialviews.utils.JsonHelper.buildSerializer
import com.neilturner.aerialviews.utils.ServerConfig
import com.neilturner.aerialviews.utils.SslHelper
import com.neilturner.aerialviews.utils.UrlParser
import kotlinx.serialization.json.Json
import retrofit2.Retrofit
import timber.log.Timber

class ImmichRepository(
    private val prefs: ImmichMediaPrefs,
    private val urlBuilder: ImmichUrlBuilder
) {
    lateinit var server: String

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

    suspend fun getSharedAlbumFromAPI(): Album {
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

    suspend fun getSelectedAlbumFromAPI(): Album {
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
            val searchRequest = SearchMetadataRequest(
                isFavorite = true,
                withExif = true,
                type = getTypeFilter(),
            )
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

    suspend fun getRatedAssetsFromAPI(): List<Asset> {
        val ratedAssets = mutableListOf<Asset>()
        try {
            val ratings = prefs.includeRatings
            if (ratings.isNotEmpty()) {
                for (rating in ratings) {
                    Timber.d("Fetching rated assets with rating: $rating")
                    val searchRequest = SearchMetadataRequest(
                        rating = rating.toInt(),
                        withExif = true,
                        type = getTypeFilter(),
                    )
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

    suspend fun getRandomAssetsFromAPI(): List<Asset> {
        try {
            val count = prefs.includeRandom.toIntOrNull() ?: return emptyList()
            Timber.d("Fetching $count random assets")
            val searchRequest = SearchMetadataRequest(
                size = count,
                withExif = true,
                type = getTypeFilter(),
            )
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

    suspend fun getRecentAssetsFromAPI(): List<Asset> {
        try {
            val count = prefs.includeRecent.toIntOrNull() ?: return emptyList()
            Timber.d("Fetching $count recent assets")
            val searchRequest = SearchMetadataRequest(
                size = count,
                order = "desc",
                withExif = true,
                type = getTypeFilter(),
            )
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
            .replace(Regex("^/+|+/$"), "") // Remove leading and trailing slashes
            .replace(Regex("^(share|s)/"), "") // Support both "/share/<key>" and "/s/<slug>" formats
    }

    private fun isSlugFormat(input: String): Boolean {
        val trimmed = input.trim().replace(Regex("^/+"), "")
        return trimmed.startsWith("s/")
    }

    // Moved this to be available earlier for initial connection string
    fun getServerUrl(): String {
        if (!this::server.isInitialized) {
            server = UrlParser.parseServerUrl(prefs.url)
        }
        return server
    }
}
