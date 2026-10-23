package com.neilturner.aerialviews.testconfig

import android.content.Context
import com.neilturner.aerialviews.models.enums.ButtonType
import com.neilturner.aerialviews.models.enums.ImmichAuthType
import com.neilturner.aerialviews.models.enums.ImmichImageType
import com.neilturner.aerialviews.models.enums.ImmichVideoType
import com.neilturner.aerialviews.models.enums.NCMemoriesImageType
import com.neilturner.aerialviews.models.enums.NCMemoriesVideoType
import com.neilturner.aerialviews.models.enums.SchemeType
import com.neilturner.aerialviews.models.enums.VideoQuality
import com.neilturner.aerialviews.models.prefs.AmazonVideoPrefs
import com.neilturner.aerialviews.models.prefs.AppleVideoPrefs
import com.neilturner.aerialviews.models.prefs.Comm1VideoPrefs
import com.neilturner.aerialviews.models.prefs.Comm2VideoPrefs
import com.neilturner.aerialviews.models.prefs.CustomFeedPrefs
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.models.prefs.ImmichMediaPrefs
import com.neilturner.aerialviews.models.prefs.LocalMediaPrefs
import com.neilturner.aerialviews.models.prefs.NCMemoriesMediaPrefs
import com.neilturner.aerialviews.models.prefs.SambaMediaPrefs
import com.neilturner.aerialviews.models.prefs.SambaMediaPrefs2
import com.neilturner.aerialviews.models.prefs.WebDavMediaPrefs
import com.neilturner.aerialviews.models.prefs.WebDavMediaPrefs2
import com.neilturner.aerialviews.ui.helpers.PreferenceHelper
import timber.log.Timber

/**
 * Applies a [TestConfig] to the app preferences.
 *
 * Every provider is disabled first so that a config only ever exercises the provider it names.
 * Settings that need a companion preference are filled in automatically, for example
 * `startScreensaverOnLaunch` also needs a button mapped to `EXIT_TO_SETTINGS`.
 */
object TestConfigApplier {
    private const val SAMBA_MEDIA_SELECTION_KEY = "samba_media_selection"
    private const val WEBDAV_MEDIA_SELECTION_KEY = "webdav_media_selection"
    private const val IMMICH_MEDIA_SELECTION_KEY = "immich_media_selection"
    private const val IMMICH_INCLUDE_RATINGS_KEY = "immich_media_include_ratings"
    private const val NCMEMORIES_MEDIA_SELECTION_KEY = "ncmemories_media_selection"
    private const val NCMEMORIES_FAVORITES_NAME_KEY = "ncmemories_media_favorites_name"
    private const val NCMEMORIES_RECENT_NAME_KEY = "ncmemories_media_recent_name"

    fun apply(
        context: Context,
        config: TestConfig,
    ): String {
        disableAllProviders()
        applyGeneral(config.general)
        ensureExitToSettings()
        applyCustomFeed(config.providers.customFeed)
        applySamba(config.providers.samba, secondAccount = false)
        applySamba(config.providers.samba2, secondAccount = true)
        applyWebDav(config.providers.webdav, secondAccount = false)
        applyWebDav(config.providers.webdav2, secondAccount = true)
        applyImmich(context, config.providers.immich)
        applyNCMemories(context, config.providers.ncMemories)
        applyMediaSelections(context, config.providers)

        val summary = config.name.ifBlank { "unnamed" }
        Timber.i("TestConfigApplier: applied config '$summary'")
        return summary
    }

    private fun disableAllProviders() {
        CustomFeedPrefs.enabled = false
        SambaMediaPrefs.enabled = false
        SambaMediaPrefs2.enabled = false
        WebDavMediaPrefs.enabled = false
        WebDavMediaPrefs2.enabled = false
        ImmichMediaPrefs.enabled = false
        NCMemoriesMediaPrefs.enabled = false
        LocalMediaPrefs.enabled = false
        // The built in community feeds are enabled by default and would otherwise dominate the
        // playlist, so a test run would take a long time to reach the provider under test.
        Comm1VideoPrefs.enabled = false
        Comm2VideoPrefs.enabled = false
        AmazonVideoPrefs.enabled = false
        AppleVideoPrefs.enabled = false
    }

    private fun applyGeneral(general: GeneralConfig) {
        general.playlistCache?.let { GeneralPrefs.playlistCache = it }
        general.startScreensaverOnLaunch?.let { GeneralPrefs.startScreensaverOnLaunch = it }
        general.shuffleVideos?.let { GeneralPrefs.shuffleVideos = it }
        general.autoTimeOfDay?.let { GeneralPrefs.autoTimeOfDay = it }
        general.wifiOnly?.let { GeneralPrefs.wifiOnly = it }
        general.messageApiEnabled?.let { GeneralPrefs.messageApiEnabled = it }
        general.messageApiPort?.let { GeneralPrefs.messageApiPort = it }
    }

    /**
     * MainActivity only starts the screensaver on launch when a button is mapped to
     * [ButtonType.EXIT_TO_SETTINGS], so a test config that enables that option needs a way out.
     * OK press is unused by default, so map that rather than overwriting a common action.
     */
    private fun ensureExitToSettings() {
        if (!GeneralPrefs.startScreensaverOnLaunch || PreferenceHelper.isExitToSettingSet()) return
        GeneralPrefs.buttonOkPress = ButtonType.EXIT_TO_SETTINGS
        Timber.i("TestConfigApplier: mapped OK press to EXIT_TO_SETTINGS for start on launch")
    }

    private fun applyCustomFeed(config: CustomFeedConfig?) {
        if (config == null) return
        CustomFeedPrefs.enabled = config.enabled
        config.urls?.let { CustomFeedPrefs.urls = it }
        config.quality?.let { value ->
            enumOrNull<VideoQuality>(value)?.let { CustomFeedPrefs.quality = it }
        }
    }

    private fun applySamba(
        config: SambaConfig?,
        secondAccount: Boolean,
    ) {
        if (config == null) return
        if (secondAccount) {
            SambaMediaPrefs2.enabled = config.enabled
            config.hostName?.let { SambaMediaPrefs2.hostName = it }
            config.domainName?.let { SambaMediaPrefs2.domainName = it }
            config.shareName?.let { SambaMediaPrefs2.shareName = it }
            config.userName?.let { SambaMediaPrefs2.userName = it }
            config.password?.let { SambaMediaPrefs2.password = it }
            config.searchSubfolders?.let { SambaMediaPrefs2.searchSubfolders = it }
            config.enableEncryption?.let { SambaMediaPrefs2.enableEncryption = it }
        } else {
            SambaMediaPrefs.enabled = config.enabled
            config.hostName?.let { SambaMediaPrefs.hostName = it }
            config.domainName?.let { SambaMediaPrefs.domainName = it }
            config.shareName?.let { SambaMediaPrefs.shareName = it }
            config.userName?.let { SambaMediaPrefs.userName = it }
            config.password?.let { SambaMediaPrefs.password = it }
            config.searchSubfolders?.let { SambaMediaPrefs.searchSubfolders = it }
            config.enableEncryption?.let { SambaMediaPrefs.enableEncryption = it }
        }
    }

    private fun applyWebDav(
        config: WebDavConfig?,
        secondAccount: Boolean,
    ) {
        if (config == null) return
        if (secondAccount) {
            WebDavMediaPrefs2.enabled = config.enabled
            config.scheme?.let { value -> enumOrNull<SchemeType>(value)?.let { WebDavMediaPrefs2.scheme = it } }
            config.hostName?.let { WebDavMediaPrefs2.hostName = it }
            config.pathName?.let { WebDavMediaPrefs2.pathName = it }
            config.userName?.let { WebDavMediaPrefs2.userName = it }
            config.password?.let { WebDavMediaPrefs2.password = it }
            config.searchSubfolders?.let { WebDavMediaPrefs2.searchSubfolders = it }
            config.validateSsl?.let { WebDavMediaPrefs2.validateSsl = it }
        } else {
            WebDavMediaPrefs.enabled = config.enabled
            config.scheme?.let { value -> enumOrNull<SchemeType>(value)?.let { WebDavMediaPrefs.scheme = it } }
            config.hostName?.let { WebDavMediaPrefs.hostName = it }
            config.pathName?.let { WebDavMediaPrefs.pathName = it }
            config.userName?.let { WebDavMediaPrefs.userName = it }
            config.password?.let { WebDavMediaPrefs.password = it }
            config.searchSubfolders?.let { WebDavMediaPrefs.searchSubfolders = it }
            config.validateSsl?.let { WebDavMediaPrefs.validateSsl = it }
        }
    }

    private fun applyImmich(
        context: Context,
        config: ImmichConfig?,
    ) {
        if (config == null) return
        ImmichMediaPrefs.enabled = config.enabled
        config.url?.let { ImmichMediaPrefs.url = it }
        config.pathName?.let { ImmichMediaPrefs.pathName = it }
        config.apiKey?.let { ImmichMediaPrefs.apiKey = it }
        config.authType?.let { value -> enumOrNull<ImmichAuthType>(value)?.let { ImmichMediaPrefs.authType = it } }
        config.validateSsl?.let { ImmichMediaPrefs.validateSsl = it }
        config.imageType?.let { value -> enumOrNull<ImmichImageType>(value)?.let { ImmichMediaPrefs.imageType = it } }
        config.videoType?.let { value -> enumOrNull<ImmichVideoType>(value)?.let { ImmichMediaPrefs.videoType = it } }
        // Without at least one of these the provider has nothing to fetch and quietly returns
        // "No files found", so a config that only sets the connection details tests nothing.
        config.includeFavorites?.let { ImmichMediaPrefs.includeFavorites = it }
        config.includeRandom?.let { ImmichMediaPrefs.includeRandom = it }
        config.includeRecent?.let { ImmichMediaPrefs.includeRecent = it }
        config.includeRatings?.let { writeStringSet(context, IMMICH_INCLUDE_RATINGS_KEY, it) }
    }

    private fun applyNCMemories(
        context: Context,
        config: NcMemoriesConfig?,
    ) {
        if (config == null) return
        NCMemoriesMediaPrefs.enabled = config.enabled
        config.url?.let { NCMemoriesMediaPrefs.url = it }
        config.username?.let { NCMemoriesMediaPrefs.username = it }
        config.password?.let { NCMemoriesMediaPrefs.password = it }
        config.validateSsl?.let { NCMemoriesMediaPrefs.validateSsl = it }
        config.favoritesName?.let { writePref(context, NCMEMORIES_FAVORITES_NAME_KEY, it) }
        config.recentName?.let { writePref(context, NCMEMORIES_RECENT_NAME_KEY, it) }
    }

    private fun applyMediaSelections(
        context: Context,
        providers: ProviderConfigs,
    ) {
        val selections =
            listOf(
                SAMBA_MEDIA_SELECTION_KEY to (providers.samba?.mediaSelection ?: providers.samba2?.mediaSelection),
                WEBDAV_MEDIA_SELECTION_KEY to (providers.webdav?.mediaSelection ?: providers.webdav2?.mediaSelection),
                IMMICH_MEDIA_SELECTION_KEY to providers.immich?.mediaSelection,
                NCMEMORIES_MEDIA_SELECTION_KEY to providers.ncMemories?.mediaSelection,
            )
        selections.forEach { (key, selection) ->
            if (selection != null) writeMediaSelection(context, key, selection)
        }
    }

    /**
     * The media selection is a string set preference that is exposed as a read only property,
     * so it is written straight to the shared preferences file that Kotpref uses.
     */
    private fun writeMediaSelection(
        context: Context,
        key: String,
        selection: List<String>,
    ) {
        writeStringSet(context, key, selection)
    }

    /** Writes a string set preference that is exposed as a read only property. */
    private fun writeStringSet(
        context: Context,
        key: String,
        values: Collection<String>,
    ) {
        val prefs = context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
        prefs.edit().putStringSet(key, values.toSet()).apply()
    }

    /** Writes a preference that is exposed as a read only property. */
    private fun writePref(
        context: Context,
        key: String,
        value: String,
    ) {
        val prefs = context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
        prefs.edit().putString(key, value).apply()
    }

    private inline fun <reified T : Enum<T>> enumOrNull(value: String): T? =
        runCatching { enumValueOf<T>(value.trim().uppercase()) }.getOrNull()
}
