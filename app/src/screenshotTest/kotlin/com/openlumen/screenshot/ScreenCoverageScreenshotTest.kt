package com.openlumen.screenshot

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import com.android.tools.screenshot.PreviewTest
import com.openlumen.engine.DriverProbe
import com.openlumen.engine.EngineKind
import com.openlumen.prefs.EngineKindDto
import com.openlumen.prefs.ImportSummary
import com.openlumen.prefs.MatrixDto
import com.openlumen.prefs.NamedProfile
import com.openlumen.prefs.Preferences
import com.openlumen.prefs.PreferencesRecovery
import com.openlumen.prefs.ScheduleDto
import com.openlumen.prefs.ScheduleModeDto
import com.openlumen.ui.OpenLumenRoot
import com.openlumen.viewmodel.OpenLumenScreenModel
import com.openlumen.viewmodel.OpenLumenViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.net.Uri
import androidx.compose.runtime.Composable

/**
 * Production-composable screenshot coverage. The fake model supplies only
 * deterministic state and no-op actions; the layout, navigation scaffold,
 * insets, dialogs, and theme all come from the app's real screens.
 */
@PreviewTest
@Preview(name = "Production home light phone", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
fun ProductionHomeLightPhone() {
    ProductionScreenPreview(route = "home", darkTheme = false, model = PreviewScreenModel.home())
}

@PreviewTest
@Preview(name = "Production home dark phone", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
fun ProductionHomeDarkPhone() {
    ProductionScreenPreview(route = "home", darkTheme = true, model = PreviewScreenModel.home())
}

@PreviewTest
@Preview(name = "Production home right to left", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
fun ProductionHomeRtl() {
    // C271. The manifest has claimed supportsRtl since the first release and
    // nothing had ever rendered the app mirrored, so the claim was untested.
    ProductionScreenPreview(
        route = "home",
        darkTheme = false,
        model = PreviewScreenModel.home(),
        layoutDirection = LayoutDirection.Rtl
    )
}

@PreviewTest
@Preview(name = "Production schedule right to left", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
fun ProductionScheduleRtl() {
    ProductionScreenPreview(
        route = "schedule",
        darkTheme = false,
        model = PreviewScreenModel.schedule(),
        layoutDirection = LayoutDirection.Rtl
    )
}

@PreviewTest
@Preview(name = "Production presets right to left", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
fun ProductionPresetsRtl() {
    ProductionScreenPreview(
        route = "presets",
        darkTheme = false,
        model = PreviewScreenModel.home(),
        layoutDirection = LayoutDirection.Rtl
    )
}

@PreviewTest
@Preview(name = "Production driver right to left", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
fun ProductionDriverRtl() {
    ProductionScreenPreview(
        route = "driver",
        darkTheme = false,
        model = PreviewScreenModel.driver(),
        layoutDirection = LayoutDirection.Rtl
    )
}

@PreviewTest
@Preview(name = "Production about right to left", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
fun ProductionAboutRtl() {
    ProductionScreenPreview(
        route = "about",
        darkTheme = false,
        model = PreviewScreenModel.home(),
        layoutDirection = LayoutDirection.Rtl
    )
}

@PreviewTest
@Preview(
    name = "Production home light phone at 2x font",
    showBackground = true,
    widthDp = 393,
    heightDp = 852,
    fontScale = 2f)
@Composable
fun ProductionHomeLargeFont() {
    // C323. The bottom bar had a fixed height while the rail grew with the
    // font scale, so a two-line tab label was clipped at 1.5x and above.
    ProductionScreenPreview(route = "home", darkTheme = false, model = PreviewScreenModel.home())
}

@PreviewTest
@Preview(
    name = "Production presets light phone at 2x font",
    showBackground = true,
    widthDp = 393,
    heightDp = 852,
    fontScale = 2f)
@Composable
fun ProductionPresetsLargeFont() {
    // The channel meter's label and percentage cells were fixed widths, so
    // "100%" lost a character with no ellipsis to show for it.
    ProductionScreenPreview(route = "presets", darkTheme = false, model = PreviewScreenModel.home())
}

@PreviewTest
@Preview(name = "Production schedule light phone", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
fun ProductionScheduleLightPhone() {
    ProductionScreenPreview(
        route = "schedule",
        darkTheme = false,
        model = PreviewScreenModel.schedule()
    )
}

@PreviewTest
@Preview(name = "Production schedule dark rail", showBackground = true, widthDp = 900, heightDp = 800)
@Composable
fun ProductionScheduleDarkRail() {
    ProductionScreenPreview(
        route = "schedule",
        darkTheme = true,
        model = PreviewScreenModel.schedule()
    )
}

@PreviewTest
@Preview(name = "Production presets light phone empty detail", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
fun ProductionPresetsLightPhone() {
    ProductionScreenPreview(
        route = "presets",
        darkTheme = false,
        model = PreviewScreenModel.home()
    )
}

@PreviewTest
@Preview(name = "Production driver dark phone loading error", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
fun ProductionDriverDarkPhone() {
    ProductionScreenPreview(
        route = "driver",
        darkTheme = true,
        model = PreviewScreenModel.driver()
    )
}

@PreviewTest
@Preview(name = "Production about light phone import dialog", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
fun ProductionAboutLightPhone() {
    ProductionScreenPreview(
        route = "about",
        darkTheme = false,
        model = PreviewScreenModel.about()
    )
}

@PreviewTest
@Preview(name = "Production root dark rail", showBackground = true, widthDp = 900, heightDp = 800)
@Composable
fun ProductionRootDarkRail() {
    ProductionScreenPreview(route = "home", darkTheme = true, model = PreviewScreenModel.home())
}

@Composable
private fun ProductionScreenPreview(
    route: String,
    darkTheme: Boolean,
    model: OpenLumenScreenModel,
    layoutDirection: LayoutDirection = LayoutDirection.Ltr
) {
    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        OpenLumenRoot(
            screenModel = model,
            initialRoute = route,
            enableSystemActions = false,
            darkTheme = darkTheme
        )
    }
}

private class PreviewScreenModel(
    preferences: Preferences,
    luxValue: Float = 18f,
    probesValue: List<DriverProbe.Probe> = availableProbes(),
    refreshing: Boolean = false,
    probeFailure: String? = null,
    pending: OpenLumenViewModel.PendingImport? = null
) : OpenLumenScreenModel {
    private val _state = MutableStateFlow(preferences)
    private val _preferenceRecovery = MutableStateFlow<PreferencesRecovery?>(null)
    private val _probes = MutableStateFlow(probesValue)
    private val _probesRefreshing = MutableStateFlow(refreshing)
    private val _probeError = MutableStateFlow(probeFailure)
    private val _lux = MutableStateFlow(luxValue)
    private val _lightSensorAvailable = MutableStateFlow(true)
    private val _exportResult = MutableStateFlow<String?>(null)
    private val _pendingImport = MutableStateFlow(pending)

    override val state: StateFlow<Preferences> = _state.asStateFlow()
    override val preferenceRecovery: StateFlow<PreferencesRecovery?> =
        _preferenceRecovery.asStateFlow()
    override val probes: StateFlow<List<DriverProbe.Probe>> = _probes.asStateFlow()
    override val probesRefreshing: StateFlow<Boolean> = _probesRefreshing.asStateFlow()
    override val probeError: StateFlow<String?> = _probeError.asStateFlow()
    override val lux: StateFlow<Float> = _lux.asStateFlow()
    override val lightSensorAvailable: StateFlow<Boolean> = _lightSensorAvailable.asStateFlow()
    override val exportResult: StateFlow<String?> = _exportResult.asStateFlow()
    override val pendingImport: StateFlow<OpenLumenViewModel.PendingImport?> =
        _pendingImport.asStateFlow()

    override fun setEnabled(enabled: Boolean) = Unit
    override fun selectPreset(key: String): Job = Job()
    override fun restorePreviousPreset(): Job = Job()
    override fun saveProfileAs(name: String, replaceExisting: Boolean): Job = Job()
    override fun loadProfile(name: String): Job = Job()
    override fun deleteProfile(name: String): Job = Job()
    override fun restoreDeletedProfile(profile: NamedProfile): Job = Job()
    override fun setScheduleMode(mode: ScheduleModeDto): Job = Job()
    override fun setScheduleTimes(startH: Int, startM: Int, endH: Int, endM: Int): Job = Job()
    override fun setLocation(lat: Double, lng: Double, solarTimezone: String?): Job = Job()
    override fun setEngine(kind: EngineKindDto): Job = Job()
    override fun setIntensity(value: Float): Job = Job()
    override fun setDim(value: Float): Job = Job()
    override fun setAmoledBlackClamp(enabled: Boolean): Job = Job()
    override fun setContrast(value: Float): Job = Job()
    override fun setCustomRgb(r: Float, g: Float, b: Float): Job = Job()
    override fun setCustomKelvin(kelvin: Int): Job = Job()
    override fun setGamma(r: Float, g: Float, b: Float): Job = Job()
    override fun setScheduleOffsets(sunsetMin: Int, sunriseMin: Int): Job = Job()
    override fun reconcileExactAlarmPermission(): Job = Job()
    override fun setLightSensor(enabled: Boolean, threshold: Float): Job = Job()
    override fun setTransitionDuration(durationMs: Long): Job = Job()
    override fun refreshProbes(): Job = Job()
    override fun buildDriverReport(): String = "OpenLumen production screenshot preview"
    override fun exportTo(uri: Uri): Job = Job()
    override fun exportCorruptPreferencesTo(uri: Uri): Job = Job()
    override fun resetCorruptPreferences(): Job = Job()
    override fun importFrom(uri: Uri): Job = Job()
    override fun consumeExportResult() = Unit
    override fun beginImportPreview(uri: Uri): Job = Job()
    override fun confirmPendingImport(): Job = Job()
    override fun cancelPendingImport() = Unit
    override fun toggleFavorite(key: String): Job = Job()

    companion object {
        fun home() = PreviewScreenModel(
            preferences = Preferences(
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
        )

        fun schedule() = PreviewScreenModel(
            preferences = home().state.value.copy(
                schedule = ScheduleDto(
                    mode = ScheduleModeDto.Solar,
                    latitude = 40.7128,
                    longitude = -74.0060,
                    solarTimezone = "America/New_York",
                    sunsetOffsetMin = -30,
                    sunriseOffsetMin = 20
                ),
                lightSensorEnabled = true
            ),
            luxValue = 4.2f
        )

        fun driver() = PreviewScreenModel(
            preferences = home().state.value.copy(engine = EngineKindDto.Auto),
            refreshing = true,
            probeFailure = "Driver probe failed. Try again."
        )

        fun about() = PreviewScreenModel(
            preferences = home().state.value.copy(
                savedProfiles = listOf(
                    NamedProfile("Evening", com.openlumen.prefs.Profiles.snapshot(home().state.value))
                )
            ),
            pending = OpenLumenViewModel.PendingImport(
                ImportSummary(
                    preferences = home().state.value.copy(activePresetKey = "night", dim = 0.3f),
                    droppedDuplicateNames = listOf("Evening")
                )
            )
        )
    }
}

private fun availableProbes(): List<DriverProbe.Probe> =
    DriverProbe.defaultEngines()
        .map { engine ->
            DriverProbe.Probe(
                engine = engine,
                available = engine.kind == EngineKind.COLOR_DISPLAY_MANAGER ||
                    engine.kind == EngineKind.OVERLAY
            )
        }
