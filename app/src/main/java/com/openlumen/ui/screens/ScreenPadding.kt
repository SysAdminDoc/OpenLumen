package com.openlumen.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val bottomNavigationClearance = 128.dp

internal fun topLevelScrollPadding(
    horizontal: Dp = 16.dp,
    top: Dp = 16.dp,
    bottom: Dp = bottomNavigationClearance
): PaddingValues = PaddingValues(
    start = horizontal,
    top = top,
    end = horizontal,
    bottom = bottom
)
