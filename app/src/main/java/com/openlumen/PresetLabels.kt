package com.openlumen

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

@StringRes
fun presetNameRes(key: String): Int? = when (key) {
    "off" -> R.string.preset_name_off
    "night" -> R.string.preset_name_night
    "amber" -> R.string.preset_name_amber
    "red" -> R.string.preset_name_red
    "salmon" -> R.string.preset_name_salmon
    "sepia" -> R.string.preset_name_sepia
    "gray" -> R.string.preset_name_gray
    "deep" -> R.string.preset_name_deep
    "pwm" -> R.string.preset_name_pwm
    "protan" -> R.string.preset_name_protan
    "deutan" -> R.string.preset_name_deutan
    "tritan" -> R.string.preset_name_tritan
    // Not a Presets entry: the custom RGB state has no matrix in the
    // catalogue, so byKey returns null for it and every caller fell back
    // to something different. The Home card showed the raw key, the tile
    // showed "Custom", and the Presets screen showed "Custom RGB".
    "custom" -> R.string.presets_custom
    else -> null
}

@Composable
fun presetLabel(key: String, fallback: String = key, override: String? = null): String =
    override?.takeIf { it.isNotBlank() } ?: (presetNameRes(key)?.let { stringResource(it) } ?: fallback)

fun presetDisplayName(
    context: Context,
    key: String,
    fallback: String = key,
    override: String? = null
): String =
    override?.takeIf { it.isNotBlank() } ?: (presetNameRes(key)?.let(context::getString) ?: fallback)
