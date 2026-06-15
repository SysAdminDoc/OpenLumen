package com.openlumen.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val bottomNavigationClearance = 80.dp

@Composable
internal fun topLevelScrollPadding(
    horizontal: Dp = 16.dp,
    top: Dp = 16.dp,
    bottom: Dp = 16.dp
): PaddingValues {
    val navigationSuiteType = NavigationSuiteScaffoldDefaults
        .calculateFromAdaptiveInfo(currentWindowAdaptiveInfo())
    val bottomPadding = when (navigationSuiteType) {
        NavigationSuiteType.NavigationBar,
        NavigationSuiteType.ShortNavigationBarCompact,
        NavigationSuiteType.ShortNavigationBarMedium -> bottomNavigationClearance
        else -> bottom
    }
    return PaddingValues(
        start = horizontal,
        top = top,
        end = horizontal,
        bottom = bottomPadding
    )
}
