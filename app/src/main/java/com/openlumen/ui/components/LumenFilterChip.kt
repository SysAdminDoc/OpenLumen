package com.openlumen.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.openlumen.R

/**
 * A filter chip whose selected state is visible without colour and clear with it.
 *
 * Material's own selected fill is `secondaryContainer`, which this theme maps to
 * one step off the surface in both palettes: a selected chip measured 1.40:1
 * against the screen behind it in dark and 1.37:1 in light, which is nothing.
 * Colour was also the only thing carrying the state, so a user who cannot
 * separate those two greys had no way to read the control at all.
 *
 * `primary` is the fill instead, which is how the rest of the app already says
 * "on", and a check mark says it again for anyone the colour does not reach.
 */
@Composable
fun LumenFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        leadingIcon = if (selected) {
            {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    // The label already says what is selected, and the chip's
                    // own selected state is announced by the role. A name here
                    // would make TalkBack read the label twice.
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        } else {
            null
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
        )
    )
}
