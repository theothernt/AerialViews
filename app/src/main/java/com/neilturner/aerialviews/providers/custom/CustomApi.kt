package com.neilturner.aerialviews.providers.custom

import com.neilturner.aerialviews.models.videos.Comm1Video
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Url

interface CustomApi {
    @GET
    suspend fun getManifest(
        @Url url: String,
    ): Manifest

    @GET
    suspend fun getCustomVideos(
        @Url url: String,
    ): CustomVideos
}

@Serializable
data class CustomVideos(
    val assets: List<Comm1Video>? = null,
)

@Serializable
data class ManifestSource(
    val name: String,
    val description: String? = null,
    val scenes: List<String>? = null,
    val manifestUrl: String,
    val license: String? = null,
    val more: String? = null,
    val local: Boolean = false,
    val cacheable: Boolean = true,
)

@Serializable
data class Manifest(
    val sources: List<ManifestSource>,
)
