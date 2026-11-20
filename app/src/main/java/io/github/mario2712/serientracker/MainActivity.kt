package io.github.mario2712.serientracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.mario2712.serientracker.ui.navigation.AppNavHost
import io.github.mario2712.serientracker.ui.theme.SerientrackerTheme
import io.github.mario2712.serientracker.work.Notifications

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Tap on a new-episode notification for one series opens that series (only on a fresh start,
        // not again after rotating the screen).
        val openSeriesId = if (savedInstanceState == null) {
            intent.getLongExtra(Notifications.EXTRA_SERIES_ID, 0L).takeIf { it > 0 }
        } else {
            null
        }
        setContent {
            SerientrackerTheme {
                AppNavHost(openSeriesId = openSeriesId)
            }
        }
    }
}
