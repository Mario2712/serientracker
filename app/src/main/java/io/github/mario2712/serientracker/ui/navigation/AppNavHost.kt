package io.github.mario2712.serientracker.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.mario2712.serientracker.ui.about.AboutScreen
import io.github.mario2712.serientracker.ui.editseries.EditSeriesScreen
import io.github.mario2712.serientracker.ui.seriesdetail.SeriesDetailScreen
import io.github.mario2712.serientracker.ui.serieslist.SeriesListScreen
import io.github.mario2712.serientracker.ui.settings.SettingsScreen
import io.github.mario2712.serientracker.ui.tmdbsync.MatchSeriesScreen
import io.github.mario2712.serientracker.ui.tmdbsync.SyncScreen

object Routes {
    const val ARG_SERIES_ID = "seriesId"
    const val LIST = "series"
    const val DETAIL = "series/detail/{$ARG_SERIES_ID}"
    const val EDIT = "series/edit?$ARG_SERIES_ID={$ARG_SERIES_ID}"
    const val SYNC = "sync"
    const val MATCH = "sync/match/{$ARG_SERIES_ID}"
    const val SETTINGS = "settings"
    const val ABOUT = "about"

    fun detail(id: Long) = "series/detail/$id"
    fun match(id: Long) = "sync/match/$id"
    fun edit(id: Long? = null) = if (id == null) "series/edit" else "series/edit?$ARG_SERIES_ID=$id"
}

@Composable
fun AppNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.LIST) {
        composable(Routes.LIST) {
            SeriesListScreen(
                onSeriesClick = { nav.navigate(Routes.detail(it)) },
                onAddClick = { nav.navigate(Routes.edit()) },
                onSyncClick = { nav.navigate(Routes.SYNC) },
                onSettingsClick = { nav.navigate(Routes.SETTINGS) },
                onAboutClick = { nav.navigate(Routes.ABOUT) },
            )
        }
        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument(Routes.ARG_SERIES_ID) { type = NavType.LongType }),
        ) {
            SeriesDetailScreen(
                onBack = { nav.popBackStack() },
                onEdit = { nav.navigate(Routes.edit(it)) },
                onMatch = { nav.navigate(Routes.match(it)) },
            )
        }
        composable(Routes.SYNC) {
            SyncScreen(
                onBack = { nav.popBackStack() },
                onResolve = { nav.navigate(Routes.match(it)) },
                onOpenSeries = { nav.navigate(Routes.detail(it)) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(
            route = Routes.MATCH,
            arguments = listOf(navArgument(Routes.ARG_SERIES_ID) { type = NavType.LongType }),
        ) {
            MatchSeriesScreen(onDone = { nav.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.ABOUT) {
            AboutScreen(onBack = { nav.popBackStack() })
        }
        composable(
            route = Routes.EDIT,
            arguments = listOf(
                navArgument(Routes.ARG_SERIES_ID) {
                    type = NavType.LongType
                    defaultValue = 0L
                },
            ),
        ) {
            EditSeriesScreen(
                onDone = { savedId, isNew ->
                    if (isNew) {
                        nav.navigate(Routes.detail(savedId)) { popUpTo(Routes.LIST) }
                    } else {
                        nav.popBackStack()
                    }
                },
                onBack = { nav.popBackStack() },
            )
        }
    }
}
