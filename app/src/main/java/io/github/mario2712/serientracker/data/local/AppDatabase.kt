package io.github.mario2712.serientracker.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SeriesEntity::class, SeasonEntity::class, EpisodeEntity::class, ImportProgressEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun seriesDao(): SeriesDao
    abstract fun episodeDao(): EpisodeDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "serientracker.db")
                // Version 1 was only the empty skeleton; its data is replaced by the Excel seed.
                // From version 2 on, add real Migration objects (schemas in app/schemas/).
                .fallbackToDestructiveMigrationFrom(1)
                .build()

        fun inMemory(context: Context): AppDatabase =
            Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }
}
