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
        @Query("key") key: String,
        @Query("password") password: String?,
    ): Response<Album>

    @GET("/api/albums")
    suspend fun getAlbums(
        @Header("x-api-key") apiKey: String,
    ): Response<List<Album>>

    @GET("/api/albums/{id}")
    suspend fun getAlbum(
        @Header("x-api-key") apiKey: String,
        @Path("id") albumId: String,
    ): Response<Album>

    @POST("/api/search/metadata")
    suspend fun getFavoriteAssets(
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
    val isFavorite: Boolean = true,
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
data class ErrorResponse(
    val message: String = "",
    val error: String = "",
    val statusCode: Int = 0,
    @SerialName("correlationId")
    val correlationId: String = "",
)
