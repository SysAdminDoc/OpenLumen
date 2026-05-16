package com.openlumen.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.openlumen.R
import com.openlumen.engine.Kelvin
import com.openlumen.engine.Presets
import com.openlumen.ui.components.OverlayPermissionCard
import com.openlumen.viewmodel.OpenLumenViewModel
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt

@Composable
fun HomeScreen(vm: OpenLumenViewModel = hiltViewModel()) {
    val prefs by vm.state.collectAsState()
    val preset = Presets.byKey(prefs.activePresetKey)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PaddingValues(16.dp)),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OverlayPermissionCard()

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { vm.setEnabled(!prefs.enabled) }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = if (prefs.enabled)
                            stringResource(R.string.home_filter_on)
                        else
                            stringResource(R.string.home_filter_off),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = preset?.displayName ?: prefs.activePresetKey,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = prefs.enabled, onCheckedChange = vm::setEnabled)
            }
        }

        Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.home_intensity), style = MaterialTheme.typography.titleMedium)
                Text("${(prefs.presetIntensity * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = prefs.presetIntensity,
                    onValueChange = vm::setIntensity,
                    valueRange = 0f..1f,
                    modifier = Modifier.semantics {
                        stateDescription = "${(prefs.presetIntensity * 100).toInt()} percent"
                    }
                )

                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.home_dim), style = MaterialTheme.typography.titleMedium)
                Text("${(prefs.dim * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = prefs.dim,
                    onValueChange = vm::setDim,
                    valueRange = 0f..0.95f,
                    modifier = Modifier.semantics {
                        stateDescription = "${(prefs.dim * 100).toInt()} percent"
                    }
                )

                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.home_contrast), style = MaterialTheme.typography.titleMedium)
                Text("%.2f×".format(prefs.contrast),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = prefs.contrast,
                    onValueChange = vm::setContrast,
                    valueRange = com.openlumen.prefs.Preferences.CONTRAST_MIN..com.openlumen.prefs.Preferences.CONTRAST_MAX,
                    modifier = Modifier.semantics {
                        stateDescription = "%.2f times".format(prefs.contrast)
                    }
                )
            }
        }

        // Custom RGB picker — three slider rows in fixed R/G/B order.
        Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.presets_custom), style = MaterialTheme.typography.titleMedium)

                RgbSlider(
                    label = "R",
                    value = prefs.customMatrix.r,
                    track = Color(0xFFF38BA8),
                    onChange = { vm.setCustomRgb(it, prefs.customMatrix.g, prefs.customMatrix.b) }
                )
                RgbSlider(
                    label = "G",
                    value = prefs.customMatrix.g,
                    track = Color(0xFFA6E3A1),
                    onChange = { vm.setCustomRgb(prefs.customMatrix.r, it, prefs.customMatrix.b) }
                )
                RgbSlider(
                    label = "B",
                    value = prefs.customMatrix.b,
                    track = Color(0xFF89B4FA),
                    onChange = { vm.setCustomRgb(prefs.customMatrix.r, prefs.customMatrix.g, it) }
                )

                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Preview", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.size(8.dp))
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(
                                color = Color(
                                    red = prefs.customMatrix.r.coerceIn(0f, 1f),
                                    green = prefs.customMatrix.g.coerceIn(0f, 1f),
                                    blue = prefs.customMatrix.b.coerceIn(0f, 1f),
                                    alpha = 1f
                                ),
                                shape = RoundedCornerShape(6.dp)
                            )
                    )
                }
            }
        }

        // Kelvin color-temperature picker (C65). UI-local state — the picker
        // is a convenience input that writes through `setCustomKelvin`, but
        // the canonical persisted value is the RGB triplet on `customMatrix`.
        // Reverse-mapping RGB → Kelvin is approximate, so we don't try to
        // derive the slider position from the current RGB on every recomp.
        var kelvinSliderK by rememberSaveable { mutableIntStateOf(Kelvin.DEFAULT_K) }
        Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.home_kelvin_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    "${kelvinSliderK} K",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = kelvinSliderK.toFloat(),
                    onValueChange = { v ->
                        val newK = v.roundToInt().coerceIn(Kelvin.MIN_K, Kelvin.MAX_K)
                        kelvinSliderK = newK
                        vm.setCustomKelvin(newK)
                    },
                    valueRange = Kelvin.MIN_K.toFloat()..Kelvin.MAX_K.toFloat(),
                    modifier = Modifier.semantics {
                        stateDescription = "$kelvinSliderK Kelvin"
                    }
                )
                Text(
                    stringResource(R.string.home_kelvin_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Advanced: per-channel gamma sliders.
        Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Per-channel gamma", style = MaterialTheme.typography.titleMedium)
                Text(
                    "1.0 = neutral. Higher lifts mid-tones; lower deepens them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                GammaSlider(
                    label = "γR",
                    value = prefs.customMatrix.gammaR,
                    track = Color(0xFFF38BA8),
                    onChange = { vm.setGamma(it, prefs.customMatrix.gammaG, prefs.customMatrix.gammaB) }
                )
                GammaSlider(
                    label = "γG",
                    value = prefs.customMatrix.gammaG,
                    track = Color(0xFFA6E3A1),
                    onChange = { vm.setGamma(prefs.customMatrix.gammaR, it, prefs.customMatrix.gammaB) }
                )
                GammaSlider(
                    label = "γB",
                    value = prefs.customMatrix.gammaB,
                    track = Color(0xFF89B4FA),
                    onChange = { vm.setGamma(prefs.customMatrix.gammaR, prefs.customMatrix.gammaG, it) }
                )
            }
        }
    }
}

@Composable
private fun GammaSlider(
    label: String,
    value: Float,
    track: Color,
    onChange: (Float) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(color = track, shape = RoundedCornerShape(4.dp))
        )
        Spacer(Modifier.size(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = 0.5f..2.5f,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
        )
        Text("%.2f".format(value), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun RgbSlider(
    label: String,
    value: Float,
    track: Color,
    onChange: (Float) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(color = track, shape = RoundedCornerShape(4.dp))
        )
        Spacer(Modifier.size(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = 0f..1f,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
        )
        Text("${(value * 100).toInt()}", style = MaterialTheme.typography.bodySmall)
    }
}
