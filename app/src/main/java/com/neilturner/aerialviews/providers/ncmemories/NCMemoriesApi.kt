package com.neilturner.aerialviews.providers.ncmemories

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Query

interface NCMemoriesApi {
    @Headers("OCS-APIRequest: true")
    @GET("/apps/memories/api/clusters/albums")
    suspend fun getAlbumList(
        @Header("Authorization") credential: String,
    ): Response<List<Album>>

    @Headers("OCS-APIRequest: true")
    @GET("/apps/memories/api/days")
    suspend fun getDays(
        @Header("Authorization") credential: String,
        @Query("albums") clusterId: String? = null,
        @Query("fav") fav: Int? = null,
        @Query("vid") vid: Int? = null,
    ): Response<List<Day>>

    @Headers("OCS-APIRequest: true")
    @GET("/apps/memories/api/days/{dayids}")
    suspend fun getImages(
        @Header("Authorization") credential: String,
        @Path("dayids") dayIds: String,
        @Query("albums") clusterId: String? = null,
        @Query("fav") fav: Int? = null,
        @Query("vid") vid: Int? = null,
        ): Response<List<Image>>

    @Headers("OCS-APIRequest: true")
    @GET("/apps/memories/api/image/info/{fileid}")
    suspend fun getFullImageInfo(
        @Header("Authorization") credential: String,
        @Path("fileid") fileId: Int,
    ): Response<Image>
}

@Serializable
data class Album(
    @SerialName("album_id")
    val albumId: Int,
    @SerialName("cluster_id")
    val clusterId: String,
    val name: String,
    val count: Int,
)

@Serializable
data class Day(
    @SerialName("dayid")
    val dayId: Int,
)

@Serializable
data class Image(
    @SerialName("fileid")
    val fileId: Int,
    val etag: String,
    @SerialName("basename")
    val baseName: String,
    val epoch: Long? = null,
    @SerialName("filename")
    val fileName: String? = null,
    val exif: ExifInfo? = null,
    val albumName: String? = "",
)

@Serializable
data class ExifInfo(
    @SerialName("DateTimeEpoch")
    val dateTimeEpoch: Long? = null,
    @SerialName("OffsetTimeOriginal")
    val offsetTimeOriginal: String? = null,
    @SerialName("GPSLatitude")
    val gpsLatitude: String? = null,
    @SerialName("GPSLongitude")
    val gpsLongitude: String? = null,
    @SerialName("Title")
    val title: String? = null,
    @SerialName("Description")
    val description: String? = null,
)

@Serializable
data class ErrorResponse(
    val message: String = "",
    val error: String = "",
    val statusCode: Int = 0,
    @SerialName("correlationId")
    val correlationId: String = "",
)
