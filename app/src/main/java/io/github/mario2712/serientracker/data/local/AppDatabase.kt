package io.github.mario2712.serientracker.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SeriesEntity::class, SeasonEntity::class, EpisodeEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun seriesDao(): SeriesDao
    abstract fun episodeDao(): EpisodeDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "serientracker.db")
                // Add Migration objects here when the schema changes (see app/schemas/).
                .build()
    }
}
