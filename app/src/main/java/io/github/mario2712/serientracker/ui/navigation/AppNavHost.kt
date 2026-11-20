package io.github.mario2712.serientracker.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import io.github.mario2712.serientracker.ui.stats.StatsScreen
import io.github.mario2712.serientracker.ui.tmdbsync.MatchSeriesScreen
import io.github.mario2712.serientracker.ui.tmdbsync.SyncScreen
import io.github.mario2712.serientracker.ui.upcoming.UpcomingScreen

object Routes {
    const val ARG_SERIES_ID = "seriesId"
    const val LIST = "series"
    const val DETAIL = "series/detail/{$ARG_SERIES_ID}"
    const val EDIT = "series/edit?$ARG_SERIES_ID={$ARG_SERIES_ID}"
    const val SYNC = "sync"
    const val MATCH = "sync/match/{$ARG_SERIES_ID}"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val STATS = "stats"
    const val UPCOMING = "upcoming"

    fun detail(id: Long) = "series/detail/$id"
    fun match(id: Long) = "sync/match/$id"
    fun edit(id: Long? = null) = if (id == null) "series/edit" else "series/edit?$ARG_SERIES_ID=$id"
}

/** [openSeriesId]: open this series right away (tap on a new-episode notification). */
@Composable
fun AppNavHost(openSeriesId: Long? = null) {
    val nav = rememberNavController()
    LaunchedEffect(openSeriesId) {
        if (openSeriesId != null && openSeriesId > 0) nav.navigate(Routes.detail(openSeriesId))
    }
    NavHost(navController = nav, startDestination = Routes.LIST) {
        composable(Routes.LIST) {
            SeriesListScreen(
                onSeriesClick = { nav.navigate(Routes.detail(it)) },
                onAddClick = { nav.navigate(Routes.edit()) },
                onSyncClick = { nav.navigate(Routes.SYNC) },
                onSettingsClick = { nav.navigate(Routes.SETTINGS) },
                onAboutClick = { nav.navigate(Routes.ABOUT) },
                onStatsClick = { nav.navigate(Routes.STATS) },
                onUpcomingClick = { nav.navigate(Routes.UPCOMING) },
            )
        }
        composable(Routes.UPCOMING) {
            UpcomingScreen(
                onBack = { nav.popBackStack() },
                onOpenSeries = { nav.navigate(Routes.detail(it)) },
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
                onOpenSeries = { nav.navigate(Routes.detail(it)) },
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
        composable(Routes.STATS) {
            StatsScreen(
                onBack = { nav.popBackStack() },
                onOpenSeries = { nav.navigate(Routes.detail(it)) },
            )
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
                onOpenExisting = { nav.navigate(Routes.detail(it)) { popUpTo(Routes.LIST) } },
            )
        }
    }
}
