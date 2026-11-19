package io.github.mario2712.serientracker.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.mario2712.serientracker.ui.editseries.EditSeriesScreen
import io.github.mario2712.serientracker.ui.seriesdetail.SeriesDetailScreen
import io.github.mario2712.serientracker.ui.serieslist.SeriesListScreen

object Routes {
    const val ARG_SERIES_ID = "seriesId"
    const val LIST = "series"
    const val DETAIL = "series/{$ARG_SERIES_ID}"
    const val EDIT = "series/edit?$ARG_SERIES_ID={$ARG_SERIES_ID}"

    fun detail(id: Long) = "series/$id"
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
            )
        }
        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument(Routes.ARG_SERIES_ID) { type = NavType.LongType }),
        ) {
            SeriesDetailScreen(
                onBack = { nav.popBackStack() },
                onEdit = { nav.navigate(Routes.edit(it)) },
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
            )
        }
    }
}
