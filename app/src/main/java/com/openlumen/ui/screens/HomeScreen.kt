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
import androidx.compose.material3.IconButton
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.openlumen.R
import com.openlumen.diagnostics.MatrixPreview
import com.openlumen.engine.Kelvin
import com.openlumen.engine.Presets
import com.openlumen.prefs.EngineKindDto
import com.openlumen.presetLabel
import com.openlumen.ui.components.OverlayPermissionCard
import com.openlumen.viewmodel.OpenLumenViewModel
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt

/**
 * C114: fine-adjust step for the Dim slider's inline +/- pair. 0.5%
 * lets PWM-sensitive users land at half-percent increments in the
 * low-dim region where pulse-width-modulation flicker is most visible.
 */
private const val DIM_FINE_STEP: Float = 0.005f

@Composable
fun HomeScreen(vm: OpenLumenViewModel = hiltViewModel()) {
    val prefs by vm.state.collectAsState()
    val preset = Presets.byKey(prefs.activePresetKey)
    val activePresetLabel = preset?.let { presetLabel(it.key, it.displayName) }
        ?: prefs.activePresetKey

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PaddingValues(16.dp)),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // The overlay-permission card is only relevant if the active engine
        // selection actually needs the rootless overlay path. A root user who
        // pinned SurfaceFlinger or KCAL doesn't need SYSTEM_ALERT_WINDOW and
        // shouldn't see a permission nag. "Auto" is treated as "might use
        // overlay" because the driver probe is the only thing that knows
        // whether a higher-rank engine is actually available, and the
        // probe runs off the UI thread.
        val overlayCardRelevant =
            prefs.engine == EngineKindDto.Auto || prefs.engine == EngineKindDto.Overlay
        OverlayPermissionCard(requiredByActiveEngine = overlayCardRelevant)

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
                        text = activePresetLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = prefs.enabled, onCheckedChange = vm::setEnabled)
            }
        }

        Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                val intensityPct = (prefs.presetIntensity * 100).toInt()
                val intensityState = stringResource(R.string.home_percent_state, intensityPct)
                Text(stringResource(R.string.home_intensity), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.home_percent_value, intensityPct),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = prefs.presetIntensity,
                    onValueChange = vm::setIntensity,
                    valueRange = 0f..1f,
                    modifier = Modifier.semantics {
                        stateDescription = intensityState
                    }
                )

                Spacer(Modifier.height(8.dp))
                // C114: dim value displayed with one decimal so the inline
                // fine-adjust pair (sub-1% steps) is legible. PWM-sensitive
                // users specifically want to land at half-percent values in
                // the 0-10% dim region where the panel is at lowest
                // backlight and pulse-width-modulation flicker is most
                // visible. The coarse Slider handles broad strokes; the
                // +/- buttons step in 0.5% increments for fine landing.
                val dimPctF = prefs.dim * 100f
                val dimPct = dimPctF.toInt()
                val dimState = stringResource(R.string.home_percent_state, dimPct)
                Text(stringResource(R.string.home_dim), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.home_dim_value_precise, dimPctF),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = prefs.dim,
                    onValueChange = vm::setDim,
                    valueRange = 0f..0.95f,
                    modifier = Modifier.semantics {
                        stateDescription = dimState
                    }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val fineDecLabel = stringResource(R.string.home_dim_fine_decrease)
                    val fineIncLabel = stringResource(R.string.home_dim_fine_increase)
                    IconButton(
                        onClick = { vm.setDim((prefs.dim - DIM_FINE_STEP).coerceAtLeast(0f)) },
                        modifier = Modifier.semantics { stateDescription = fineDecLabel }
                    ) {
                        Text("−", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        stringResource(R.string.home_dim_fine_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { vm.setDim((prefs.dim + DIM_FINE_STEP).coerceAtMost(0.95f)) },
                        modifier = Modifier.semantics { stateDescription = fineIncLabel }
                    ) {
                        Text("+", style = MaterialTheme.typography.titleMedium)
                    }
                }

                Spacer(Modifier.height(8.dp))
                val contrastState = stringResource(R.string.home_contrast_state, prefs.contrast)
                Text(stringResource(R.string.home_contrast), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.home_contrast_value, prefs.contrast),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = prefs.contrast,
                    onValueChange = vm::setContrast,
                    valueRange = com.openlumen.prefs.Preferences.CONTRAST_MIN..com.openlumen.prefs.Preferences.CONTRAST_MAX,
                    modifier = Modifier.semantics {
                        stateDescription = contrastState
                    }
                )

                Spacer(Modifier.height(8.dp))
                // Blue-channel suppression indicator (C61). Physical measurement
                // of the output; not a health metric — see
                // `docs/health-evidence.md`.
                val blueSuppressionPct = (MatrixPreview.blueSuppression(prefs) * 100f).toInt()
                Text(
                    stringResource(R.string.home_blue_suppression, blueSuppressionPct),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val luminanceReductionPct =
                    (MatrixPreview.perceivedLuminanceReduction(prefs) * 100f).toInt()
                Text(
                    stringResource(R.string.home_luminance_reduction, luminanceReductionPct),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.home_amoled_clamp_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            stringResource(R.string.home_amoled_clamp_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = prefs.amoledBlackClamp,
                        onCheckedChange = vm::setAmoledBlackClamp
                    )
                }
            }
        }

        // Custom RGB picker — three slider rows in fixed R/G/B order.
        Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.presets_custom), style = MaterialTheme.typography.titleMedium)

                RgbSlider(
                    label = stringResource(R.string.channel_red_short),
                    value = prefs.customMatrix.r,
                    track = Color(0xFFF38BA8),
                    onChange = { vm.setCustomRgb(it, prefs.customMatrix.g, prefs.customMatrix.b) }
                )
                RgbSlider(
                    label = stringResource(R.string.channel_green_short),
                    value = prefs.customMatrix.g,
                    track = Color(0xFFA6E3A1),
                    onChange = { vm.setCustomRgb(prefs.customMatrix.r, it, prefs.customMatrix.b) }
                )
                RgbSlider(
                    label = stringResource(R.string.channel_blue_short),
                    value = prefs.customMatrix.b,
                    track = Color(0xFF89B4FA),
                    onChange = { vm.setCustomRgb(prefs.customMatrix.r, prefs.customMatrix.g, it) }
                )

                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.home_preview), style = MaterialTheme.typography.bodySmall)
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
                val kelvinState = stringResource(R.string.home_kelvin_state, kelvinSliderK)
                Text(stringResource(R.string.home_kelvin_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.home_kelvin_value, kelvinSliderK),
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
                        stateDescription = kelvinState
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
                Text(stringResource(R.string.home_gamma_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.home_gamma_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                GammaSlider(
                    label = stringResource(R.string.gamma_red_short),
                    value = prefs.customMatrix.gammaR,
                    track = Color(0xFFF38BA8),
                    onChange = { vm.setGamma(it, prefs.customMatrix.gammaG, prefs.customMatrix.gammaB) }
                )
                GammaSlider(
                    label = stringResource(R.string.gamma_green_short),
                    value = prefs.customMatrix.gammaG,
                    track = Color(0xFFA6E3A1),
                    onChange = { vm.setGamma(prefs.customMatrix.gammaR, it, prefs.customMatrix.gammaB) }
                )
                GammaSlider(
                    label = stringResource(R.string.gamma_blue_short),
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
    val gammaState = stringResource(R.string.home_gamma_state, label, value)
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
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
                .semantics { stateDescription = gammaState }
        )
        Text(stringResource(R.string.home_gamma_value, value), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun RgbSlider(
    label: String,
    value: Float,
    track: Color,
    onChange: (Float) -> Unit
) {
    val percent = (value * 100).toInt()
    val rgbState = stringResource(R.string.home_rgb_state, label, percent)
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
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
                .semantics { stateDescription = rgbState }
        )
        Text(stringResource(R.string.home_rgb_value, percent), style = MaterialTheme.typography.bodySmall)
    }
}
