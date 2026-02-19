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
    @GET("/api/shared-links/me")
    suspend fun getSharedAlbum(
        @Query("key") key: String? = null,
        @Query("slug") slug: String? = null,
        @Query("password") password: String? = null,
    ): Response<SharedLinkResponse>

    @GET("/api/albums")
    suspend fun getAlbums(
        @Header("x-api-key") apiKey: String,
        @Query("shared") shared: Boolean? = null,
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
}

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
)

@Serializable
data class ExifInfo(
    val description: String? = null,
    val country: String? = null,
    val state: String? = null,
    val city: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("dateTimeOriginal")
    val dateTimeOriginal: String? = null,
    @SerialName("dateTime")
    val dateTime: String? = null,
    @SerialName("offsetTimeOriginal")
    val offsetTimeOriginal: String? = null,
    @SerialName("offsetTime")
    val offsetTime: String? = null,
)

@Serializable
data class Asset(
    val id: String = "",
    val type: String = "",
    val originalPath: String = "",
    val exifInfo: ExifInfo? = null,
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
    val message: String = "",
    val error: String = "",
    val statusCode: Int = 0,
    @SerialName("correlationId")
    val correlationId: String = "",
)
