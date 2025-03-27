package com.neilturner.aerialviews.services.weather

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

interface LocationApi {
    @GET("geo/1.0/direct")
    suspend fun getLocationByName(
        @Query("q") locationName: String,
        @Query("limit") limit: Int = 10,
        @Query("appid") apiKey: String,
    ): List<LocationResponse>
}

@Serializable
data class LocationResponse(
    val name: String,
    val lat: Double,
    val lon: Double,
    val country: String,
    val state: String? = null,
) {
    fun getDisplayName(): String =
        if (state != null) {
            "$name, $state, $country"
        } else {
            "$name, $country"
        }
}
