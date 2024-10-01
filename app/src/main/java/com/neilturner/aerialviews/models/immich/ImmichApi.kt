package com.neilturner.aerialviews.models.immich

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
    val id: String = "",
    val description: String? = null,
    val type: String? = null,
    val assets: List<Asset>,
)
