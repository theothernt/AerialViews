package com.neilturner.aerialviews.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [CachedMediaEntity::class, CachedMusicTrackEntity::class, PlaylistStateEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AerialDatabase : RoomDatabase() {
    abstract fun playlistCacheDao(): PlaylistCacheDao

    companion object {
        private const val DATABASE_NAME = "aerial_database"

        @Volatile
        private var db: AerialDatabase? = null

        fun getInstance(context: Context): AerialDatabase {
            val existing = db
            if (existing != null) return existing

            return synchronized(this) {
                val rechecked = db
                if (rechecked != null) {
                    rechecked
                } else {
                    val created =
                        Room
                            .databaseBuilder(
                                context.applicationContext,
                                AerialDatabase::class.java,
                                DATABASE_NAME,
                            ).fallbackToDestructiveMigration(dropAllTables = true)
                            .build()
                    db = created
                    created
                }
            }
        }

        fun closeAndReset() {
            synchronized(this) {
                db?.close()
                db = null
            }
        }

        fun dbName(): String = DATABASE_NAME
    }
}
