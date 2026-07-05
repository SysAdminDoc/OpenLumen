package com.openlumen.screenshot

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.openlumen.BuildConfig
import com.openlumen.R
import com.openlumen.diagnostics.MatrixPreview
import com.openlumen.engine.Kelvin
import com.openlumen.engine.Presets
import com.openlumen.presetLabel
import com.openlumen.prefs.EngineKindDto
import com.openlumen.prefs.MatrixDto
import com.openlumen.prefs.NamedProfile
import com.openlumen.prefs.Preferences
import com.openlumen.prefs.ProfileSnapshot
import com.openlumen.prefs.ScheduleDto
import com.openlumen.prefs.ScheduleModeDto
import com.openlumen.ui.components.CommandBlock
import com.openlumen.ui.components.LightSensorCard
import com.openlumen.ui.components.LumenButton
import com.openlumen.ui.components.LumenOutlinedButton
import com.openlumen.ui.components.LumenSwitch
import com.openlumen.ui.theme.OpenLumenTheme
import com.openlumen.ui.theme.lumenChannelColors

@PreviewTest
@Preview(name = "Home tab dark", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
fun HomeTabDark() {
    ScreenFixtureFrame(selected = ScreenTab.Home) {
        HomeTabFixture()
    }
}

@PreviewTest
@Preview(name = "Schedule tab dark", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
fun ScheduleTabDark() {
    ScreenFixtureFrame(selected = ScreenTab.Schedule) {
        ScheduleTabFixture()
    }
}

@PreviewTest
@Preview(name = "Presets tab dark", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
fun PresetsTabDark() {
    ScreenFixtureFrame(selected = ScreenTab.Presets) {
        PresetsTabFixture()
    }
}

@PreviewTest
@Preview(name = "Driver tab dark", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
fun DriverTabDark() {
    ScreenFixtureFrame(selected = ScreenTab.Driver) {
        DriverTabFixture()
    }
}

@PreviewTest
@Preview(name = "About tab dark", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
fun AboutTabDark() {
    ScreenFixtureFrame(selected = ScreenTab.About) {
        AboutTabFixture()
    }
}

private enum class ScreenTab(
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val iconRes: Int
) {
    Home(R.string.nav_home, R.drawable.ic_nav_home),
    Schedule(R.string.nav_schedule, R.drawable.ic_nav_schedule),
    Presets(R.string.nav_presets, R.drawable.ic_nav_presets),
    Driver(R.string.nav_driver, R.drawable.ic_nav_driver),
    About(R.string.nav_about, R.drawable.ic_nav_about)
}

@Composable
private fun ScreenFixtureFrame(
    selected: ScreenTab,
    content: @Composable () -> Unit
) {
    OpenLumenTheme(darkTheme = true) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = { FixtureBottomBar(selected) }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun FixtureBottomBar(selected: ScreenTab) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(88.dp)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ScreenTab.entries.forEach { tab ->
                val isSelected = tab == selected
                val selectedColor = MaterialTheme.colorScheme.primary
                val unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 2.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 56.dp, height = 34.dp)
                            .background(
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    Color.Transparent
                                },
                                shape = MaterialTheme.shapes.medium
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(tab.iconRes),
                            contentDescription = stringResource(tab.labelRes),
                            tint = if (isSelected) selectedColor else unselectedColor
                        )
                    }
                    Text(
                        text = stringResource(tab.labelRes),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSelected) selectedColor else unselectedColor,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun FixtureColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}

@Composable
private fun HomeTabFixture() {
    val prefs = remember {
        Preferences(
            enabled = true,
            activePresetKey = "amber",
            presetIntensity = 0.72f,
            dim = 0.18f,
            contrast = 1.12f,
            amoledBlackClamp = true,
            customMatrix = MatrixDto(r = 1f, g = 0.7f, b = 0.42f),
            lightSensorEnabled = true,
            lightSensorLuxThreshold = 8f,
            favoritePresetKeys = listOf("night", "amber", "red", "deep")
        )
    }
    val activePreset = Presets.byKey(prefs.activePresetKey)
    val activeLabel = activePreset?.let { presetLabel(it.key, it.displayName) } ?: prefs.activePresetKey

    FixtureColumn {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.home_filter_on), style = MaterialTheme.typography.titleLarge)
                    Text(
                        activeLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                LumenSwitch(checked = true, onCheckedChange = {})
            }
        }

        Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SliderRow(
                    title = stringResource(R.string.home_intensity),
                    valueText = stringResource(R.string.home_percent_value, 72),
                    value = prefs.presetIntensity,
                    range = 0f..1f
                )
                SliderRow(
                    title = stringResource(R.string.home_dim),
                    valueText = stringResource(R.string.home_dim_value_precise, prefs.dim * 100f),
                    value = prefs.dim,
                    range = 0f..0.95f
                )
                SliderRow(
                    title = stringResource(R.string.home_contrast),
                    valueText = stringResource(R.string.home_contrast_value, prefs.contrast),
                    value = prefs.contrast,
                    range = Preferences.CONTRAST_MIN..Preferences.CONTRAST_MAX
                )
                Text(
                    stringResource(
                        R.string.home_blue_suppression,
                        (MatrixPreview.blueSuppression(prefs) * 100f).toInt()
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    stringResource(
                        R.string.home_luminance_reduction,
                        (MatrixPreview.perceivedLuminanceReduction(prefs) * 100f).toInt()
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.presets_custom), style = MaterialTheme.typography.titleMedium)
                val channels = lumenChannelColors(darkTheme = true)
                ChannelSliderRow(stringResource(R.string.channel_red_short), prefs.customMatrix.r, channels.red)
                ChannelSliderRow(stringResource(R.string.channel_green_short), prefs.customMatrix.g, channels.green)
                ChannelSliderRow(stringResource(R.string.channel_blue_short), prefs.customMatrix.b, channels.blue)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.home_preview), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(
                                Color(prefs.customMatrix.r, prefs.customMatrix.g, prefs.customMatrix.b),
                                RoundedCornerShape(6.dp)
                            )
                    )
                }
            }
        }

        Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.home_kelvin_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.home_kelvin_value, 2400),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(value = 2400f, onValueChange = {}, valueRange = Kelvin.MIN_K.toFloat()..Kelvin.MAX_K.toFloat())
            }
        }
    }
}

@Composable
private fun ScheduleTabFixture() {
    val schedule = ScheduleDto(
        mode = ScheduleModeDto.Solar,
        startHour = 22,
        startMinute = 0,
        endHour = 7,
        endMinute = 15,
        latitude = 40.7128,
        longitude = -74.0060,
        sunsetOffsetMin = -30,
        sunriseOffsetMin = 20
    )

    FixtureColumn {
        Text(stringResource(R.string.schedule_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.schedule_timezone, "America/New_York"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ScheduleModeDto.entries.forEach { mode ->
            val label = scheduleModeLabel(mode)
            SelectableCard(
                selected = mode == schedule.mode,
                title = label,
                subtitle = if (mode == ScheduleModeDto.Solar) {
                    stringResource(R.string.schedule_solar)
                } else {
                    null
                }
            )
        }
        Card(shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LumenOutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                    Text("40.713, -74.006")
                }
                SliderRow(
                    title = stringResource(R.string.schedule_sunset_offset, schedule.sunsetOffsetMin),
                    valueText = stringResource(R.string.schedule_sunrise_offset, schedule.sunriseOffsetMin),
                    value = 0.42f,
                    range = 0f..1f
                )
            }
        }
        LightSensorCard(
            enabled = true,
            threshold = 8f,
            currentLux = 4.4f,
            onToggle = {},
            onThresholdChange = {},
            onUseCurrent = {}
        )
        Card(shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.transition_duration_title), style = MaterialTheme.typography.titleSmall)
                TransitionOption(stringResource(R.string.transition_instant), selected = false)
                TransitionOption(stringResource(R.string.transition_5m), selected = true)
                TransitionOption(stringResource(R.string.transition_30m), selected = false)
            }
        }
    }
}

@Composable
private fun PresetsTabFixture() {
    val favorites = setOf("night", "amber", "deep")

    FixtureColumn {
        Text(stringResource(R.string.nav_presets), style = MaterialTheme.typography.titleMedium)
        Presets.ALL.take(6).forEach { entry ->
            val selected = entry.key == "amber"
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(
                                Color(entry.matrix.r, entry.matrix.g, entry.matrix.b),
                                RoundedCornerShape(6.dp)
                            )
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = presetLabel(entry.key, entry.displayName),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(onClick = {}) {
                        Icon(
                            painter = painterResource(
                                if (entry.key in favorites) {
                                    R.drawable.ic_favorite_filled
                                } else {
                                    R.drawable.ic_favorite_border
                                }
                            ),
                            contentDescription = stringResource(
                                if (entry.key in favorites) R.string.preset_unfavorite else R.string.preset_favorite
                            )
                        )
                    }
                    RadioButton(selected = selected, onClick = null)
                }
            }
        }
        PresetDetailFixture()
    }
}

@Composable
private fun PresetDetailFixture() {
    val entry = Presets.byKey("amber") ?: return
    val channels = lumenChannelColors(darkTheme = true)
    Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .background(Color(entry.matrix.r, entry.matrix.g, entry.matrix.b), RoundedCornerShape(12.dp))
            )
            Text(presetLabel(entry.key, entry.displayName), style = MaterialTheme.typography.headlineSmall)
            ChannelBar(stringResource(R.string.channel_red_short), entry.matrix.r, channels.red)
            ChannelBar(stringResource(R.string.channel_green_short), entry.matrix.g, channels.green)
            ChannelBar(stringResource(R.string.channel_blue_short), entry.matrix.b, channels.blue)
        }
    }
}

@Composable
private fun DriverTabFixture() {
    FixtureColumn {
        Text(stringResource(R.string.driver_title), style = MaterialTheme.typography.titleMedium)
        DriverChoice(EngineKindDto.Auto, selected = true, available = true, subtitle = stringResource(R.string.driver_auto_resolved, stringResource(R.string.driver_overlay)))
        DriverChoice(EngineKindDto.ColorDisplayManager, selected = false, available = false, subtitle = stringResource(R.string.driver_not_available))
        DriverChoice(EngineKindDto.SurfaceFlinger, selected = false, available = false, subtitle = stringResource(R.string.driver_not_available))
        DriverChoice(EngineKindDto.Kcal, selected = false, available = false, subtitle = stringResource(R.string.driver_not_available))
        DriverChoice(EngineKindDto.Overlay, selected = false, available = true, subtitle = stringResource(R.string.driver_available))

        LumenButton(onClick = {}, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.driver_refresh))
        }

        Card(
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.overlay_caveats_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.overlay_alpha_cap_explanation),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Card(
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.driver_grant_secure_settings), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.driver_grant_status_not_granted),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                CommandBlock("adb shell pm grant com.openlumen.debug android.permission.WRITE_SECURE_SETTINGS")
            }
        }
    }
}

@Composable
private fun AboutTabFixture() {
    val profiles = remember {
        listOf(
            NamedProfile("Reading amber", ProfileSnapshot("amber", MatrixDto(), 0.8f, 0.1f, ScheduleDto(), EngineKindDto.Auto, false, 2f, emptyList(), 0L)),
            NamedProfile("Darkroom", ProfileSnapshot("deep", MatrixDto(), 1f, 0.35f, ScheduleDto(), EngineKindDto.Overlay, true, 5f, emptyList(), 30_000L))
        )
    }

    FixtureColumn {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Text("${stringResource(R.string.about_version)} ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.about_license), style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(R.string.about_offline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        FixtureActionCard(
            title = stringResource(R.string.about_backup_title),
            body = stringResource(R.string.about_backup_body)
        ) {
            LumenButton(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.about_export_profile))
            }
            LumenOutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.about_import_profile))
            }
        }

        FixtureActionCard(
            title = stringResource(R.string.about_profiles_title),
            body = stringResource(R.string.about_profiles_body)
        ) {
            profiles.forEach { profile ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Text(
                        profile.name,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        FixtureActionCard(
            title = stringResource(R.string.about_emergency_off_title),
            body = stringResource(R.string.about_emergency_off_body)
        ) {
            CommandBlock("adb shell am broadcast -a com.openlumen.action.TURN_OFF -n com.openlumen.debug/com.openlumen.service.AutomationReceiver")
        }
    }
}

@Composable
private fun FixtureActionCard(
    title: String,
    body: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            content()
        }
    }
}

@Composable
private fun SliderRow(title: String, valueText: String, value: Float, range: ClosedFloatingPointRange<Float>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(valueText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(value = value, onValueChange = {}, valueRange = range)
    }
}

@Composable
private fun ChannelSliderRow(label: String, value: Float, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.size(20.dp).background(color, RoundedCornerShape(4.dp)))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.widthIn(min = 20.dp))
        Slider(value = value, onValueChange = {}, valueRange = 0f..1f, modifier = Modifier.weight(1f))
        Text("${(value * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(40.dp), textAlign = TextAlign.End)
    }
}

@Composable
private fun ChannelBar(label: String, value: Float, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(24.dp))
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .background(color.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(value.coerceIn(0f, 1f))
                    .background(color, RoundedCornerShape(4.dp))
            )
        }
        Spacer(Modifier.width(8.dp))
        Text("${(value * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(40.dp), textAlign = TextAlign.End)
    }
}

@Composable
private fun SelectableCard(selected: Boolean, title: String, subtitle: String? = null) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = null)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun TransitionOption(label: String, selected: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun DriverChoice(kind: EngineKindDto, selected: Boolean, available: Boolean, subtitle: String) {
    val label = when (kind) {
        EngineKindDto.Auto -> stringResource(R.string.driver_auto)
        EngineKindDto.ColorDisplayManager -> stringResource(R.string.driver_color_display)
        EngineKindDto.SurfaceFlinger -> stringResource(R.string.driver_surfaceflinger)
        EngineKindDto.Kcal -> stringResource(R.string.driver_kcal)
        EngineKindDto.Overlay -> stringResource(R.string.driver_overlay)
    }
    val selectable = kind == EngineKindDto.Auto || available
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = null, enabled = selectable)
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selectable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun scheduleModeLabel(mode: ScheduleModeDto): String = when (mode) {
    ScheduleModeDto.AlwaysOff -> stringResource(R.string.schedule_off)
    ScheduleModeDto.AlwaysOn -> stringResource(R.string.schedule_always)
    ScheduleModeDto.FixedTime -> stringResource(R.string.schedule_fixed)
    ScheduleModeDto.Solar -> stringResource(R.string.schedule_solar)
    ScheduleModeDto.UntilNextAlarm -> stringResource(R.string.schedule_until_next_alarm)
}
