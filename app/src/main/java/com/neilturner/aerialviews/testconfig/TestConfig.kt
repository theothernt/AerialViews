package com.neilturner.aerialviews.testconfig

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Runtime test configuration.
 *
 * Each file describes the complete state of the app for one test run. The applier disables
 * every provider before applying a config, so a run only ever talks to the server under test.
 */
@Serializable
data class TestConfig(
    val name: String = "",
    val description: String = "",
    val general: GeneralConfig = GeneralConfig(),
    val providers: ProviderConfigs = ProviderConfigs(),
) {
    companion object {
        private val JSON = Json { ignoreUnknownKeys = true }

        fun parse(json: String): TestConfig = JSON.decodeFromString(json)
    }
}

/** Overrides for [com.neilturner.aerialviews.models.prefs.GeneralPrefs]. */
@Serializable
data class GeneralConfig(
    val playlistCache: Boolean? = null,
    val startScreensaverOnLaunch: Boolean? = null,
    val shuffleVideos: Boolean? = null,
    val autoTimeOfDay: Boolean? = null,
    val wifiOnly: Boolean? = null,
    val messageApiEnabled: Boolean? = null,
    val messageApiPort: String? = null,
)

/** Provider blocks. Any provider that is missing is disabled before the config is applied. */
@Serializable
data class ProviderConfigs(
    val customFeed: CustomFeedConfig? = null,
    val samba: SambaConfig? = null,
    val samba2: SambaConfig? = null,
    val webdav: WebDavConfig? = null,
    val webdav2: WebDavConfig? = null,
    val immich: ImmichConfig? = null,
    val ncMemories: NcMemoriesConfig? = null,
)

@Serializable
data class CustomFeedConfig(
    val enabled: Boolean = false,
    val urls: String? = null,
    val quality: String? = null,
)

@Serializable
data class SambaConfig(
    val enabled: Boolean = false,
    val hostName: String? = null,
    val domainName: String? = null,
    val shareName: String? = null,
    val userName: String? = null,
    val password: String? = null,
    val searchSubfolders: Boolean? = null,
    val enableEncryption: Boolean? = null,
    val mediaSelection: List<String>? = null,
)

@Serializable
data class WebDavConfig(
    val enabled: Boolean = false,
    val scheme: String? = null,
    val hostName: String? = null,
    val pathName: String? = null,
    val userName: String? = null,
    val password: String? = null,
    val searchSubfolders: Boolean? = null,
    val validateSsl: Boolean? = null,
    val mediaSelection: List<String>? = null,
)

@Serializable
data class ImmichConfig(
    val enabled: Boolean = false,
    val url: String? = null,
    val pathName: String? = null,
    val apiKey: String? = null,
    val authType: String? = null,
    val validateSsl: Boolean? = null,
    val imageType: String? = null,
    val videoType: String? = null,
    val mediaSelection: List<String>? = null,
    /** `DISABLED` or a count of 100/200/300/400 assets. */
    val includeFavorites: String? = null,
    /** `DISABLED` or a count of 100/200/300/400 assets. */
    val includeRandom: String? = null,
    /** `DISABLED` or a count of 100/200/300/400 assets. */
    val includeRecent: String? = null,
    /** Star ratings to include, each between 1 and 5. */
    val includeRatings: List<String>? = null,
)

@Serializable
data class NcMemoriesConfig(
    val enabled: Boolean = false,
    val url: String? = null,
    val username: String? = null,
    val password: String? = null,
    val validateSsl: Boolean? = null,
    val favoritesName: String? = null,
    val recentName: String? = null,
    val mediaSelection: List<String>? = null,
)
