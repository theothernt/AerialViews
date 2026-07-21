package tv.projectivy.plugin.wallpaperprovider.api

/**
 * IPC contract between wallpaper plugins and Projectivy.
 * Plugins can send a broadcast with [ACTION_WALLPAPER_PROVIDER_UPDATED]
 * to signal that their configuration or data has changed and that a refresh is needed.
 */
object WallpaperProviderContract {
    /** Action broadcast by a plugin when its potential wallpapers have changed. */
    const val ACTION_WALLPAPER_PROVIDER_UPDATED =
        "tv.projectivy.plugin.action.WALLPAPER_PROVIDER_UPDATED"

    /** Mandatory String extra: provider identifier (UUID) as seen by Projectivy. */
    const val EXTRA_PROVIDER_ID = "tv.projectivy.plugin.extra.PROVIDER_ID"

    /** Optional Int extra: reason for the update (see [UpdateReason]). */
    const val EXTRA_UPDATE_REASON = "tv.projectivy.plugin.extra.UPDATE_REASON"

    object UpdateReason {
        /** User-facing preferences in the plugin have changed. */
        const val PREFS_CHANGED: Int = 1

        /** Remote data has changed (new wallpapers available, etc.). */
        const val DATA_CHANGED: Int = 2
    }
}