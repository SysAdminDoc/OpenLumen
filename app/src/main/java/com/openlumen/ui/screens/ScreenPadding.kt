package com.openlumen.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun topLevelScrollPadding(
    horizontal: Dp = 16.dp,
    top: Dp = 16.dp,
    bottom: Dp = 24.dp
): PaddingValues {
    return PaddingValues(
        start = horizontal,
        top = top,
        end = horizontal,
        bottom = bottom
    )
}
