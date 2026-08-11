package com.openlumen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
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
        val navigationSuiteType = NavigationSuiteScaffoldDefaults
            .calculateFromAdaptiveInfo(currentWindowAdaptiveInfo())
        val onDestinationSelected: (Dest) -> Unit = { dest ->
            nav.navigate(dest.route) {
                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }

        if (navigationSuiteType.usesBottomNavigation()) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    LumenBottomNavigation(
                        currentRoute = currentRoute,
                        onDestinationSelected = onDestinationSelected
                    )
                }
            ) { innerPadding ->
                OpenLumenNavHost(
                    nav = nav,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .consumeWindowInsets(innerPadding)
                        .background(MaterialTheme.colorScheme.background)
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                LumenNavigationRail(
                    currentRoute = currentRoute,
                    onDestinationSelected = onDestinationSelected
                )
                OpenLumenNavHost(
                    nav = nav,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .background(MaterialTheme.colorScheme.background)
                )
            }
        }
    }
}

@Composable
private fun LumenBottomNavigation(
    currentRoute: String?,
    onDestinationSelected: (Dest) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(88.dp)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Dest.all.forEach { dest ->
                LumenDestinationItem(
                    dest = dest,
                    selected = currentRoute == dest.route,
                    onClick = { onDestinationSelected(dest) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
        }
    }
}

@Composable
private fun LumenNavigationRail(
    currentRoute: String?,
    onDestinationSelected: (Dest) -> Unit
) {
    val metrics = navigationRailMetrics(LocalDensity.current.fontScale)
    Surface(
        modifier = Modifier
            .width(metrics.width)
            .fillMaxHeight(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Dest.all.forEach { dest ->
                LumenDestinationItem(
                    dest = dest,
                    selected = currentRoute == dest.route,
                    onClick = { onDestinationSelected(dest) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(metrics.itemHeight)
                )
            }
        }
    }
}

@Composable
private fun LumenDestinationItem(
    dest: Dest,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = stringResource(dest.labelRes)
    val selectedColor = MaterialTheme.colorScheme.primary
    val unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = onClick
            )
            .semantics { contentDescription = label }
            .padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val indicator = if (selected) {
            Modifier.background(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.medium
            )
        } else {
            Modifier
        }
        Box(
            modifier = Modifier
                .size(width = 56.dp, height = 34.dp)
                .then(indicator),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(dest.iconRes),
                contentDescription = label,
                tint = if (selected) selectedColor else unselectedColor
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) selectedColor else unselectedColor,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

internal data class NavigationRailMetrics(
    val width: Dp,
    val itemHeight: Dp
)

internal fun navigationRailMetrics(fontScale: Float): NavigationRailMetrics {
    val scale = if (fontScale.isFinite()) fontScale.coerceIn(1f, 2f) else 1f
    return NavigationRailMetrics(
        width = 120.dp + (88.dp * (scale - 1f)),
        itemHeight = 76.dp + (36.dp * (scale - 1f))
    )
}

@Composable
private fun OpenLumenNavHost(
    nav: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = nav,
        startDestination = Dest.Home.route,
        modifier = modifier
    ) {
        composable(Dest.Home.route)     { HomeScreen() }
        composable(Dest.Schedule.route) { ScheduleScreen() }
        composable(Dest.Presets.route)  { PresetsScreen() }
        composable(Dest.Driver.route)   { DriverScreen() }
        composable(Dest.About.route)    { AboutScreen() }
    }
}

private fun NavigationSuiteType.usesBottomNavigation(): Boolean =
    when (this) {
        NavigationSuiteType.NavigationBar,
        NavigationSuiteType.ShortNavigationBarCompact,
        NavigationSuiteType.ShortNavigationBarMedium -> true
        else -> false
    }
