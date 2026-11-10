package com.neilturner.aerialviews.services.weather

import android.content.Context
import com.neilturner.aerialviews.data.network.NetworkHelper
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.hours

object NetworkHelpers {
    private const val TIMEOUT = 10L // Seconds = socket, etc timeout
    private val timeoutUnits = TimeUnit.SECONDS
    private const val CACHESIZE = 1 * 1024 * 1024L // 10 MB
    private val offlineCacheTimeout = 2.hours.inWholeSeconds.toInt()
    private const val ONLINECACHETIMEOUT = 60 // Minutes

    fun isNetworkAvailable(context: Context): Boolean = NetworkHelper.isNetworkAvailable(context)

    fun buildOkHttpClient(context: Context): OkHttpClient {
        val cache = Cache(File(context.cacheDir, "weather_cache"), CACHESIZE)
        return OkHttpClient
            .Builder()
            .cache(cache)
            // .addInterceptor(logging)
            .addInterceptor(cacheStatusInterceptor)
            .addInterceptor(offlineCacheInterceptor(context))
            .addNetworkInterceptor(onlineCacheInterceptor())
            .connectTimeout(TIMEOUT, timeoutUnits)
            .readTimeout(TIMEOUT, timeoutUnits)
            .build()
    }

    private val cacheStatusInterceptor by lazy {
        Interceptor { chain ->
            val response = chain.proceed(chain.request())
            val isFromCache = response.cacheResponse != null
            Timber.i("Cache status: ${if (isFromCache) "from cache" else "from network"}")
            response
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
                Timber.i("Using offline cache...")
                val maxStale = offlineCacheTimeout
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
                    .maxAge(ONLINECACHETIMEOUT, timeoutUnits)
                    .build()

            return@Interceptor originalResponse
                .newBuilder()
                .header(cacheControlHeader, cacheControl.toString())
                .build()
        }
}
