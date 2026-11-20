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
import io.github.mario2712.serientracker.data.local.isWatched
import io.github.mario2712.serientracker.domain.EpisodeKind
import io.github.mario2712.serientracker.domain.Progress
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

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
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EpisodeDetailSheet(
    seasonNumber: Int,
    episode: EpisodeEntity,
    onDismiss: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onSetWatchedAt: (Long) -> Unit,
    onRate: (Int?) -> Unit,
    onKind: (EpisodeKind) -> Unit,
    onDelete: () -> Unit,
) {
    var pickDate by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val code = if (episode.isCustom) "Eigenes Special" else Progress.episodeCode(seasonNumber, episode.number)
            Text(code, style = MaterialTheme.typography.labelLarge)
            Text(episode.title ?: "Kein Titel bekannt", style = MaterialTheme.typography.headlineSmall)
            Detail(
                "Erstausstrahlung",
                episode.airDate?.let { LocalDate.ofEpochDay(it).format(dateFormat) } ?: "unbekannt",
            )
            episode.runtime?.let { Detail("Laufzeit", "$it Minuten") }
            Detail("Gesehen", watchedText(episode))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Gesehen", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = episode.isWatched, onCheckedChange = onToggle)
            }
            OutlinedButton(onClick = { pickDate = true }) {
                Text(if (episode.isWatched) "Abhak-Datum ändern" else "Mit Datum abhaken")
            }

            Text("Bewertung", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..10).forEach { value ->
                    FilterChip(
                        selected = episode.rating == value,
                        onClick = { onRate(if (episode.rating == value) null else value) },
                        label = { Text(value.toString()) },
                    )
                }
            }
            if (episode.rating != null) {
                TextButton(onClick = { onRate(null) }) { Text("Bewertung entfernen") }
            }

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

    if (pickDate) {
        val initial = (episode.watchedAt ?: System.currentTimeMillis()).let {
            // DatePicker works with UTC midnight of the chosen day.
            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }
        val state = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickDate = false
                    state.selectedDateMillis?.let { utcMidnight ->
                        val day = Instant.ofEpochMilli(utcMidnight).atZone(ZoneOffset.UTC).toLocalDate()
                        // Keep the original time of day if there was one, otherwise 20:00.
                        val time = episode.watchedAt
                            ?.takeUnless { episode.watchedAtImported }
                            ?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalTime() }
                            ?: LocalTime.of(20, 0)
                        onSetWatchedAt(day.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                    }
                }) { Text("Übernehmen") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Abbrechen") } },
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun watchedText(episode: EpisodeEntity): String {
    val at = episode.watchedAt ?: return "noch nicht gesehen"
    val zoned = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
    return if (episode.watchedAtImported) {
        "vor dem TMDB-Abgleich (lt. Excel ca. ${zoned.toLocalDate().format(dateFormat)})"
    } else {
        "am ${zoned.format(dateTimeFormat)}"
    }
}
