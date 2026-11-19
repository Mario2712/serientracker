package io.github.mario2712.serientracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.mario2712.serientracker.ui.navigation.AppNavHost
import io.github.mario2712.serientracker.ui.theme.SerientrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SerientrackerTheme {
                AppNavHost()
            }
        }
    }
}
