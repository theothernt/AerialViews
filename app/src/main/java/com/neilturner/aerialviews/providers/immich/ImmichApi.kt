package com.neilturner.aerialviews.providers.immich
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ImmichApi {
    @GET("/api/server/version")
    suspend fun getServerVersion(): Response<ServerVersionResponse>

    @GET("/api/shared-links/me")
    suspend fun getSharedAlbum(
        @Query("key") key: String? = null,
        @Query("slug") slug: String? = null,
        @Query("password") password: String? = null,
    ): Response<SharedLinkResponse>

    // v3: no password query param on shared-links/me
    @GET("/api/shared-links/me")
    suspend fun getSharedAlbumV3(
        @Query("key") key: String? = null,
        @Query("slug") slug: String? = null,
    ): Response<SharedLinkResponse>

    @GET("/api/albums")
    suspend fun getAlbums(
        @Header("x-api-key") apiKey: String,
        @Query("shared") shared: Boolean? = null,
    ): Response<List<Album>>

    // v3: renamed param from `shared` to `isShared`
    @GET("/api/albums")
    suspend fun getAlbumsV3(
        @Header("x-api-key") apiKey: String,
        @Query("isShared") isShared: Boolean? = null,
    ): Response<List<Album>>

    @GET("/api/albums/{id}")
    suspend fun getAlbum(
        @Header("x-api-key") apiKey: String,
        @Path("id") albumId: String,
    ): Response<Album>

    @GET("/api/albums/{id}")
    suspend fun getSharedAlbumById(
        @Path("id") albumId: String,
        @Query("key") key: String,
        @Query("password") password: String? = null,
    ): Response<Album>

    // v3: no password query param; assets are not inline so just fetch metadata
    @GET("/api/albums/{id}")
    suspend fun getSharedAlbumByIdV3(
        @Path("id") albumId: String,
        @Query("key") key: String,
    ): Response<Album>

    @POST("/api/search/metadata")
    suspend fun getFavoriteAssets(
        @Header("x-api-key") apiKey: String,
        @Body searchRequest: SearchMetadataRequest,
    ): Response<SearchAssetsResponse>

    @POST("/api/search/random")
    suspend fun getRandomAssets(
        @Header("x-api-key") apiKey: String,
        @Body searchRequest: SearchMetadataRequest,
    ): Response<List<Asset>>

    @POST("/api/search/metadata")
    suspend fun getRecentAssets(
        @Header("x-api-key") apiKey: String,
        @Body searchRequest: SearchMetadataRequest,
    ): Response<SearchAssetsResponse>

    // v3: fetch album assets via search/metadata with albumIds filter
    @POST("/api/search/metadata")
    suspend fun getAlbumAssets(
        @Header("x-api-key") apiKey: String,
        @Body searchRequest: SearchMetadataRequest,
    ): Response<SearchAssetsResponse>

    // v3: fetch shared album assets via search/metadata with albumIds and shared link key
    @POST("/api/search/metadata")
    suspend fun getSharedAlbumAssets(
        @Query("key") key: String,
        @Body searchRequest: SearchMetadataRequest,
    ): Response<SearchAssetsResponse>
}

@Serializable
data class ServerVersionResponse(
    val major: Int = 0,
    val minor: Int = 0,
    val patch: Int = 0,
)

@Serializable
data class SearchAssetsResponse(
    val assets: AssetsResult,
)

@Serializable
data class AssetsResult(
    val items: List<Asset>,
)

@Serializable
data class SearchMetadataRequest(
    val isFavorite: Boolean? = null,
    val rating: Int? = null,
    val order: String? = null,
    val size: Int? = null,
    val withExif: Boolean? = null,
    val type: String? = null,
    val albumIds: List<String>? = null,
    val page: Int? = null,
)

@Serializable
data class ExifInfo(
    val description: String? = null,
    val country: String? = null,
    val state: String? = null,
    val city: String? = null,
)

@Serializable
data class Asset(
    val id: String = "",
    val type: String = "",
    val originalPath: String = "",
    val localDateTime: String? = null,
    val description: String? = null,
    val exifInfo: ExifInfo? = null,
    val albumName: String? = null,
)

@Serializable
data class Album(
    @SerialName("id")
    val id: String = "",
    @SerialName("albumName")
    val name: String = "",
    @SerialName("description")
    val description: String = "",
    @SerialName("shared")
    val type: String = "",
    @SerialName("assets")
    val assets: List<Asset> = emptyList(),
    @SerialName("assetCount")
    val assetCount: Int = 0,
)

@Serializable
data class SharedLinkResponse(
    val id: String = "",
    val description: String? = null,
    val password: String? = null,
    val token: String? = null,
    val userId: String = "",
    val key: String = "",
    val type: String = "",
    val createdAt: String = "",
    val expiresAt: String? = null,
    val assets: List<Asset> = emptyList(),
    val album: Album? = null,
    val allowUpload: Boolean = true,
    val allowDownload: Boolean = true,
    @SerialName("showMetadata")
    val showMetadata: Boolean = true,
    val slug: String? = null,
)

@Serializable
data class ErrorResponse(
    // v2 fields
    val message: String = "",
    val error: String = "",
    val statusCode: Int = 0,
    // v2 field; moved to X-Correlation-ID header in v3 but kept for backwards compat
    @SerialName("correlationId")
    val correlationId: String = "",
    // v3 field: array of error detail strings
    val errors: List<String> = emptyList(),
)
