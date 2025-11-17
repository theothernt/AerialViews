package com.neilturner.aerialviews.services.projectivy

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.neilturner.aerialviews.models.prefs.ProjectivyAmazonPrefs
import com.neilturner.aerialviews.models.prefs.ProjectivyApplePrefs
import com.neilturner.aerialviews.models.prefs.ProjectivyComm1Prefs
import com.neilturner.aerialviews.models.prefs.ProjectivyComm2Prefs
import com.neilturner.aerialviews.models.prefs.ProjectivyPrefs
import com.neilturner.aerialviews.providers.AmazonMediaProvider
import com.neilturner.aerialviews.providers.AppleMediaProvider
import com.neilturner.aerialviews.providers.Comm1MediaProvider
import com.neilturner.aerialviews.providers.Comm2MediaProvider
import com.neilturner.aerialviews.providers.MediaProvider
import kotlinx.coroutines.runBlocking
import tv.projectivy.plugin.wallpaperprovider.api.Event
import tv.projectivy.plugin.wallpaperprovider.api.IWallpaperProviderService
import tv.projectivy.plugin.wallpaperprovider.api.Wallpaper
import tv.projectivy.plugin.wallpaperprovider.api.WallpaperDisplayMode
import tv.projectivy.plugin.wallpaperprovider.api.WallpaperType

class WallpaperProviderService : Service() {
    override fun onBind(intent: Intent): IBinder = binder

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
                                    .filter { it.enabled }
                                    .flatMap { provider ->
                                        try {
                                            provider.fetchMedia()
                                        } catch (ex: Exception) {
                                            emptyList()
                                        }
                                    }
                            }.let { mediaList ->
                                if (ProjectivyPrefs.shuffleVideos) {
                                    mediaList.shuffled()
                                } else {
                                    mediaList
                                }
                            }

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

                    else -> {
                        emptyList()
                    } // Returning an empty list won't change the currently displayed wallpaper
                }

            override fun getPreferences(): String? = null

            override fun setPreferences(params: String?) {
            }
        }

    private fun getEnabledProviders(): List<MediaProvider> =
        mutableListOf<MediaProvider>().apply {
            add(AppleMediaProvider(applicationContext, ProjectivyApplePrefs))
            add(Comm1MediaProvider(applicationContext, ProjectivyComm1Prefs))
            add(Comm2MediaProvider(applicationContext, ProjectivyComm2Prefs))
            add(AmazonMediaProvider(applicationContext, ProjectivyAmazonPrefs))
        }
}
