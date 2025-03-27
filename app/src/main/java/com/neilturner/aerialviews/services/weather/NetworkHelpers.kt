package com.neilturner.aerialviews.services.weather

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Converter
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.hours

object NetworkHelpers {
    @Suppress("DEPRECATION")
    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            val networkInfo = connectivityManager.activeNetworkInfo
            return networkInfo != null && networkInfo.isConnected
        }
    }

    fun buildSerializer(): Converter.Factory {
        val contentType = "application/json".toMediaType()

        val json =
            Json {
                ignoreUnknownKeys = true
                isLenient = true
            }

        return json.asConverterFactory(contentType)
    }

    fun buildOkHttpClient(context: Context): OkHttpClient {
        val cache = Cache(File(context.cacheDir, "weather_cache"), 1 * 1024 * 1024.toLong())

        return OkHttpClient
            .Builder()
            .cache(cache)
            .addInterceptor(logging)
            .addInterceptor(cacheStatusInterceptor)
            .addInterceptor(offlineCacheInterceptor(context))
            .addNetworkInterceptor(onlineCacheInterceptor())
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private val cacheStatusInterceptor by lazy {
        object : Interceptor {
            override fun intercept(chain: Interceptor.Chain): Response {
                val response = chain.proceed(chain.request())
                val isFromCache = response.cacheResponse != null
                Timber.Forest.i("Cache status: ${if (isFromCache) "from cache" else "from network"}")
                return response
            }
        }
    }

    private val logging by lazy {
        HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
    }

    fun offlineCacheInterceptor(context: Context) =
        Interceptor { chain ->
            var request = chain.request()
            if (!isNetworkAvailable(context)) {
                Timber.Forest.i("Using offline cache...")
                val maxStale = 12.hours.inWholeSeconds.toInt()
                request =
                    request
                        .newBuilder()
                        .header("Cache-Control", "public, only-if-cached, max-stale=$maxStale")
                        .build()
            }
            chain.proceed(request)
        }

    fun onlineCacheInterceptor() =
        Interceptor { chain ->
            val cacheControlHeader = "Cache-Control"
            val cacheControlNoCache = "no-cache"

            val request = chain.request()
            val originalResponse = chain.proceed(request)

            val shouldUseCache = request.header(cacheControlHeader) != cacheControlNoCache
            if (!shouldUseCache) return@Interceptor originalResponse

            val cacheControl =
                CacheControl
                    .Builder()
                    .maxAge(5, TimeUnit.MINUTES)
                    .build()

            return@Interceptor originalResponse
                .newBuilder()
                .header(cacheControlHeader, cacheControl.toString())
                .build()
        }
}
