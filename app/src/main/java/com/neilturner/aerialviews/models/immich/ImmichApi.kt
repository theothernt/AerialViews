package com.neilturner.aerialviews.models.immich
import com.google.gson.annotations.SerializedName

data class ExifInfo(
    val description: String? = null,
    val country: String? = null,
    val state: String? = null,
    val city: String? = null,
)

data class Asset(
    val id: String = "",
    val type: String? = null,
    val originalPath: String? = null,
    val exifInfo: ExifInfo? = null,
)

data class Album(
    @SerializedName("id")
    val id: String = "",

    @SerializedName("albumName")
    val name: String = "",

    @SerializedName("description")
    val description: String? = null,

    @SerializedName("shared")
    val type: String? = null,

    @SerializedName("assets")
    val assets: List<Asset> = emptyList(),

    @SerializedName("assetCount")
    val assetCount: Int = 0
)

data class ErrorResponse(
    val message: String = "",
    val error: String = "",
    val statusCode: Int = 0,
    @SerializedName("correlationId") val correlationId: String = ""
)
