package io.github.mario2712.serientracker.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [SeriesEntity::class, SeasonEntity::class, EpisodeEntity::class, ImportProgressEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun seriesDao(): SeriesDao
    abstract fun episodeDao(): EpisodeDao

    companion object {
        const val NAME = "serientracker.db"

        /** v3: imported-watch flag on episodes, Excel "ungesehen" on import_progress. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE episodes ADD COLUMN watchedAtImported INTEGER NOT NULL DEFAULT 0")
                // Episodes ticked from the Excel got the "aktualisiert" date at local midnight;
                // real taps practically never land on a whole minute.
                db.execSQL(
                    "UPDATE episodes SET watchedAtImported = 1 WHERE watchedAt IS NOT NULL AND watchedAt % 60000 = 0",
                )
                db.execSQL("ALTER TABLE import_progress ADD COLUMN unseen INTEGER")
            }
        }

        fun build(context: Context, name: String = NAME): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(MIGRATION_2_3)
                // Version 1 was only the empty skeleton; its data is replaced by the Excel seed.
                .fallbackToDestructiveMigrationFrom(1)
                .build()

        fun inMemory(context: Context): AppDatabase =
            Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }
}
