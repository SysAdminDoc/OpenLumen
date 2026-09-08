package com.openlumen.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
        // Not surface: against the unchecked track that was 2.63:1 dark and
        // 1.91:1 light, so the thumb of an off switch was barely there. A
        // control you have to see and aim wants 3:1 against what it sits on.
        else -> MaterialTheme.colorScheme.onSurface
    }

    // A disabled track is surfaceVariant on a card that is often surfaceVariant
    // too, and an unchecked track is outlineVariant, which is barely a step off
    // either. The outline gives the control an edge in both states, which is
    // what tells a switch that is off from a switch that is not there. A
    // checked track is `primary` and carries its own edge.
    // onSurfaceVariant, not outline. Measured against the three grounds a
    // switch sits on in this app, outline reaches 1.48:1 on a light unchecked
    // track and onSurfaceVariant never drops below 4.05:1.
    val borderColor = if (enabled && checked) {
        trackColor
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
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
                .border(1.dp, borderColor, shape = MaterialTheme.shapes.large)
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
