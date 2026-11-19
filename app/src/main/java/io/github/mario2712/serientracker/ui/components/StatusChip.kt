package io.github.mario2712.serientracker.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.mario2712.serientracker.domain.WatchStatus

@Composable
fun StatusChip(status: WatchStatus, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val (bg, fg) = when (status) {
        WatchStatus.WATCHING -> colors.primaryContainer to colors.onPrimaryContainer
        WatchStatus.PLAN_TO_WATCH -> colors.secondaryContainer to colors.onSecondaryContainer
        WatchStatus.COMPLETED -> colors.tertiaryContainer to colors.onTertiaryContainer
        WatchStatus.PAUSED -> colors.surfaceVariant to colors.onSurfaceVariant
        WatchStatus.DROPPED -> colors.errorContainer to colors.onErrorContainer
    }
    Surface(color = bg, contentColor = fg, shape = MaterialTheme.shapes.small, modifier = modifier) {
        Text(
            text = status.label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

