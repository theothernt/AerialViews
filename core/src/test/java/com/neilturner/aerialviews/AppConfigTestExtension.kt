package com.neilturner.aerialviews

import com.neilturner.aerialviews.core.AppConfig
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext

/** Supplies the build-time values that `:app` normally initializes in `Application.onCreate`. */
class AppConfigTestExtension : BeforeAllCallback {
    override fun beforeAll(context: ExtensionContext) {
        AppConfig.initialize(
            AppConfig.Values(
                openWeatherApiKey = "",
                versionCode = 153,
                versionName = "test",
                isDebug = false,
                isTestConfig = false,
                flavor = "beta",
                buildType = "debug",
            ),
        )
    }
}
