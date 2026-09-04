package com.openlumen.ui.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

private val LumenButtonMinHeight = 48.dp

/**
 * Project-wide button wrappers. Material 3's `ButtonDefaults.shape` is a fully-rounded
 * pill / stadium (CircleShape), which is a hard "no" in this codebase. These thin
 * wrappers pin every button to `MaterialTheme.shapes.medium` (10dp rounded rect) so the
 * default styling can't drift back into pill territory.
 *
 * If you find yourself reaching for `Button` directly, use [LumenButton] instead.
 */
@Composable
fun LumenButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.medium,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    content: @Composable RowScope.() -> Unit
) = Button(
    onClick = onClick,
    modifier = modifier.heightIn(min = LumenButtonMinHeight),
    enabled = enabled,
    shape = shape,
    colors = colors,
    content = content
)

@Composable
fun LumenOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.medium,
    content: @Composable RowScope.() -> Unit
) = OutlinedButton(
    onClick = onClick,
    modifier = modifier.heightIn(min = LumenButtonMinHeight),
    enabled = enabled,
    shape = shape,
    content = content
)

@Composable
fun LumenTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.small,
    content: @Composable RowScope.() -> Unit
) = TextButton(
    onClick = onClick,
    modifier = modifier.heightIn(min = LumenButtonMinHeight),
    enabled = enabled,
    shape = shape,
    content = content
)

/**
 * Slider colours for the plain sliders.
 *
 * A Card and a Slider both default to the highest surface container, so a
 * slider sitting in a card painted its unfilled track in exactly the card's
 * colour: the track was invisible and the control looked like it ended at the
 * thumb. The unfilled part takes `outlineVariant` instead, which is a tone
 * apart from every surface in both schemes.
 *
 * The tick colours move with it. Material picks them for contrast against its
 * own default track, and three of these sliders draw ticks, so leaving them
 * behind would put the marks at about 3.3:1 on the new track.
 */
@Composable
fun lumenSliderColors(activeTrack: Color? = null): SliderColors = SliderDefaults.colors(
    activeTrackColor = activeTrack ?: MaterialTheme.colorScheme.primary,
    thumbColor = activeTrack ?: MaterialTheme.colorScheme.primary,
    inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
    inactiveTickColor = MaterialTheme.colorScheme.onSurface,
    activeTickColor = MaterialTheme.colorScheme.onPrimary
)
