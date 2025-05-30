package com.neilturner.aerialviews.providers.projectivy

import android.os.Parcelable
import androidx.annotation.IntDef
import com.neilturner.aerialviews.providers.projectivy.WallpaperDisplayMode.Companion.BLUR
import com.neilturner.aerialviews.providers.projectivy.WallpaperDisplayMode.Companion.CROP
import com.neilturner.aerialviews.providers.projectivy.WallpaperDisplayMode.Companion.DEFAULT
import com.neilturner.aerialviews.providers.projectivy.WallpaperDisplayMode.Companion.STRETCH
import com.neilturner.aerialviews.providers.projectivy.WallpaperType.Companion.ANIMATED_DRAWABLE
import com.neilturner.aerialviews.providers.projectivy.WallpaperType.Companion.DRAWABLE
import com.neilturner.aerialviews.providers.projectivy.WallpaperType.Companion.IMAGE
import com.neilturner.aerialviews.providers.projectivy.WallpaperType.Companion.LOTTIE
import com.neilturner.aerialviews.providers.projectivy.WallpaperType.Companion.VIDEO
import kotlinx.parcelize.Parcelize

@Target(AnnotationTarget.TYPE)
@Retention(AnnotationRetention.SOURCE)
@IntDef(IMAGE, DRAWABLE, ANIMATED_DRAWABLE, LOTTIE, VIDEO)
annotation class WallpaperType {
    companion object {
        const val IMAGE = 0
        const val DRAWABLE = 1
        const val ANIMATED_DRAWABLE = 2
        const val LOTTIE = 3
        const val VIDEO = 4
    }
}

@Target(AnnotationTarget.TYPE, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.SOURCE)
@IntDef(DEFAULT, CROP, STRETCH, BLUR)
annotation class WallpaperDisplayMode {
    companion object {
        const val DEFAULT = 0
        const val CROP = 1
        const val STRETCH = 2
        const val BLUR = 3
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
    val actionUri: String? = null,
) : Parcelable
