package tv.projectivy.plugin.wallpaperprovider.api

import android.os.Parcelable
import androidx.annotation.IntDef
import kotlinx.parcelize.Parcelize
import tv.projectivy.plugin.wallpaperprovider.api.WallpaperDisplayMode.Companion.BLUR
import tv.projectivy.plugin.wallpaperprovider.api.WallpaperDisplayMode.Companion.CROP
import tv.projectivy.plugin.wallpaperprovider.api.WallpaperDisplayMode.Companion.DEFAULT
import tv.projectivy.plugin.wallpaperprovider.api.WallpaperDisplayMode.Companion.FIT_CENTER
import tv.projectivy.plugin.wallpaperprovider.api.WallpaperDisplayMode.Companion.FIT_CENTER_BLURRED_BG
import tv.projectivy.plugin.wallpaperprovider.api.WallpaperDisplayMode.Companion.STRETCH
import tv.projectivy.plugin.wallpaperprovider.api.WallpaperType.Companion.ANIMATED_DRAWABLE
import tv.projectivy.plugin.wallpaperprovider.api.WallpaperType.Companion.COLOR
import tv.projectivy.plugin.wallpaperprovider.api.WallpaperType.Companion.DRAWABLE
import tv.projectivy.plugin.wallpaperprovider.api.WallpaperType.Companion.IMAGE
import tv.projectivy.plugin.wallpaperprovider.api.WallpaperType.Companion.LOTTIE
import tv.projectivy.plugin.wallpaperprovider.api.WallpaperType.Companion.VIDEO


@Target(AnnotationTarget.TYPE)
@Retention(AnnotationRetention.SOURCE)
@IntDef(IMAGE, DRAWABLE, ANIMATED_DRAWABLE, LOTTIE, VIDEO, COLOR)
annotation class WallpaperType {
    companion object {
        const val IMAGE = 0
        const val DRAWABLE = 1
        const val ANIMATED_DRAWABLE = 2
        const val LOTTIE = 3
        const val VIDEO = 4
        const val COLOR = 5
    }
}

@Target(AnnotationTarget.TYPE, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.SOURCE)
@IntDef(DEFAULT, CROP, STRETCH, BLUR, FIT_CENTER, FIT_CENTER_BLURRED_BG)
annotation class WallpaperDisplayMode {
    companion object {
        const val DEFAULT = 0
        const val CROP = 1
        const val STRETCH = 2
        const val BLUR = 3
        const val FIT_CENTER = 4
        const val FIT_CENTER_BLURRED_BG = 5
    }
}

@Parcelize
data class Wallpaper(
    val uri: String,
    val type: @WallpaperType Int = IMAGE,
    val displayMode: @WallpaperDisplayMode Int = CROP,
    val title: String? = null,
    val source: String? = null,
    val author: String? = null,
    val actionUri: String? = null
) : Parcelable
