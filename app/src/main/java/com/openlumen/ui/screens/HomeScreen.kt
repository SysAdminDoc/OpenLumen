package com.openlumen.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.openlumen.diagnostics.DiagnosticsLog
import com.openlumen.R
import com.openlumen.diagnostics.MatrixPreview
import com.openlumen.engine.Kelvin
import com.openlumen.engine.Presets
import com.openlumen.prefs.EngineKindDto
import com.openlumen.presetLabel
import com.openlumen.ui.components.OverlayPermissionCard
import com.openlumen.ui.theme.lumenChannelColors
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
    val prefs by vm.state.collectAsStateWithLifecycle()
    val preset = Presets.byKey(prefs.activePresetKey)
    val activePresetLabel = preset?.let { presetLabel(it.key, it.displayName) }
        ?: prefs.activePresetKey

    val context = LocalContext.current
    val notifPromptPrefs = remember {
        context.getSharedPreferences("openlumen-prompts", Context.MODE_PRIVATE)
    }
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notifPromptPrefs.edit {
            putBoolean("notification_permission_asked", true)
        }
        if (!granted) {
            DiagnosticsLog.log(
                context, DiagnosticsLog.Level.INFO, DiagnosticsLog.Category.SERVICE,
                "notification permission denied on first enable"
            )
        }
    }
    val requestNotifIfNeeded: () -> Unit = remember {
        {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED &&
                !notifPromptPrefs.getBoolean("notification_permission_asked", false)
            ) {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    var intensityDraft by rememberSaveable { mutableFloatStateOf(prefs.presetIntensity) }
    var dimDraft by rememberSaveable { mutableFloatStateOf(prefs.dim) }
    var contrastDraft by rememberSaveable { mutableFloatStateOf(prefs.contrast) }
    var customR by rememberSaveable { mutableFloatStateOf(prefs.customMatrix.r) }
    var customG by rememberSaveable { mutableFloatStateOf(prefs.customMatrix.g) }
    var customB by rememberSaveable { mutableFloatStateOf(prefs.customMatrix.b) }
    var gammaR by rememberSaveable { mutableFloatStateOf(prefs.customMatrix.gammaR) }
    var gammaG by rememberSaveable { mutableFloatStateOf(prefs.customMatrix.gammaG) }
    var gammaB by rememberSaveable { mutableFloatStateOf(prefs.customMatrix.gammaB) }
    var kelvinSliderK by rememberSaveable { mutableIntStateOf(Kelvin.DEFAULT_K) }

    LaunchedEffect(prefs.presetIntensity) { intensityDraft = prefs.presetIntensity }
    LaunchedEffect(prefs.dim) { dimDraft = prefs.dim }
    LaunchedEffect(prefs.contrast) { contrastDraft = prefs.contrast }
    LaunchedEffect(prefs.customMatrix.r) { customR = prefs.customMatrix.r }
    LaunchedEffect(prefs.customMatrix.g) { customG = prefs.customMatrix.g }
    LaunchedEffect(prefs.customMatrix.b) { customB = prefs.customMatrix.b }
    LaunchedEffect(prefs.customMatrix.gammaR) { gammaR = prefs.customMatrix.gammaR }
    LaunchedEffect(prefs.customMatrix.gammaG) { gammaG = prefs.customMatrix.gammaG }
    LaunchedEffect(prefs.customMatrix.gammaB) { gammaB = prefs.customMatrix.gammaB }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(topLevelScrollPadding()),
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
                .toggleable(
                    value = prefs.enabled,
                    role = Role.Switch,
                    onValueChange = { enabled ->
                        vm.setEnabled(enabled)
                        if (enabled) requestNotifIfNeeded()
                    }
                )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Switch(
                    checked = prefs.enabled,
                    onCheckedChange = null
                )
            }
        }

        Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                val intensityPct = (intensityDraft * 100).toInt()
                val intensityState = stringResource(R.string.home_percent_state, intensityPct)
                Text(stringResource(R.string.home_intensity), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.home_percent_value, intensityPct),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = intensityDraft,
                    onValueChange = { intensityDraft = it.coerceIn(0f, 1f) },
                    onValueChangeFinished = { vm.setIntensity(intensityDraft) },
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
                val dimPctF = dimDraft * 100f
                val dimPct = dimPctF.toInt()
                val dimState = stringResource(R.string.home_percent_state, dimPct)
                Text(stringResource(R.string.home_dim), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.home_dim_value_precise, dimPctF),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = dimDraft,
                    onValueChange = { dimDraft = it.coerceIn(0f, 0.95f) },
                    onValueChangeFinished = { vm.setDim(dimDraft) },
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
                    // contentDescription (not stateDescription) is the right
                    // semantics property for an action button — TalkBack reads
                    // it as the button's accessible name instead of the
                    // inner "−" / "+" glyph. Disabling at the bounds gives
                    // visible feedback ("you can't go lower") instead of
                    // silently swallowing taps.
                    IconButton(
                        onClick = {
                            dimDraft = (dimDraft - DIM_FINE_STEP).coerceAtLeast(0f)
                            vm.setDim(dimDraft)
                        },
                        enabled = dimDraft > 0f,
                        modifier = Modifier.semantics { contentDescription = fineDecLabel }
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
                        onClick = {
                            dimDraft = (dimDraft + DIM_FINE_STEP).coerceAtMost(0.95f)
                            vm.setDim(dimDraft)
                        },
                        enabled = dimDraft < 0.95f,
                        modifier = Modifier.semantics { contentDescription = fineIncLabel }
                    ) {
                        Text("+", style = MaterialTheme.typography.titleMedium)
                    }
                }

                Spacer(Modifier.height(8.dp))
                val contrastState = stringResource(R.string.home_contrast_state, contrastDraft)
                Text(stringResource(R.string.home_contrast), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.home_contrast_value, contrastDraft),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = contrastDraft,
                    onValueChange = {
                        contrastDraft = it.coerceIn(
                            com.openlumen.prefs.Preferences.CONTRAST_MIN,
                            com.openlumen.prefs.Preferences.CONTRAST_MAX
                        )
                    },
                    onValueChangeFinished = { vm.setContrast(contrastDraft) },
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
                    val amoledLabel = stringResource(R.string.home_amoled_clamp_title)
                    Switch(
                        checked = prefs.amoledBlackClamp,
                        onCheckedChange = vm::setAmoledBlackClamp,
                        modifier = Modifier.semantics { contentDescription = amoledLabel }
                    )
                }
            }
        }

        // Custom RGB picker — three slider rows in fixed R/G/B order.
        val channels = lumenChannelColors()
        Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.presets_custom), style = MaterialTheme.typography.titleMedium)

                RgbSlider(
                    label = stringResource(R.string.channel_red_short),
                    value = customR,
                    track = channels.red,
                    onChange = { customR = it },
                    onChangeFinished = { vm.setCustomRgb(customR, customG, customB) }
                )
                RgbSlider(
                    label = stringResource(R.string.channel_green_short),
                    value = customG,
                    track = channels.green,
                    onChange = { customG = it },
                    onChangeFinished = { vm.setCustomRgb(customR, customG, customB) }
                )
                RgbSlider(
                    label = stringResource(R.string.channel_blue_short),
                    value = customB,
                    track = channels.blue,
                    onChange = { customB = it },
                    onChangeFinished = { vm.setCustomRgb(customR, customG, customB) }
                )

                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.home_preview), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.size(8.dp))
                    val previewLabel = stringResource(R.string.home_preview)
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .semantics { contentDescription = previewLabel }
                            .background(
                                color = Color(
                                    red = customR.coerceIn(0f, 1f),
                                    green = customG.coerceIn(0f, 1f),
                                    blue = customB.coerceIn(0f, 1f),
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
                    },
                    onValueChangeFinished = { vm.setCustomKelvin(kelvinSliderK) },
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
                    value = gammaR,
                    track = channels.red,
                    onChange = { gammaR = it },
                    onChangeFinished = { vm.setGamma(gammaR, gammaG, gammaB) }
                )
                GammaSlider(
                    label = stringResource(R.string.gamma_green_short),
                    value = gammaG,
                    track = channels.green,
                    onChange = { gammaG = it },
                    onChangeFinished = { vm.setGamma(gammaR, gammaG, gammaB) }
                )
                GammaSlider(
                    label = stringResource(R.string.gamma_blue_short),
                    value = gammaB,
                    track = channels.blue,
                    onChange = { gammaB = it },
                    onChangeFinished = { vm.setGamma(gammaR, gammaG, gammaB) }
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
    onChange: (Float) -> Unit,
    onChangeFinished: () -> Unit
) {
    val gammaState = stringResource(R.string.home_gamma_state, label, value)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(color = track, shape = RoundedCornerShape(4.dp))
        )
        Spacer(Modifier.size(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        Slider(
            value = value,
            onValueChange = onChange,
            onValueChangeFinished = onChangeFinished,
            valueRange = 0.5f..2.5f,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
                .semantics { stateDescription = gammaState }
        )
        Text(
            stringResource(R.string.home_gamma_value, value),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.widthIn(min = 44.dp),
            textAlign = TextAlign.End,
            maxLines = 1
        )
    }
}

@Composable
private fun RgbSlider(
    label: String,
    value: Float,
    track: Color,
    onChange: (Float) -> Unit,
    onChangeFinished: () -> Unit
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
        Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        Slider(
            value = value,
            onValueChange = onChange,
            onValueChangeFinished = onChangeFinished,
            valueRange = 0f..1f,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
                .semantics { stateDescription = rgbState }
        )
        Text(
            stringResource(R.string.home_rgb_value, percent),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.widthIn(min = 32.dp),
            textAlign = TextAlign.End,
            maxLines = 1
        )
    }
}
