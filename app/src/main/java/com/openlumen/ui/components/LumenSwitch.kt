package com.openlumen.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@Composable
fun LumenSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interactiveModifier = if (onCheckedChange != null) {
        Modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onCheckedChange
        )
    } else {
        Modifier
    }
    val trackColor = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant
        checked -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val thumbColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
        checked -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.surface
    }

    Box(
        modifier = modifier
            .then(interactiveModifier)
            .size(width = 64.dp, height = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 56.dp, height = 34.dp)
                .background(trackColor, shape = MaterialTheme.shapes.large)
                .padding(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                    .background(thumbColor, shape = MaterialTheme.shapes.small)
            )
        }
    }
}
