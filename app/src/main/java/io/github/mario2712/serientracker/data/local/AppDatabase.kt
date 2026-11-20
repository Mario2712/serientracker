package io.github.mario2712.serientracker.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SeriesEntity::class, SeasonEntity::class, EpisodeEntity::class, ImportProgressEntity::class,
        EpisodeViewEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun seriesDao(): SeriesDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun episodeViewDao(): EpisodeViewDao

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

        /** v4: original title from TMDB. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE series ADD COLUMN originalTitle TEXT")
            }
        }

        /** v5: episode rating, kind (regular/special/hidden), custom specials, runtime; Excel counts on series. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE episodes ADD COLUMN rating INTEGER")
                db.execSQL("ALTER TABLE episodes ADD COLUMN kind TEXT NOT NULL DEFAULT 'REGULAR'")
                db.execSQL("ALTER TABLE episodes ADD COLUMN isCustom INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE episodes ADD COLUMN runtime INTEGER")
                db.execSQL("UPDATE episodes SET kind = 'SPECIAL' WHERE seasonId IN (SELECT id FROM seasons WHERE number = 0)")
                db.execSQL("ALTER TABLE series ADD COLUMN tmdbMetaVersion INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE series ADD COLUMN excelWatched INTEGER")
                db.execSQL("ALTER TABLE series ADD COLUMN excelTotal INTEGER")
                // Series not matched yet still have their Excel numbers in import_progress.
                db.execSQL(
                    """
                    UPDATE series SET
                        excelWatched = (SELECT watched FROM import_progress WHERE seriesId = series.id),
                        excelTotal = (SELECT total FROM import_progress WHERE seriesId = series.id)
                    WHERE id IN (SELECT seriesId FROM import_progress)
                    """.trimIndent(),
                )
            }
        }

        /** v6: next announced episode and TMDB info (overview, score, trailer, cast). */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf(
                    "nextEpisodeSeason INTEGER", "nextEpisodeNumber INTEGER", "nextEpisodeName TEXT",
                    "overview TEXT", "tmdbRating REAL", "tmdbVoteCount INTEGER", "trailerKey TEXT", "castSummary TEXT",
                ).forEach { db.execSQL("ALTER TABLE series ADD COLUMN $it") }
            }
        }

        /** v7: time zone per viewing, further viewings, extra name for duplicates. */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE episodes ADD COLUMN watchedZone TEXT")
                db.execSQL("UPDATE episodes SET watchedZone = '$HOME_ZONE' WHERE watchedAt IS NOT NULL")
                db.execSQL("ALTER TABLE series ADD COLUMN label TEXT")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `episode_views` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `episodeId` INTEGER NOT NULL,
                        `watchedAt` INTEGER NOT NULL,
                        `imported` INTEGER NOT NULL DEFAULT 0,
                        `zoneId` TEXT NOT NULL,
                        FOREIGN KEY(`episodeId`) REFERENCES `episodes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_episode_views_episodeId` ON `episode_views` (`episodeId`)")
            }
        }

        fun build(context: Context, name: String = NAME): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                // Version 1 was only the empty skeleton; its data is replaced by the Excel seed.
                .fallbackToDestructiveMigrationFrom(1)
                .build()

        fun inMemory(context: Context): AppDatabase =
            Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }
}
