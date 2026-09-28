package com.neilturner.aerialviews.data.network

import com.neilturner.aerialviews.BuildConfig
import okhttp3.logging.HttpLoggingInterceptor

/**
 * Single source of truth for OkHttp wire logging.
 *
 * Returns null outside debug builds so R8 constant-folds the branch and no
 * logging interceptor is ever attached to a release client. BuildConfig.DEBUG
 * must be read directly here for that folding to happen.
 *
 * The interceptor logs via okhttp's AndroidPlatform, which writes to
 * android.util.Log.println rather than Timber, so it is not covered by the
 * Timber tree gate in AerialApp.
 */
object HttpLogging {
    fun interceptor(): HttpLoggingInterceptor? =
        if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.HEADERS
                redactHeader("Authorization")
                redactHeader("X-API-Key")
                redactHeader("Proxy-Authorization")
                redactHeader("Cookie")
            }
        } else {
            null
        }
}
