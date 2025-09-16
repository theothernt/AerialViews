package com.neilturner.aerialviews.providers.custom

import com.neilturner.aerialviews.models.videos.Comm1Video
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Url

interface CustomFeedApi {
    @GET
    suspend fun getManifests(
        @Url url: String,
    ): FeedManifests

    @GET
    suspend fun getManifest(
        @Url url: String,
    ): FeedManifest

    @GET
    suspend fun getVideos(
        @Url url: String,
    ): FeedVideos
}

@Serializable
data class FeedVideos(
    val assets: List<Comm1Video>? = null,
)

@Serializable
data class FeedManifest(
    val name: String,
    val description: String? = null,
    val manifestUrl: String,
)

@Serializable
data class FeedManifests(
    val sources: List<FeedManifest>,
)
