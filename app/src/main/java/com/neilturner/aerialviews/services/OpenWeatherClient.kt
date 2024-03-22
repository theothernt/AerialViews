package com.neilturner.aerialviews.services

import android.content.Context
import com.neilturner.aerialviews.BuildConfig
import com.neilturner.aerialviews.models.openweather.OpenWeatherAPI
import com.neilturner.aerialviews.utils.NetworkHelper
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.create
import java.io.File
import java.util.concurrent.TimeUnit

class OpenWeatherClient(private val context: Context) {

    // Create 1MB cache for HTTP responses
    private fun cache(): Cache {
        val cacheSize = 1 * 1024 * 1024 // 1 MB
        val cacheFolder = File(context.cacheDir, "http-cache")
        return Cache(cacheFolder, cacheSize.toLong())
    }

    // Cache online request, cache offline for longer
    private val offlineInterceptor = Interceptor { chain ->
        var request = chain.request()
        val cache = CacheControl.Builder()
        if (!NetworkHelper.isInternetAvailable(context)) {
            cache.onlyIfCached()
            if (BuildConfig.DEBUG) {
                cache.maxStale(1, TimeUnit.MINUTES)
            } else {
                cache.maxStale(2, TimeUnit.DAYS)
            }
        } else {
            if (BuildConfig.DEBUG) {
                cache.maxStale(1, TimeUnit.MINUTES)
            } else {
                cache.maxStale(12, TimeUnit.HOURS)
            }
        }
        request = request.newBuilder()
            .header(HEADER_CACHE_CONTROL, cache.build().toString())
            .build()
        chain.proceed(request)
    }

    private fun okHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor()
            .setLevel(HttpLoggingInterceptor.Level.BASIC)

        val client = OkHttpClient
            .Builder()
            .addInterceptor(offlineInterceptor)
            .cache(cache())

        if (BuildConfig.DEBUG) {
            client.addInterceptor(loggingInterceptor)
        }

        return client.build()
    }

    val client by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(MoshiConverterFactory.create())
            .client(okHttpClient())
            .build()
            .create<OpenWeatherAPI>()
    }

    companion object {
        private const val BASE_URL = "https://api.openweathermap.org/"
        private const val HEADER_CACHE_CONTROL = "Cache-Control"
        private const val TAG = "OpenWeatherClient"
    }
}
