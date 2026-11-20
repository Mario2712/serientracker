package io.github.mario2712.serientracker.data.repository

import io.github.mario2712.serientracker.data.remote.TmdbKeyMissingException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

data class ReviewItem(val seriesId: Long, val title: String, val candidateCount: Int)

data class SyncState(
    val running: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val matched: List<SyncReport> = emptyList(),
    val review: List<ReviewItem> = emptyList(),
    val failed: List<String> = emptyList(),
    /** Fatal problem that stopped the run (missing/invalid key, no internet). */
    val error: String? = null,
) {
    val differences: List<SyncReport> get() = matched.filter { it.hasDifference }
}

/**
 * Runs the bulk TMDB match in the app scope, so it keeps going while the user
 * moves between screens (e.g. to resolve a series by hand).
 */
class TmdbSyncCoordinator(
    private val repository: TmdbSyncRepository,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(SyncState())
    val state: StateFlow<SyncState> = _state.asStateFlow()
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            val todo = repository.unsynced()
            _state.value = SyncState(running = true, total = todo.size)
            for (series in todo) {
                if (!isActive) break
                try {
                    when (val outcome = repository.autoMatch(series)) {
                        is MatchOutcome.Matched -> _state.update { it.copy(matched = it.matched + outcome.report) }
                        is MatchOutcome.NeedsReview -> _state.update {
                            it.copy(review = it.review + ReviewItem(series.id, series.title, outcome.candidates.size))
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: TmdbKeyMissingException) {
                    stopWith("Bitte zuerst in den Einstellungen einen TMDB-API-Key eintragen.")
                    return@launch
                } catch (e: HttpException) {
                    if (e.code() == 401) {
                        stopWith("TMDB lehnt den API-Key ab. Bitte in den Einstellungen prüfen.")
                        return@launch
                    }
                    _state.update { it.copy(failed = it.failed + "${series.title}: HTTP ${e.code()}") }
                } catch (e: IOException) {
                    stopWith("Keine Verbindung zu TMDB. Bitte Internet prüfen und erneut starten.")
                    return@launch
                } catch (e: Exception) {
                    _state.update { it.copy(failed = it.failed + "${series.title}: ${e.message}") }
                }
                _state.update { it.copy(done = it.done + 1) }
            }
            _state.update { it.copy(running = false) }
        }
    }

    fun stop() {
        job?.cancel()
        _state.update { it.copy(running = false) }
    }

    /** Called when the user linked a series by hand. */
    fun resolved(seriesId: Long, report: SyncReport?) {
        _state.update { s ->
            s.copy(
                review = s.review.filterNot { it.seriesId == seriesId },
                matched = if (report != null) s.matched.filterNot { it.seriesId == seriesId } + report else s.matched,
            )
        }
    }

    private fun stopWith(message: String) {
        _state.update { it.copy(running = false, error = message) }
    }
}
