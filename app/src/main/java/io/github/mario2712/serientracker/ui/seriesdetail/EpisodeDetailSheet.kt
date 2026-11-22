package io.github.mario2712.serientracker.ui.seriesdetail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.mario2712.serientracker.data.local.EpisodeEntity
import io.github.mario2712.serientracker.data.local.EpisodeViewEntity
import io.github.mario2712.serientracker.data.local.HOME_ZONE
import io.github.mario2712.serientracker.data.local.isWatched
import io.github.mario2712.serientracker.data.local.zoneOf
import io.github.mario2712.serientracker.domain.EpisodeKind
import io.github.mario2712.serientracker.domain.Progress
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val dateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy")
private val dateTimeFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm 'Uhr'")

/** Episode number tile: tap toggles watched, long press opens the details. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EpisodeChip(
    label: String,
    watched: Boolean,
    hidden: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    /** Number of viewings; shown as "2×" from two on. */
    viewings: Int = 1,
) {
    val haptic = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.small
    Surface(
        shape = shape,
        color = if (watched && !hidden) colors.secondaryContainer else Color.Transparent,
        contentColor = if (watched && !hidden) colors.onSecondaryContainer else colors.onSurface,
        border = if (watched && !hidden) null else BorderStroke(1.dp, colors.outline),
        modifier = Modifier
            .alpha(if (hidden) 0.4f else 1f)
            .padding(vertical = 3.dp)
            .sizeIn(minWidth = 48.dp, minHeight = 36.dp)
            .clip(shape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
                onLongClickLabel = "Details",
            ),
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (watched && !hidden) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                textDecoration = if (hidden) TextDecoration.LineThrough else null,
                maxLines = 1,
            )
            if (viewings > 1 && watched) {
                Spacer(Modifier.width(4.dp))
                Text("${viewings}×", style = MaterialTheme.typography.labelSmall, color = colors.primary)
            }
        }
    }
}

/** One special in the specials list: running number, title, air date; tap ticks, long press opens details. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SpecialRow(number: Int?, episode: EpisodeEntity, onClick: () -> Unit, onLongClick: () -> Unit, viewings: Int = 1) {
    val haptic = LocalHapticFeedback.current
    val hidden = episode.kind == EpisodeKind.HIDDEN
    val watched = episode.isWatched && !hidden
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
                onLongClickLabel = "Details",
            )
            .alpha(if (hidden) 0.45f else 1f)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            number?.let { "$it." } ?: "–",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.width(32.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                episode.title ?: "Special",
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                textDecoration = if (hidden) TextDecoration.LineThrough else null,
            )
            val info = listOfNotNull(
                episode.airDate?.let { LocalDate.ofEpochDay(it).format(dateFormat) },
                "eigenes Special".takeIf { episode.isCustom },
                episode.rating?.let { "★ $it" },
                "${viewings}× gesehen".takeIf { viewings > 1 && watched },
                "ausgeblendet – antippen zum Zählen".takeIf { hidden },
            )
            if (info.isNotEmpty()) {
                Text(
                    info.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(
            Icons.Filled.Check,
            contentDescription = if (watched) "gesehen" else "nicht gesehen",
            tint = if (watched) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.size(24.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EpisodeDetailSheet(
    seasonNumber: Int,
    episode: EpisodeEntity,
    /** Running number among the counting specials (season 0), shown as "Special n". */
    specialNumber: Int? = null,
    /** Further viewings (rewatches); the first viewing is in [episode]. */
    views: List<EpisodeViewEntity> = emptyList(),
    onDismiss: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onWatchAgain: () -> Unit,
    /** viewId null = first viewing. */
    onDeleteViewing: (Long?) -> Unit,
    /** viewId null = first viewing; time in millis, zone id of the viewing. */
    onSetViewingTime: (Long?, Long, String) -> Unit,
    onRate: (Int?) -> Unit,
    onKind: (EpisodeKind) -> Unit,
    onDelete: () -> Unit,
) {
    // The viewing whose date is being changed (or a new first viewing "with date").
    var editing by remember { mutableStateOf<Viewing?>(null) }
    val viewings = buildList {
        episode.watchedAt?.let { add(Viewing(null, it, episode.watchedZone ?: HOME_ZONE, episode.watchedAtImported)) }
        views.forEach { add(Viewing(it.id, it.watchedAt, it.zoneId, it.imported)) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val code = when {
                episode.isCustom -> listOfNotNull("Eigenes Special", specialNumber?.let { "Nr. $it" }).joinToString(" · ")
                seasonNumber == 0 -> specialNumber?.let { "Special $it" } ?: "Special (ausgeblendet)"
                else -> Progress.episodeCode(seasonNumber, episode.number)
            }
            Text(code, style = MaterialTheme.typography.labelLarge)
            Text(episode.title ?: "Kein Titel bekannt", style = MaterialTheme.typography.headlineSmall)
            Detail(
                "Erstausstrahlung",
                episode.airDate?.let { LocalDate.ofEpochDay(it).format(dateFormat) } ?: "unbekannt",
            )
            episode.runtime?.let { Detail("Laufzeit", "$it Minuten") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (viewings.size > 1) "Gesehen: ${viewings.size}×" else "Gesehen",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = episode.isWatched, onCheckedChange = onToggle)
            }
            if (viewings.size > 1) {
                Text(
                    "Haken entfernen löscht alle ${viewings.size} Sichtungen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (viewings.isEmpty()) {
                Text("noch nicht gesehen", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { editing = Viewing(null, null, ZoneId.systemDefault().id, false) }) {
                    Text("Mit Datum abhaken")
                }
            }
            viewings.forEachIndexed { index, v ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (viewings.size > 1) "${index + 1}. Sichtung" else "Sichtung",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(viewingText(v), style = MaterialTheme.typography.bodyMedium)
                    }
                    TextButton(onClick = { editing = v }) { Text("Datum") }
                    if (viewings.size > 1) {
                        TextButton(onClick = { onDeleteViewing(v.viewId) }) { Text("Löschen") }
                    }
                }
            }
            if (episode.isWatched) {
                OutlinedButton(onClick = onWatchAgain) { Text("Nochmal gesehen") }
            }

            // Same control as the series rating: slider 0..10, 0 = no rating. Saved when released.
            // Snapped values can be 7.9999995 instead of 8: round, never truncate.
            var rating by remember(episode.id, episode.rating) { mutableStateOf((episode.rating ?: 0).toFloat()) }
            Text(
                "Bewertung: " + (rating.roundToInt().takeIf { it > 0 }?.let { "$it/10" } ?: "keine"),
                style = MaterialTheme.typography.titleSmall,
            )
            Slider(
                value = rating,
                onValueChange = { rating = it },
                onValueChangeFinished = { onRate(rating.roundToInt().takeIf { it > 0 }) },
                valueRange = 0f..10f,
                steps = 9,
            )

            Text("Zählt als", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                EpisodeKind.entries.forEach { kind ->
                    FilterChip(
                        selected = episode.kind == kind,
                        onClick = { onKind(kind) },
                        label = { Text(kind.label) },
                    )
                }
            }
            Text(
                when (episode.kind) {
                    EpisodeKind.REGULAR -> "Normale Folge der Staffel."
                    EpisodeKind.SPECIAL -> "Zählt als Special (Excel-Spalte „Special“)."
                    EpisodeKind.HIDDEN -> "Zählt nirgends mit, weder bei den Folgen noch in der Excel."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (episode.isCustom) {
                OutlinedButton(onClick = onDelete) { Text("Eigenes Special löschen") }
            }
        }
    }

    editing?.let { v ->
        // Dates are shown and changed in the zone where the viewing was ticked.
        val zone = zoneOf(v.zone)
        val initial = (v.at ?: System.currentTimeMillis()).let {
            // DatePicker works with UTC midnight of the chosen day.
            Instant.ofEpochMilli(it).atZone(zone).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }
        val state = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { editing = null },
            confirmButton = {
                TextButton(onClick = {
                    editing = null
                    state.selectedDateMillis?.let { utcMidnight ->
                        val day = Instant.ofEpochMilli(utcMidnight).atZone(ZoneOffset.UTC).toLocalDate()
                        // Keep the original time of day if there was one, otherwise 20:00.
                        val time = v.at?.takeUnless { v.imported }
                            ?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime() }
                            ?: LocalTime.of(20, 0)
                        onSetViewingTime(v.viewId, day.atTime(time).atZone(zone).toInstant().toEpochMilli(), zone.id)
                    }
                }) { Text("Übernehmen") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Abbrechen") } },
        ) {
            DatePicker(state = state)
        }
    }
}

/** One viewing as shown in the sheet; [viewId] null = first viewing (stored in the episode). */
private data class Viewing(val viewId: Long?, val at: Long?, val zone: String, val imported: Boolean)

@Composable
private fun Detail(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Date and time in the zone of the viewing; the zone is named when it differs from the phone's. */
private fun viewingText(v: Viewing): String {
    val at = v.at ?: return "noch nicht gesehen"
    val zone = zoneOf(v.zone)
    val zoned = Instant.ofEpochMilli(at).atZone(zone)
    if (v.imported) return "früher gesehen (ohne genaues Datum, erfasst am ${zoned.toLocalDate().format(dateFormat)})"
    val zoneHint = if (zone.id != ZoneId.systemDefault().id) " (${zone.id})" else ""
    return "am ${zoned.format(dateTimeFormat)}$zoneHint"
}
