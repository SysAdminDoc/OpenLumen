package com.openlumen.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.SettingsInputComponent
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
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

private sealed class Dest(val route: String, val labelRes: Int, val icon: ImageVector) {
    data object Home     : Dest("home",     R.string.nav_home,     Icons.Outlined.WbSunny)
    data object Schedule : Dest("schedule", R.string.nav_schedule, Icons.Outlined.Schedule)
    data object Presets  : Dest("presets",  R.string.nav_presets,  Icons.Outlined.Tune)
    data object Driver   : Dest("driver",   R.string.nav_driver,   Icons.Outlined.SettingsInputComponent)
    data object About    : Dest("about",    R.string.nav_about,    Icons.Outlined.Info)

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

        Scaffold(
            bottomBar = {
                NavigationBar {
                    Dest.all.forEach { dest ->
                        NavigationBarItem(
                            selected = currentRoute == dest.route,
                            onClick = {
                                nav.navigate(dest.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(dest.icon, contentDescription = null) },
                            label = { Text(stringResource(dest.labelRes)) }
                        )
                    }
                }
            }
        ) { inner ->
            NavHost(
                navController = nav,
                startDestination = Dest.Home.route,
                modifier = Modifier.fillMaxSize().padding(inner)
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
