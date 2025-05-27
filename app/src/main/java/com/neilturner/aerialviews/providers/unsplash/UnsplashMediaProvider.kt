package com.neilturner.aerialviews.providers.unsplash

import android.content.Context
import androidx.core.net.toUri
import com.neilturner.aerialviews.BuildConfig
import com.neilturner.aerialviews.models.enums.AerialMediaSource
import com.neilturner.aerialviews.models.enums.AerialMediaType
import com.neilturner.aerialviews.models.enums.ProviderSourceType
import com.neilturner.aerialviews.models.prefs.UnsplashMediaPrefs
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.providers.MediaProvider
import com.neilturner.aerialviews.utils.JsonHelper.buildSerializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import timber.log.Timber

class UnsplashMediaProvider(
    context: Context,
    private val prefs: UnsplashMediaPrefs,
) : MediaProvider(context) {
    override val type = ProviderSourceType.REMOTE
    override val enabled: Boolean
        get() = prefs.enabled

    private val unsplashClient by lazy {
        Timber.i("Connecting to Unsplash API")

        val okHttpClient = OkHttpClient
            .Builder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()

        Retrofit
            .Builder()
            .baseUrl("https://api.unsplash.com/")
            .client(okHttpClient)
            .addConverterFactory(buildSerializer())
            .build()
            .create(UnsplashApi::class.java)
    }

    override suspend fun fetchMedia(): List<AerialMedia> = fetchUnsplashMedia().first

    override suspend fun fetchTest(): String = fetchUnsplashMedia().second

    override suspend fun fetchMetadata(): MutableMap<String, Pair<String, Map<Int, String>>> =
        mutableMapOf<String, Pair<String, Map<Int, String>>>()

    private suspend fun fetchUnsplashMedia(): Pair<List<AerialMedia>, String> =
        withContext(Dispatchers.IO) {
            val media = mutableListOf<AerialMedia>()
            var totalPhotos = 0
            var rateLimitInfo = ""

            val accessKey = BuildConfig.UNSPLASH
            if (accessKey.isEmpty()) {
                return@withContext Pair(media, "Unsplash API key not configured")
            }
            val authHeader = "Client-ID $accessKey"

            try {
                val photos: List<UnsplashPhoto>
                var response: Response<*>? = null

                if (prefs.searchQuery.isNotEmpty()) {
                    // Search for specific photos
                    val searchResponse =
                        unsplashClient.searchPhotos(
                            authorization = authHeader,
                            query = prefs.searchQuery,
                            // perPage = prefs.photosPerPage.toIntOrNull() ?: 30,
                            perPage = 30,
                            orderBy = prefs.orderBy,
                            orientation = if (prefs.orientation == "any") null else prefs.orientation,
                        )

                    response = searchResponse
                    if (searchResponse.isSuccessful) {
                        totalPhotos = searchResponse.body()?.total ?: 0
                        photos = searchResponse.body()?.results ?: emptyList()
                    } else {
                        val errorMsg = searchResponse.errorBody()?.string() ?: searchResponse.message()
                        return@withContext Pair(media, "API Error: ${searchResponse.code()} - $errorMsg")
                    }
                } else {
                    // Get random photos
                    val randomResponse =
                        unsplashClient.getRandomPhotos(
                            authorization = authHeader,
                            //count = prefs.photosPerPage.toIntOrNull() ?: 30,
                            count = 30,
                            orientation = if (prefs.orientation == "any") null else prefs.orientation,
                        )

                    response = randomResponse
                    if (randomResponse.isSuccessful) {
                        val photoList = randomResponse.body() ?: emptyList()
                        totalPhotos = photoList.size
                        photos = photoList
                    } else {
                        val errorMsg = randomResponse.errorBody()?.string() ?: randomResponse.message()
                        return@withContext Pair(media, "API Error: ${randomResponse.code()} - $errorMsg")
                    }
                }

                // Extract rate limit headers if in debug mode
                if (BuildConfig.DEBUG) {
                    val rateLimit = response.headers()["X-Ratelimit-Limit"]
                    val rateRemaining = response.headers()["X-Ratelimit-Remaining"]

                    if (rateLimit != null && rateRemaining != null) {
                        rateLimitInfo = "Unsplash API Rate Limits: $rateRemaining/$rateLimit remaining"
                        Timber.d(rateLimitInfo)

                        // Show toast on UI thread
//                        withContext(Dispatchers.Main) {
//                            Toast.makeText(context, rateLimitInfo, Toast.LENGTH_LONG).show()
//                        }
                    }
                }

                // Convert Unsplash photos to AerialMedia
                photos.forEach { photo ->
                    val imageUrl =
                        when {
                            photo.width >= 1920 -> photo.urls.raw + "&w=1920&q=80"
                            photo.width >= 1080 -> photo.urls.full
                            else -> photo.urls.regular
                        }

                    var description =
                        listOfNotNull(
                            photo.description,
                            // photo.altDescription,
                            // "Photo by ${photo.user.name} on Unsplash",
                        ).firstOrNull() ?: ""

                    val limit = 85
                    if (description.length > limit) {
                        description = description.substring(0, limit) + "..."
                    }

                    val poi = mutableMapOf<Int, String>()
                    photo.location?.let { location ->
                        val locationText =
                            listOfNotNull(
                                location.city,
                                location.country,
                            ).joinToString(", ")
                        if (locationText.isNotEmpty()) {
                            poi[0] = locationText
                        }
                    }

                    val item =
                        AerialMedia(
                            uri = imageUrl.toUri(),
                            description = description,
                            poi = poi,
                            type = AerialMediaType.IMAGE,
                            source = AerialMediaSource.UNSPLASH,
                        )

                    media.add(item)
                }

                // TODO
                // To track downloads, add it to the player

                // Track downloads (required by Unsplash API guidelines)
                // Note: This should ideally be done when the image is actually displayed,
                // but for now we'll track when fetched
//                photos.forEach { photo ->
//                    try {
//                        unsplashClient.trackDownload(authHeader, photo.id)
//                    } catch (e: Exception) {
//                        // Don't fail the whole operation if tracking fails
//                        Timber.w(e, "Failed to track download for photo ${photo.id}")
//                    }
//                }

                val message =
                    if (prefs.searchQuery.isNotEmpty()) {
                        "Search query: '${prefs.searchQuery}'\n" +
                            "Total matching photos: $totalPhotos\n" +
                            "Photos fetched: ${media.size}\n" +
                            "Orientation: ${prefs.orientation}\n" +
                            "Order by: ${prefs.orderBy}" +
                            if (rateLimitInfo.isNotEmpty()) "\n\n$rateLimitInfo" else ""
                    } else {
                        "Random photos fetched: ${media.size}\n" +
                            "Orientation: ${prefs.orientation}" +
                            if (rateLimitInfo.isNotEmpty()) "\n\n$rateLimitInfo" else ""
                    }

                Timber.i("Unsplash photos found: ${media.size}")
                return@withContext Pair(media, message)
            } catch (e: Exception) {
                Timber.e(e, "Error fetching Unsplash media")
                return@withContext Pair(emptyList(), e.message ?: "Unknown error occurred")
            }
        }
}
