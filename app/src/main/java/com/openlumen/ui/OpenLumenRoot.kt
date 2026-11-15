package com.openlumen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.openlumen.R
import com.openlumen.ui.components.BlockedStartUi
import com.openlumen.ui.screens.AboutScreen
import com.openlumen.ui.screens.DriverScreen
import com.openlumen.ui.screens.HomeScreen
import com.openlumen.ui.screens.PresetsScreen
import com.openlumen.ui.screens.ScheduleScreen
import com.openlumen.ui.theme.OpenLumenTheme
import com.openlumen.viewmodel.OpenLumenScreenModel
import androidx.compose.foundation.isSystemInDarkTheme

internal sealed class Dest(val route: String, val labelRes: Int, val iconRes: Int) {
    data object Home     : Dest("home",     R.string.nav_home,     R.drawable.ic_nav_home)
    data object Schedule : Dest("schedule", R.string.nav_schedule, R.drawable.ic_nav_schedule)
    data object Presets  : Dest("presets",  R.string.nav_presets,  R.drawable.ic_nav_presets)
    data object Driver   : Dest("driver",   R.string.nav_driver,   R.drawable.ic_nav_driver)
    data object About    : Dest("about",    R.string.nav_about,    R.drawable.ic_nav_about)

    companion object {
        val all: List<Dest>
            get() = listOf(Home, Schedule, Presets, Driver, About)
    }
}

@Composable
fun OpenLumenRoot(
    screenModel: OpenLumenScreenModel? = null,
    initialRoute: String = Dest.Home.route,
    enableSystemActions: Boolean = true,
    darkTheme: Boolean? = null,
    blockedStart: BlockedStartUi? = null
) {
    OpenLumenTheme(darkTheme = darkTheme ?: isSystemInDarkTheme()) {
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
        // A tile or widget tapped while the app sat on another tab lands on
        // Home, where the notice is, the same as a cold launch does. Once per
        // notice, so a rotation does not pull the user back from a tab they
        // chose afterwards.
        val visibleBlockedStartId = blockedStart?.notice?.takeIf { it.visible }?.id
        var routedBlockedStartId by rememberSaveable { mutableLongStateOf(0L) }
        LaunchedEffect(visibleBlockedStartId) {
            if (visibleBlockedStartId == null || visibleBlockedStartId == routedBlockedStartId) {
                return@LaunchedEffect
            }
            routedBlockedStartId = visibleBlockedStartId
            if (nav.currentDestination?.route != Dest.Home.route) onDestinationSelected(Dest.Home)
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
                    startRoute = initialRoute,
                    screenModel = screenModel,
                    enableSystemActions = enableSystemActions,
                    blockedStart = blockedStart,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .consumeWindowInsets(innerPadding)
                        .background(MaterialTheme.colorScheme.background)
                )
            }
        } else {
            LumenRailLayout(
                currentRoute = currentRoute,
                onDestinationSelected = onDestinationSelected
            ) { modifier ->
                OpenLumenNavHost(
                    nav = nav,
                    startRoute = initialRoute,
                    screenModel = screenModel,
                    enableSystemActions = enableSystemActions,
                    blockedStart = blockedStart,
                    modifier = modifier
                )
            }
        }
    }
}

/** The tablet layout: the rail on the start side and the screen beside it, in [content]'s modifier. */
@Composable
internal fun LumenRailLayout(
    currentRoute: String?,
    onDestinationSelected: (Dest) -> Unit,
    content: @Composable (Modifier) -> Unit
) {
    // A Surface, not a background modifier: the Scaffold the phone layout
    // uses also sets the content color to onBackground, and without it
    // every text that takes the default color (the screen headings, the
    // Sort label on Presets) was drawn black on the dark theme's black
    // background.
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Row(modifier = Modifier.fillMaxSize()) {
            LumenNavigationRail(
                currentRoute = currentRoute,
                onDestinationSelected = onDestinationSelected
            )
            content(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .background(MaterialTheme.colorScheme.background)
            )
        }
    }
}

@Composable
internal fun LumenBottomNavigation(
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
                // Scaled, but still a fixed height: the rail already grows
                // with the font scale and the bar did not, so at 1.5x and
                // above a two-line label plus the icon exceeded a flat 88 dp
                // and clipped. heightIn would let this Row take the whole
                // column and push the screen off.
                .height(bottomBarHeight(LocalDensity.current.fontScale))
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
                // The Text below already names the tab, and a merged
                // semantics node reads every description it contains. With
                // this set and a Column description as well, each tab
                // announced "Home, Home, Home, tab".
                contentDescription = null,
                tint = if (selected) selectedColor else unselectedColor
            )
        }
        NavDestinationLabel(
            text = label,
            color = if (selected) selectedColor else unselectedColor,
            selected = selected
        )
    }
}

/**
 * A tab label gets a line per word, up to two, and shrinks until every word
 * fits whole. At 2x font size "Schedule" split into "Sche" and "dule" in a
 * phone-width bar, and "Controlador" and "Startseite" split even at 1x.
 */
@Composable
internal fun NavDestinationLabel(text: String, color: Color, selected: Boolean) {
    val theme = MaterialTheme.typography.labelMedium
    // Sized in the selected weight, the wider one, so a label keeps its size when picked.
    val sizing = theme.copy(
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        // In em, so the line spacing comes down with a label that shrinks.
        lineHeight = (theme.lineHeight.value / theme.fontSize.value).em
    )
    val maxLines = navLabelMaxLines(text)
    val measurer = rememberTextMeasurer()
    val floor = with(LocalDensity.current) { NAV_LABEL_FLOOR.toSp() }
    BoxWithConstraints(contentAlignment = Alignment.Center) {
        val fontSize = remember(text, sizing, constraints, floor) {
            navLabelFontSize(measurer, text, sizing, constraints, maxLines, floor)
        }
        Text(
            text = text,
            style = sizing.copy(
                fontSize = fontSize,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            ),
            color = color,
            maxLines = maxLines
        )
    }
}

/** A word that does not fit is broken mid-word, so a line per word is the most a label may take. */
internal fun navLabelMaxLines(text: String): Int = navLabelWords(text).size.coerceIn(1, 2)

private fun navLabelWords(text: String): List<String> = text.trim().split(Regex("\\s+"))

/**
 * The largest size, from the theme's down to [floor], at which every word of
 * [text] fits the width whole and the label fits the height. Words are
 * measured on their own and rounded up, so a word that fits here is never the
 * one the line breaker splits.
 */
internal fun navLabelFontSize(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    constraints: Constraints,
    maxLines: Int,
    floor: TextUnit
): TextUnit {
    val words = navLabelWords(text)
    val bounds = Constraints(maxWidth = constraints.maxWidth, maxHeight = constraints.maxHeight)
    var size = style.fontSize.value
    while (size > floor.value) {
        val candidate = style.copy(fontSize = size.sp)
        val wordsFit = words.all { word ->
            measurer.measure(word, candidate, softWrap = false, maxLines = 1).size.width <= constraints.maxWidth
        }
        if (wordsFit && !measurer.measure(text, candidate, maxLines = maxLines, constraints = bounds).hasVisualOverflow) {
            return size.sp
        }
        size -= NAV_LABEL_STEP_SP
    }
    return floor
}

/** Absolute, so it does not grow with the font size it is there to make room for. */
private val NAV_LABEL_FLOOR = 8.dp
private const val NAV_LABEL_STEP_SP = 0.5f

internal data class NavigationRailMetrics(
    val width: Dp,
    val itemHeight: Dp
)

/**
 * How tall the bottom bar has to be at a given font scale.
 *
 * Same shape as [navigationRailMetrics], and for the same reason: the labels
 * are allowed two lines, so the bar has to have room for two lines plus the
 * icon before it starts cutting them off.
 */
internal fun bottomBarHeight(fontScale: Float): Dp {
    val scale = if (fontScale.isFinite()) fontScale.coerceIn(1f, 2f) else 1f
    return 88.dp + (44.dp * (scale - 1f))
}

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
    startRoute: String,
    screenModel: OpenLumenScreenModel?,
    enableSystemActions: Boolean,
    blockedStart: BlockedStartUi?,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = nav,
        startDestination = startRoute,
        modifier = modifier
    ) {
        composable(Dest.Home.route) {
            if (screenModel == null) {
                HomeScreen(enableSystemActions = enableSystemActions, blockedStart = blockedStart)
            } else {
                HomeScreen(screenModel, enableSystemActions, blockedStart)
            }
        }
        composable(Dest.Schedule.route) {
            if (screenModel == null) ScheduleScreen(enableSystemActions = enableSystemActions)
            else ScheduleScreen(screenModel, enableSystemActions)
        }
        composable(Dest.Presets.route) {
            if (screenModel == null) PresetsScreen(enableSystemActions = enableSystemActions)
            else PresetsScreen(screenModel, enableSystemActions)
        }
        composable(Dest.Driver.route) {
            if (screenModel == null) DriverScreen()
            else DriverScreen(screenModel)
        }
        composable(Dest.About.route) {
            if (screenModel == null) AboutScreen(enableSystemActions = enableSystemActions)
            else AboutScreen(screenModel, enableSystemActions)
        }
    }
}

private fun NavigationSuiteType.usesBottomNavigation(): Boolean =
    when (this) {
        NavigationSuiteType.NavigationBar,
        NavigationSuiteType.ShortNavigationBarCompact,
        NavigationSuiteType.ShortNavigationBarMedium -> true
        else -> false
    }
