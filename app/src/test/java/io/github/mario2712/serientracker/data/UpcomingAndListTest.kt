package io.github.mario2712.serientracker.data

import io.github.mario2712.serientracker.data.local.EpisodeEntity
import io.github.mario2712.serientracker.data.local.SeasonEntity
import io.github.mario2712.serientracker.data.local.SeasonWithEpisodes
import io.github.mario2712.serientracker.data.local.SeriesEntity
import io.github.mario2712.serientracker.data.local.SeriesOverview
import io.github.mario2712.serientracker.data.local.SeriesWithSeasons
import io.github.mario2712.serientracker.data.remote.TmdbCastJson
import io.github.mario2712.serientracker.data.remote.TmdbClient
import io.github.mario2712.serientracker.data.remote.TmdbCreditsJson
import io.github.mario2712.serientracker.data.remote.TmdbRoleJson
import io.github.mario2712.serientracker.data.remote.TmdbVideoJson
import io.github.mario2712.serientracker.data.remote.TmdbVideosJson
import io.github.mario2712.serientracker.data.repository.SyncReport
import io.github.mario2712.serientracker.data.upcoming.Upcoming
import io.github.mario2712.serientracker.domain.WatchStatus
import io.github.mario2712.serientracker.ui.serieslist.SeriesListLogic
import io.github.mario2712.serientracker.ui.serieslist.SeriesSort
import io.github.mario2712.serientracker.work.AutoBackup
import io.github.mario2712.serientracker.work.Notifications
import io.github.mario2712.serientracker.work.WorkScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class UpcomingAndListTest {

    // Friday, 20.11.2026
    private val today = LocalDate.of(2026, 11, 20)
    private fun day(d: LocalDate) = d.toEpochDay()

    private fun series(id: Long, status: WatchStatus, next: LocalDate?, tmdb: Int? = id.toInt(), episodes: List<EpisodeEntity> = emptyList()) =
        SeriesWithSeasons(
            series = SeriesEntity(
                id = id, title = "S$id", status = status, tmdbId = tmdb, nextAirDate = next?.let(::day),
                nextEpisodeSeason = next?.let { 2 }, nextEpisodeNumber = next?.let { 5 }, nextEpisodeName = next?.let { "Finale" },
            ),
            seasons = listOf(SeasonWithEpisodes(SeasonEntity(id = id, seriesId = id, number = 1), episodes)),
            importProgress = null,
        )

    @Test
    fun `upcoming excludes dropped and planned, includes completed and caught up`() {
        val data = Upcoming.compute(
            listOf(
                series(1, WatchStatus.WATCHING, today),
                series(2, WatchStatus.CAUGHT_UP, today.plusDays(2)),
                series(3, WatchStatus.COMPLETED, today.plusDays(10)),
                series(4, WatchStatus.DROPPED, today.plusDays(1)),
                series(5, WatchStatus.PLAN_TO_WATCH, today.plusDays(1)),
                series(6, WatchStatus.PAUSED, today.plusDays(60)),
                series(7, WatchStatus.WATCHING, today.minusDays(3)), // past date: not upcoming
                series(8, WatchStatus.WATCHING, today.plusDays(1), tmdb = null), // not matched
            ),
            today,
        )
        val sections = data.upcoming.associate { (k, v) -> k to v.map { it.seriesId } }
        assertEquals(listOf("Heute", "Diese Woche", "Diesen Monat", "Später"), data.upcoming.map { it.first })
        assertEquals(listOf(1L), sections["Heute"])
        assertEquals(listOf(2L), sections["Diese Woche"]) // Sunday 22.11. is still this week
        assertEquals(listOf(3L), sections["Diesen Monat"]) // 30.11.
        assertEquals(listOf(6L), sections["Später"])
    }

    @Test
    fun `duplicates of a show are listed once in upcoming`() {
        val data = Upcoming.compute(
            listOf(series(1, WatchStatus.WATCHING, today, tmdb = 99), series(2, WatchStatus.CAUGHT_UP, today, tmdb = 99)),
            today,
        )
        val item = data.upcoming.single().second.single()
        assertEquals("S1 (2 Einträge)", item.title)
    }

    @Test
    fun `upcoming item shows code, name and days`() {
        val item = Upcoming.compute(listOf(series(1, WatchStatus.WATCHING, today.plusDays(1))), today).upcoming.single().second.single()
        assertEquals("S02E05", item.code)
        assertEquals("Finale", item.episodeName)
        assertEquals(1, item.daysAway)
    }

    @Test
    fun `newly aired lists unwatched episodes of the last 14 days`() {
        val eps = listOf(
            EpisodeEntity(seasonId = 1, number = 1, airDate = day(today.minusDays(30)), watchedAt = 1L),
            EpisodeEntity(seasonId = 1, number = 2, airDate = day(today.minusDays(7))),
            EpisodeEntity(seasonId = 1, number = 3, airDate = day(today.minusDays(1))),
        )
        val data = Upcoming.compute(
            listOf(series(1, WatchStatus.CAUGHT_UP, null, episodes = eps), series(2, WatchStatus.DROPPED, null, episodes = eps)),
            today,
        )
        val item = data.newlyAired.single()
        assertEquals(1L, item.seriesId)
        assertEquals(2, item.unwatched)
        assertEquals("S01E02", item.nextCode)
        assertEquals(today.minusDays(1), item.latestAirDate)
    }

    private fun overview(id: Long, title: String, watched: Int, total: Int, rating: Int? = null, avg: Double? = null, next: Long? = null, genre: String? = null) =
        SeriesOverview(
            series = SeriesEntity(id = id, title = title, rating = rating, nextAirDate = next, genre = genre),
            totalEpisodes = total, watchedEpisodes = watched, nextSeason = null, nextEpisode = null,
            excelSeason = null, excelEpisode = null, needsSync = false, avgEpisodeRating = avg,
        )

    @Test
    fun `sorting by title, progress, rating and next air date`() {
        val items = listOf(
            overview(1, "Beta", 5, 10, avg = 7.5, next = 100, genre = "Drama, Krimi"),
            overview(2, "alpha", 10, 10, rating = 9, genre = "Comedy"),
            overview(3, "Gamma", 1, 10, next = 50, genre = "Drama"),
        )
        assertEquals(listOf(2L, 1L, 3L), SeriesListLogic.sort(items, SeriesSort.TITLE).map { it.series.id })
        assertEquals(listOf(2L, 1L, 3L), SeriesListLogic.sort(items, SeriesSort.PROGRESS).map { it.series.id })
        assertEquals(listOf(2L, 1L, 3L), SeriesListLogic.sort(items, SeriesSort.RATING).map { it.series.id })
        assertEquals(listOf(3L, 1L, 2L), SeriesListLogic.sort(items, SeriesSort.NEXT_AIR).map { it.series.id })
        assertEquals(items, SeriesListLogic.sort(items, SeriesSort.RECENT))
        assertEquals(listOf("Comedy", "Drama", "Krimi"), SeriesListLogic.genres(items))
        assertEquals(listOf(1L, 3L), items.filter { SeriesListLogic.matchesGenre(it, "drama") }.map { it.series.id })
        assertEquals(SeriesSort.RECENT, SeriesSort.from("UNKNOWN"))
    }

    @Test
    fun `backup rotation keeps the newest five`() {
        val names = (1..7).map { "serientracker_backup_2026110$it.json" } + "andere.json"
        assertEquals(listOf("serientracker_backup_20261102.json", "serientracker_backup_20261101.json"), AutoBackup.filesToDelete(names))
        assertTrue(AutoBackup.filesToDelete(names.take(3)).isEmpty())
    }

    @Test
    fun `scheduler waits until the next 8 o'clock`() {
        assertEquals(Duration.ofHours(1), WorkScheduler.delayUntil(LocalTime.of(8, 0), LocalDateTime.of(2026, 11, 20, 7, 0)))
        assertEquals(Duration.ofHours(23), WorkScheduler.delayUntil(LocalTime.of(8, 0), LocalDateTime.of(2026, 11, 20, 9, 0)))
    }

    @Test
    fun `notification text summarizes new episodes`() {
        val (title, lines) = Notifications.content(
            listOf(
                SyncReport(1, "The Bear", null, null, 46, 48, listOf("S05E09 „Neu“")),
                SyncReport(2, "South Park", null, null, 338, 340, listOf("S28E06", "S28E07")),
            ),
        )
        assertEquals("3 neue Folgen", title)
        assertEquals(listOf("The Bear: S05E09 „Neu“", "South Park: 2 neue Folgen"), lines)
    }

    @Test
    fun `tmdb trailer and cast summary`() {
        val videos = TmdbVideosJson(
            listOf(
                TmdbVideoJson("tease", "YouTube", "Teaser", "de"),
                TmdbVideoJson("en", "YouTube", "Trailer", "en", official = true),
                TmdbVideoJson("de", "YouTube", "Trailer", "de"),
                TmdbVideoJson("vimeo", "Vimeo", "Trailer", "de"),
            ),
        )
        assertEquals("de", TmdbClient.trailerKey(videos))
        assertNull(TmdbClient.trailerKey(TmdbVideosJson(emptyList())))
        val credits = TmdbCreditsJson(
            listOf(
                TmdbCastJson("B", 1, listOf(TmdbRoleJson("Rolle B"))),
                TmdbCastJson("A", 0, listOf(TmdbRoleJson(""))),
            ),
        )
        assertEquals("A, B als Rolle B", TmdbClient.castSummary(credits))
    }
}
