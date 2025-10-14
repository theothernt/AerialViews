package com.neilturner.aerialviews.services.projectivy

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.neilturner.aerialviews.models.prefs.AppleVideoPrefs
import com.neilturner.aerialviews.models.prefs.Comm1VideoPrefs
import com.neilturner.aerialviews.models.prefs.Comm2VideoPrefs
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.models.prefs.LocalMediaPrefs
import com.neilturner.aerialviews.providers.AppleMediaProvider
import com.neilturner.aerialviews.providers.Comm1MediaProvider
import com.neilturner.aerialviews.providers.Comm2MediaProvider
import com.neilturner.aerialviews.providers.LocalMediaProvider
import com.neilturner.aerialviews.providers.MediaProvider
import kotlinx.coroutines.runBlocking
import tv.projectivy.plugin.wallpaperprovider.api.Event
import tv.projectivy.plugin.wallpaperprovider.api.IWallpaperProviderService
import tv.projectivy.plugin.wallpaperprovider.api.Wallpaper
import tv.projectivy.plugin.wallpaperprovider.api.WallpaperDisplayMode
import tv.projectivy.plugin.wallpaperprovider.api.WallpaperType

class WallpaperProviderService : Service() {
    override fun onBind(intent: Intent): IBinder {
        // Return the interface.
        return binder
    }

    private val binder =
        object : IWallpaperProviderService.Stub() {
            override fun getWallpapers(event: Event?): List<Wallpaper> =
                when (event) {
                    is Event.TimeElapsed -> {
                        // Get enabled providers based on shareProjectivyVideos preference
                        val enabledProviders = getEnabledProviders()

                        // Fetch media from all enabled providers
                        val aerialMediaList =
                            runBlocking {
                                enabledProviders
                                    // .filter { it.enabled }
                                    .flatMap { provider ->
                                        try {
                                            provider.fetchMedia()
                                        } catch (ex: Exception) {
                                            emptyList()
                                        }
                                    }
                            }.shuffled()

                        // Convert AerialMedia objects to Wallpaper objects
                        aerialMediaList.map { media ->
                            Wallpaper(
                                media.uri.toString(),
                                WallpaperType.VIDEO,
                                WallpaperDisplayMode.DEFAULT,
                                title = media.description,
                            )
                        }
                    }
                    else -> emptyList() // Returning an empty list won't change the currently displayed wallpaper
                }

            override fun getPreferences(): String? = null

            override fun setPreferences(params: String?) {
            }
        }

    private fun getEnabledProviders(): List<MediaProvider> {
        val providers = mutableListOf<MediaProvider>()
        val enabledSources = GeneralPrefs.shareProjectivyVideos

        if (enabledSources.contains("APPLE")) {
            providers.add(AppleMediaProvider(applicationContext, AppleVideoPrefs))
        }
        if (enabledSources.contains("COMM1")) {
            providers.add(Comm1MediaProvider(applicationContext, Comm1VideoPrefs))
        }
        if (enabledSources.contains("COMM2")) {
            providers.add(Comm2MediaProvider(applicationContext, Comm2VideoPrefs))
        }
//        if (enabledSources.contains("AMBIENT")) {
//            providers.add(AmazonMediaProvider(applicationContext, AmazonVideoPrefs))
//        }
        if (enabledSources.contains("LOCAL")) {
            providers.add(LocalMediaProvider(applicationContext, LocalMediaPrefs))
        }

        return providers
    }
}
