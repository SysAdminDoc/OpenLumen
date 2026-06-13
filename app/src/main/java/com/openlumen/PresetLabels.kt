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
    else -> null
}

@Composable
fun presetLabel(key: String, fallback: String = key): String =
    presetNameRes(key)?.let { stringResource(it) } ?: fallback

fun presetDisplayName(context: Context, key: String, fallback: String = key): String =
    presetNameRes(key)?.let(context::getString) ?: fallback
