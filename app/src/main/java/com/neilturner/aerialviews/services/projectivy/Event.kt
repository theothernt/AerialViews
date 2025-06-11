package com.neilturner.aerialviews.services.projectivy

import android.os.Parcel
import android.os.Parcelable
import androidx.annotation.IntDef
import com.neilturner.aerialviews.services.projectivy.WallpaperUpdateEventType.Companion.CARD_FOCUSED
import com.neilturner.aerialviews.services.projectivy.WallpaperUpdateEventType.Companion.LAUNCHER_IDLE_MODE_CHANGED
import com.neilturner.aerialviews.services.projectivy.WallpaperUpdateEventType.Companion.NOW_PLAYING_CHANGED
import com.neilturner.aerialviews.services.projectivy.WallpaperUpdateEventType.Companion.PROGRAM_CARD_FOCUSED
import com.neilturner.aerialviews.services.projectivy.WallpaperUpdateEventType.Companion.TIME_ELAPSED
import kotlinx.parcelize.Parcelize

@Target(AnnotationTarget.TYPE)
@Retention(AnnotationRetention.SOURCE)
@IntDef(TIME_ELAPSED, NOW_PLAYING_CHANGED, CARD_FOCUSED, PROGRAM_CARD_FOCUSED, LAUNCHER_IDLE_MODE_CHANGED)
annotation class WallpaperUpdateEventType {
    companion object {
        const val TIME_ELAPSED = 1
        const val NOW_PLAYING_CHANGED = 2
        const val CARD_FOCUSED = 4
        const val PROGRAM_CARD_FOCUSED = 8
        const val LAUNCHER_IDLE_MODE_CHANGED = 16
    }
}

sealed class Event(
    open val eventType: Int,
) : Parcelable {
    companion object CREATOR : Parcelable.Creator<Event> {
        override fun createFromParcel(parcel: Parcel): Event? {
            val initialDataPosition = parcel.dataPosition() // save current data position
            val eventType = parcel.readInt() // get the Event type for dynamic creation
            parcel.setDataPosition(initialDataPosition) // reset position to let the specialized CREATOR read from start

            return when (eventType) {
                TIME_ELAPSED -> createEventFromParcel(parcel, TimeElapsed::class.java)
                NOW_PLAYING_CHANGED -> createEventFromParcel(parcel, NowPlayingChanged::class.java)
                CARD_FOCUSED -> createEventFromParcel(parcel, CardFocused::class.java)
                PROGRAM_CARD_FOCUSED -> createEventFromParcel(parcel, ProgramCardFocused::class.java)
                LAUNCHER_IDLE_MODE_CHANGED -> createEventFromParcel(parcel, LauncherIdleModeChanged::class.java)
                else -> null
            }
        }

        override fun newArray(size: Int): Array<Event?> = arrayOfNulls(size)

        private fun <T> createEventFromParcel(
            parcel: Parcel,
            clazz: Class<T>,
        ): T {
            val creatorField = clazz.getField("CREATOR")
            val creator = creatorField.get(null) as Parcelable.Creator<*>
            @Suppress("UNCHECKED_CAST")
            return creator.createFromParcel(parcel) as T
        }
    }

    @Parcelize
    data class TimeElapsed(
        override val eventType: Int = TIME_ELAPSED,
    ) : Event(eventType)

    @Parcelize
    data class NowPlayingChanged(
        override val eventType: Int = NOW_PLAYING_CHANGED,
        val isPlaying: Boolean = true,
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val iconUri: String? = null,
    ) : Event(eventType)

    @Parcelize
    data class CardFocused(
        override val eventType: Int = CARD_FOCUSED,
        val lightColor: Int = 0,
        val darkColor: Int = 0,
    ) : Event(eventType)

    // type refers to TYPE_* in TvContractCompat.PreviewProgramColumns
    @Parcelize
    data class ProgramCardFocused(
        override val eventType: Int = PROGRAM_CARD_FOCUSED,
        val title: String? = null,
        val type: Int? = null,
        val iconUri: String? = null,
        val iconAspectRatio: Int = 0,
    ) : Event(eventType)

    @Parcelize
    data class LauncherIdleModeChanged(
        override val eventType: Int = LAUNCHER_IDLE_MODE_CHANGED,
        val isIdle: Boolean = true,
    ) : Event(eventType)
}
