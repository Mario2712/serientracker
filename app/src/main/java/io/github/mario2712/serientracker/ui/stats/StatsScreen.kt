package io.github.mario2712.serientracker.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.saveable.rememberSaveable
import io.github.mario2712.serientracker.data.stats.Bar
import io.github.mario2712.serientracker.data.stats.DayRecord
import io.github.mario2712.serientracker.data.stats.DelayStats
import io.github.mario2712.serientracker.data.stats.Heatmap
import io.github.mario2712.serientracker.data.stats.RatingComparison
import io.github.mario2712.serientracker.data.stats.YearReview
import io.github.mario2712.serientracker.data.settings.SettingsRepository
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import io.github.mario2712.serientracker.data.stats.RatedItem
import io.github.mario2712.serientracker.data.stats.Stats
import io.github.mario2712.serientracker.data.stats.StatsRepository
import io.github.mario2712.serientracker.ui.AppViewModelProvider
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException

class StatsViewModel(
    private val repository: StatsRepository,
    settings: SettingsRepository,
) : ViewModel() {
    val showExcel: StateFlow<Boolean> = settings.showExcelComparisons
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    var stats by mutableStateOf<Stats?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun load() {
        if (loading) return
        viewModelScope.launch {
            loading = true
            error = null
            try {
                stats = repository.load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = "Statistik konnte nicht berechnet werden: ${e.message}"
            } finally {
                loading = false
            }
        }
    }
}

private val dayFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy")

private fun Double.fmt(decimals: Int = 0): String = "%,.${decimals}f".format(Locale.GERMANY, this)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    onOpenSeries: (Long) -> Unit,
    viewModel: StatsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    // Recompute whenever the screen is opened (data may have changed in the meantime).
    LaunchedEffect(Unit) { viewModel.load() }
    val showExcel by viewModel.showExcel.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Statistik") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::load, enabled = !viewModel.loading) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Neu berechnen")
                    }
                },
            )
        },
    ) { padding ->
        val stats = viewModel.stats
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                stats != null -> StatsContent(stats, showExcel, onOpenSeries)
                viewModel.error != null -> Text(viewModel.error!!, Modifier.padding(16.dp))
                else -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
private fun StatsContent(s: Stats, showExcel: Boolean, onOpenSeries: (Long) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Section("Überblick") {
                KeyValue("Serien", s.seriesCount.toString())
                KeyValue("Gesehene Folgen", s.watchedEpisodes.toDouble().fmt())
                KeyValue("Sehzeit", "${s.totalHours.fmt()} Stunden (≈ ${(s.totalHours / 24).fmt()} Tage)")
                KeyValue("Bewertete Folgen", s.ratedEpisodes.toString())
                s.avgEpisodeRating?.let { KeyValue("Ø Folgenbewertung", "${it.fmt(1)} / 10") }
                Hint("Laufzeit bekannt für ${s.runtimeCoverage} % der gesehenen Folgen; fehlende zählen mit dem Median der Serie.")
            }
        }
        item {
            Section("Meistgeschaute Genres") {
                Bars(s.genresByEpisodes.take(10), unit = "Folgen")
                if (s.genresByHours.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("nach Stunden", style = MaterialTheme.typography.labelLarge)
                    Bars(s.genresByHours.take(10), unit = "h")
                }
            }
        }
        item { RatedSection("Bestbewertete Folgen", s.topEpisodes, "Noch keine Folgen bewertet: lange auf eine Folge drücken.", onOpenSeries) }
        item { RatedSection("Bestbewertete Staffeln", s.topSeasons, "Ab 2 bewerteten Folgen pro Staffel.", onOpenSeries) }
        item { RatedSection("Bestbewertete Serien", s.topSeries, "Eigene Serienbewertung, sonst Ø aus mindestens 2 bewerteten Folgen.", onOpenSeries) }
        item {
            Section("Sehzeit") {
                Text("pro Jahr", style = MaterialTheme.typography.labelLarge)
                Bars(s.hoursPerYear, unit = "h")
                Spacer(Modifier.height(8.dp))
                Text("Serien mit der meisten Sehzeit", style = MaterialTheme.typography.labelLarge)
                Bars(s.topSeriesByHours, unit = "h", onClick = onOpenSeries)
            }
        }
        item {
            Section("Verlauf (letzte 12 Monate)") {
                Bars(s.perMonth, unit = "Folgen")
                s.mostActiveDay?.let { KeyValue("Aktivster Tag", dayText(it)) }
                s.longestBinge?.let { KeyValue("Längster Binge", "${it.title}: ${dayText(it)}") }
                Hint("Nur Folgen mit echtem Abhak-Zeitpunkt, nicht die aus der Excel übernommenen.")
            }
        }
        item {
            Section("Wer schaut?") {
                Text("Serien", style = MaterialTheme.typography.labelLarge)
                Bars(s.watcherSeries, unit = "")
                Spacer(Modifier.height(8.dp))
                Text("Gesehene Folgen", style = MaterialTheme.typography.labelLarge)
                Bars(s.watcherEpisodes, unit = "")
            }
        }
        item { YearReviewSection(s.extras.years, onOpenSeries) }
        item { RatingComparisonSection(s.extras.ratingComparison, onOpenSeries) }
        item { DelaySection(s.extras.delay, onOpenSeries) }
        item { HeatmapSection(s.extras.heatmap) }
        item { Section("Status") { Bars(s.statusCounts, unit = "") } }
        item {
            Section("Stapel der Schande") {
                Bars(s.pileOfShame, unit = "offen", onClick = onOpenSeries)
            }
        }
        if (showExcel) item {
            Section("Abweichungen zur Excel (${s.deviations.size})") {
                if (s.deviations.isEmpty()) {
                    Hint("Keine: alle abgeglichenen Serien passen zur Excel.")
                } else {
                    Hint("Gesehen/Folgen laut Excel und laut App. Lange auf eine Folge drücken, um sie als Special zu zählen oder auszublenden.")
                    s.deviations.forEach { d ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onOpenSeries(d.seriesId) }.padding(vertical = 6.dp),
                        ) {
                            Text(d.title, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                "Excel ${d.excelWatched ?: "–"}/${d.excelTotal ?: "–"} · App ${d.watched}/${d.total}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun dayText(d: DayRecord) = "${d.date.format(dayFormat)} · ${d.episodes} Folgen"

@Composable
private fun YearReviewSection(years: List<YearReview>, onOpenSeries: (Long) -> Unit) {
    var index by rememberSaveable { mutableStateOf(0) }
    Section("Jahresrückblick") {
        if (years.isEmpty()) {
            Hint("Sobald du Folgen in der App abhakst, entsteht hier dein Jahresrückblick.")
            return@Section
        }
        val review = years[index.coerceIn(0, years.lastIndex)]
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { index++ }, enabled = index < years.lastIndex) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Vorjahr")
            }
            Text(review.year.toString(), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = { index-- }, enabled = index > 0) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Folgejahr")
            }
        }
        KeyValue("Gesehene Folgen", review.episodes.toString())
        KeyValue("Sehzeit", "${review.hours.fmt()} Stunden")
        review.topGenre?.let { KeyValue("Top-Genre", it) }
        review.binge?.let { KeyValue("Binge des Jahres", "${it.title}: ${dayText(it)}") }
        review.bestEpisode?.let { KeyValue("Beste Folge", "${it.title} (★ ${it.rating.fmt()})") }
        Text("Meistgeschaute Serien", style = MaterialTheme.typography.labelLarge)
        Bars(review.topSeries, unit = "Folgen", onClick = onOpenSeries)
    }
}

@Composable
private fun RatingComparisonSection(c: RatingComparison, onOpenSeries: (Long) -> Unit) {
    Section("Ich vs. TMDB") {
        val diff = c.avgDifference
        if (diff == null) {
            Hint("Bewerte Serien oder mindestens 2 Folgen einer abgeglichenen Serie, dann erscheint der Vergleich.")
            return@Section
        }
        val verdict = when {
            diff < -0.2 -> "Du bewertest im Schnitt ${(-diff).fmt(1)} Punkte strenger als TMDB."
            diff > 0.2 -> "Du bewertest im Schnitt ${diff.fmt(1)} Punkte großzügiger als TMDB."
            else -> "Du liegst im Schnitt gleichauf mit TMDB."
        }
        Text(verdict, style = MaterialTheme.typography.bodyMedium)
        Hint("Verglichen: ${c.compared} Serien (eigene Bewertung, sonst Ø der Folgen).")
        if (c.stricter.isNotEmpty()) {
            Text("Strenger als TMDB", style = MaterialTheme.typography.labelLarge)
            c.stricter.forEach { DiffRow(it, onOpenSeries) }
        }
        if (c.moreGenerous.isNotEmpty()) {
            Text("Großzügiger als TMDB", style = MaterialTheme.typography.labelLarge)
            c.moreGenerous.forEach { DiffRow(it, onOpenSeries) }
        }
    }
}

@Composable
private fun DiffRow(item: RatedItem, onOpenSeries: (Long) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onOpenSeries(item.seriesId) }.padding(vertical = 2.dp)) {
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Hint(item.subtitle)
        }
        Text((if (item.rating > 0) "+" else "") + item.rating.fmt(1), style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun DelaySection(d: DelayStats, onOpenSeries: (Long) -> Unit) {
    Section("Wie aktuell schaust du?") {
        val avg = d.avgDays
        if (avg == null) {
            Hint("Noch keine Folgen mit Ausstrahlungsdatum und echtem Abhak-Zeitpunkt.")
            return@Section
        }
        KeyValue("Ø Tage nach Ausstrahlung", avg.fmt())
        d.medianDays?.let { KeyValue("Median", "$it Tage") }
        KeyValue("Am Ausstrahlungstag geschaut", "${d.sameDay} von ${d.counted}")
        if (d.fastest.isNotEmpty()) {
            Text("Am schnellsten (Ø Tage)", style = MaterialTheme.typography.labelLarge)
            Bars(d.fastest, unit = "Tage", onClick = onOpenSeries)
            Text("Am spätesten (Ø Tage)", style = MaterialTheme.typography.labelLarge)
            Bars(d.slowest, unit = "Tage", onClick = onOpenSeries)
        }
    }
}

@Composable
private fun HeatmapSection(h: Heatmap) {
    Section("Wann schaust du?") {
        if (h.max == 0) {
            Hint("Noch keine Folgen mit echtem Abhak-Zeitpunkt.")
            return@Section
        }
        h.bestWeekday?.let { KeyValue("Beliebtester Tag", it) }
        h.bestBlock?.let { KeyValue("Beliebteste Zeit", it) }
        Row {
            Spacer(Modifier.width(32.dp))
            Heatmap.BLOCKS.forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f), maxLines = 1)
            }
        }
        h.cells.forEachIndexed { day, row ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Heatmap.WEEKDAYS[day], style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(32.dp))
                row.forEach { count ->
                    val alpha = if (count == 0) 0.06f else 0.2f + 0.8f * count / h.max
                    Box(
                        Modifier.weight(1f).height(22.dp).padding(1.dp).clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha)),
                    )
                }
            }
        }
        Hint("Nur echte Abhak-Zeitpunkte. Dunkler = mehr Folgen.")
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun KeyValue(key: String, value: String) {
    Row {
        Text(key, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Bars(bars: List<Bar>, unit: String, onClick: ((Long) -> Unit)? = null) {
    if (bars.isEmpty()) {
        Hint("Noch keine Daten.")
        return
    }
    val max = bars.maxOf { it.value }.takeIf { it > 0 } ?: 1.0
    bars.forEach { bar ->
        val clickable = onClick != null && bar.seriesId != null
        Column(
            Modifier.fillMaxWidth()
                .then(if (clickable) Modifier.clickable { onClick!!(bar.seriesId!!) } else Modifier)
                .padding(vertical = 2.dp),
        ) {
            Row {
                Text(bar.label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                val value = if (unit == "h") bar.value.fmt(1) else bar.value.fmt()
                Text(listOf(value, unit).filter { it.isNotEmpty() }.joinToString(" "), style = MaterialTheme.typography.bodySmall)
            }
            Box(
                Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Box(
                    Modifier.fillMaxHeight().fillMaxWidth((bar.value / max).toFloat().coerceIn(0f, 1f))
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
            if (bar.detail.isNotEmpty()) Hint(bar.detail)
        }
    }
}

@Composable
private fun RatedSection(title: String, items: List<RatedItem>, emptyHint: String, onOpenSeries: (Long) -> Unit) {
    Section(title) {
        if (items.isEmpty()) {
            Hint(emptyHint)
        } else {
            items.forEachIndexed { i, item ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpenSeries(item.seriesId) }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${i + 1}.", Modifier.width(28.dp), style = MaterialTheme.typography.bodyMedium)
                    Column(Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (item.subtitle.isNotEmpty()) Hint(item.subtitle)
                    }
                    Text("★ ${item.rating.fmt(1)}", style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}
