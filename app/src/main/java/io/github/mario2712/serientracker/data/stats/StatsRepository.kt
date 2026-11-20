package io.github.mario2712.serientracker.data.stats

import io.github.mario2712.serientracker.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

class StatsRepository(private val db: AppDatabase) {

    /** Loads everything (~20,000 episodes) and computes the statistics off the main thread. */
    suspend fun load(today: LocalDate = LocalDate.now()): Stats {
        val all = db.seriesDao().getAllWithSeasons()
        return withContext(Dispatchers.Default) { StatsCalculator.compute(all, today) }
    }
}
