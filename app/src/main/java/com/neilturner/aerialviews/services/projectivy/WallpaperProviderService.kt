package com.neilturner.aerialviews.services.projectivy

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.neilturner.aerialviews.models.prefs.Comm1VideoPrefs
import com.neilturner.aerialviews.providers.Comm1MediaProvider
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
                        // Fetch all videos from Comm1MediaProvider
                        val comm1Provider = Comm1MediaProvider(applicationContext, Comm1VideoPrefs)

                        // Use runBlocking since the provider's fetchMedia is a suspend function
                        val aerialMediaList = runBlocking { comm1Provider.fetchMedia() }.shuffled()

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
}
