package com.neilturner.aerialviews.providers.unsplash

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

interface UnsplashApi {
    
    @GET("search/photos")
    suspend fun searchPhotos(
        @Header("Authorization") authorization: String,
        @Query("query") query: String,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 30,
        @Query("order_by") orderBy: String = "relevant",
        @Query("orientation") orientation: String? = null
    ): Response<UnsplashSearchResponse>
    
    @GET("photos/random")
    suspend fun getRandomPhotos(
        @Header("Authorization") authorization: String,
        @Query("count") count: Int = 30,
        @Query("query") query: String? = null,
        @Query("orientation") orientation: String? = null
    ): Response<List<UnsplashPhoto>>
    
    @GET("photos")
    suspend fun listPhotos(
        @Header("Authorization") authorization: String,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 30,
        @Query("order_by") orderBy: String = "latest"
    ): Response<List<UnsplashPhoto>>
    
    @GET("photos/{id}/download")
    suspend fun trackDownload(
        @Header("Authorization") authorization: String,
        @Path("id") photoId: String
    ): Response<Unit>
}
