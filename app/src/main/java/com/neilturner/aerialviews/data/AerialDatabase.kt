package com.neilturner.aerialviews.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [CachedMediaEntity::class, CachedMusicTrackEntity::class, PlaylistStateEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AerialDatabase : RoomDatabase() {
    abstract fun playlistCacheDao(): PlaylistCacheDao

    companion object {
        @Volatile
        private var INSTANCE: AerialDatabase? = null

        fun getInstance(context: Context): AerialDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AerialDatabase::class.java,
                    "aerial_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
