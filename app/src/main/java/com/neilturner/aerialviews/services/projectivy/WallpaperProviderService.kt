package com.neilturner.aerialviews.services.projectivy

import android.app.Service
import android.content.Intent
import android.os.IBinder
import tv.projectivy.plugin.wallpaperprovider.api.Event
import tv.projectivy.plugin.wallpaperprovider.api.IWallpaperProviderService
import tv.projectivy.plugin.wallpaperprovider.api.Wallpaper

class WallpaperProviderService : Service() {
    override fun onCreate() {
        super.onCreate()
        // PreferencesManager.init(this)
    }

    override fun onBind(intent: Intent): IBinder {
        // Return the interface.
        return binder
    }

    private val binder =
        object : IWallpaperProviderService.Stub() {
            override fun getWallpapers(event: Event?): List<Wallpaper> {
                val testUrl = "https://github.com/glouel/AerialCommunity/releases/download/mw2-1080p-sdr/video_inspire_florida_miami_brickell_sunset_00036.1080-sdr.mov"
                return when (event) {
                    is Event.TimeElapsed -> {
                        // This is where you generate the wallpaper list that will be cycled every x minute
                        return listOf(
                            // IMAGE can be served from app drawable/, local storage or internet
                            // Wallpaper(PreferencesManager.imageUrl, WallpaperType.IMAGE, author = "Pixabay"),
                            // VIDEO can be served from app raw/, local storage or internet (some formats might not be supported, though)
                            Wallpaper(testUrl, WallpaperType.VIDEO, WallpaperDisplayMode.DEFAULT, title = "Miami Sunset"),
                        )
                    }
                    else -> emptyList() // Returning an empty list won't change the currently displayed wallpaper
                }
            }
        }
}
