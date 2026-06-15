package com.openlumen.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.openlumen.R
import com.openlumen.ui.screens.AboutScreen
import com.openlumen.ui.screens.DriverScreen
import com.openlumen.ui.screens.HomeScreen
import com.openlumen.ui.screens.PresetsScreen
import com.openlumen.ui.screens.ScheduleScreen
import com.openlumen.ui.theme.OpenLumenTheme

private sealed class Dest(val route: String, val labelRes: Int, val iconRes: Int) {
    data object Home     : Dest("home",     R.string.nav_home,     R.drawable.ic_nav_home)
    data object Schedule : Dest("schedule", R.string.nav_schedule, R.drawable.ic_nav_schedule)
    data object Presets  : Dest("presets",  R.string.nav_presets,  R.drawable.ic_nav_presets)
    data object Driver   : Dest("driver",   R.string.nav_driver,   R.drawable.ic_nav_driver)
    data object About    : Dest("about",    R.string.nav_about,    R.drawable.ic_nav_about)

    companion object {
        val all = listOf(Home, Schedule, Presets, Driver, About)
    }
}

@Composable
fun OpenLumenRoot() {
    OpenLumenTheme {
        val nav = rememberNavController()
        val backStack by nav.currentBackStackEntryAsState()
        val currentRoute = backStack?.destination?.route

        NavigationSuiteScaffold(
            navigationSuiteItems = {
                Dest.all.forEach { dest ->
                    item(
                        selected = currentRoute == dest.route,
                        onClick = {
                            nav.navigate(dest.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                painter = painterResource(dest.iconRes),
                                contentDescription = null
                            )
                        },
                        label = { Text(stringResource(dest.labelRes)) }
                    )
                }
            }
        ) {
            NavHost(
                navController = nav,
                startDestination = Dest.Home.route,
                modifier = Modifier.fillMaxSize()
            ) {
                composable(Dest.Home.route)     { HomeScreen() }
                composable(Dest.Schedule.route) { ScheduleScreen() }
                composable(Dest.Presets.route)  { PresetsScreen() }
                composable(Dest.Driver.route)   { DriverScreen() }
                composable(Dest.About.route)    { AboutScreen() }
            }
        }
    }
}
