package com.openlumen.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Per the user's no-pill-backdrops rule: nothing in here is allowed to round to
 * half-the-height. Hard cap at 12dp on any container backdrop. Square corners
 * are also fine and look intentional.
 */
val OpenLumenShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(12.dp)
)
