package com.neilturner.aerialviews.providers.unsplash

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UnsplashPhoto(
    val id: String,
    val description: String? = null,
    @SerialName("alt_description") val altDescription: String? = null,
    val urls: UnsplashUrls,
    val user: UnsplashUser,
    val location: UnsplashLocation? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val likes: Int = 0,
    val width: Int,
    val height: Int,
)

@Serializable
data class UnsplashUrls(
    val raw: String,
    val full: String,
    val regular: String,
    val small: String,
    val thumb: String,
)

@Serializable
data class UnsplashUser(
    val id: String,
    val username: String,
    val name: String,
    @SerialName("profile_image") val profileImage: UnsplashProfileImage? = null,
    val links: UnsplashUserLinks? = null,
)

@Serializable
data class UnsplashProfileImage(
    val small: String,
    val medium: String,
    val large: String,
)

@Serializable
data class UnsplashUserLinks(
    val self: String,
    val html: String,
    val photos: String,
    val likes: String,
    val portfolio: String? = null,
)

@Serializable
data class UnsplashLocation(
    val city: String? = null,
    val country: String? = null,
    val position: UnsplashPosition? = null,
)

@Serializable
data class UnsplashPosition(
    val latitude: Double? = null,
    val longitude: Double? = null,
)

@Serializable
data class UnsplashSearchResponse(
    val total: Int,
    @SerialName("total_pages") val totalPages: Int,
    val results: List<UnsplashPhoto>,
)

@Serializable
data class UnsplashRandomResponse(
    val photos: List<UnsplashPhoto>,
)
