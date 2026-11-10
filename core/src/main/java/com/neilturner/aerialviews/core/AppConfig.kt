package com.neilturner.aerialviews.core

/**
 * Build-time values that library modules are not allowed to read from `:app`'s `BuildConfig`.
 *
 * `:app` calls [initialize] once from `Application.onCreate`, before any code reads a value or
 * constructs an HTTP client. Values are immutable afterwards, so `:core` and `:playback` never
 * need `buildConfig` enabled and never see `secrets.properties` or the `testConfig` property.
 */
object AppConfig {
    @Volatile
    private var values: Values? = null

    fun initialize(values: Values) {
        this.values = values
    }

    val openWeatherApiKey: String get() = current().openWeatherApiKey
    val versionCode: Int get() = current().versionCode
    val versionName: String get() = current().versionName
    val isDebug: Boolean get() = current().isDebug
    val isTestConfig: Boolean get() = current().isTestConfig
    val flavor: String get() = current().flavor
    val buildType: String get() = current().buildType

    private fun current(): Values = checkNotNull(values) { "AppConfig.initialize() must be called from Application.onCreate()" }

    data class Values(
        val openWeatherApiKey: String,
        val versionCode: Int,
        val versionName: String,
        val isDebug: Boolean,
        val isTestConfig: Boolean,
        val flavor: String,
        val buildType: String,
    )
}
