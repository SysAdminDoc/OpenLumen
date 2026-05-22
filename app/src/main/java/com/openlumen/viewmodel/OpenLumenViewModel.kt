package com.openlumen.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.openlumen.diagnostics.DriverReport
import com.openlumen.engine.DriverProbe
import com.openlumen.prefs.EngineKindDto
import com.openlumen.prefs.ImportSummary
import com.openlumen.prefs.Preferences
import com.openlumen.prefs.PreferencesStore
import com.openlumen.prefs.ScheduleModeDto
import com.openlumen.prefs.normalizedEnabledFilterState
import com.openlumen.prefs.withFilterEnabled
import com.openlumen.schedule.LightSensorAdapter
import com.openlumen.service.LumenService
import com.openlumen.service.LumenServiceStarter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OpenLumenViewModel @Inject constructor(
    application: Application,
    private val prefs: PreferencesStore,
    private val probe: DriverProbe,
    private val lightSensor: LightSensorAdapter
) : AndroidViewModel(application) {

    private val tag = "OpenLumen/ViewModel"

    val state: StateFlow<Preferences> = prefs.flow
        .stateIn(viewModelScope, SharingStarted.Eagerly, Preferences())

    private val _probes = MutableStateFlow<List<DriverProbe.Probe>>(emptyList())
    val probes: StateFlow<List<DriverProbe.Probe>> = _probes.asStateFlow()

    /** Live ambient-light lux reading. -1 means no sensor / not yet emitted. */
    val lux: StateFlow<Float> = lightSensor.lux()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), -1f)

    init {
        normalizeEnabledFilterState()
        refreshProbes()
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            // Order matters: write the pref first so the service's
            // observePreferences sees `enabled=true` on its first flow
            // emission, then start the service. If the start request is
            // rejected (very rare; usually ForegroundService restrictions
            // in unusual lifecycle states), roll the pref back so the
            // toggle UI reflects reality.
            prefs.update { it.withFilterEnabled(enabled) }
            if (enabled) {
                if (!startService()) {
                    prefs.update { it.copy(enabled = false) }
                    _exportResult.value = getApplication<Application>()
                        .getString(com.openlumen.R.string.toast_service_start_failed)
                }
            } else {
                stopService()
            }
        }
    }

    fun selectPreset(key: String) = viewModelScope.launch {
        prefs.update { com.openlumen.prefs.PresetCycle.setActiveKey(it, key) }
    }

    /** Restore the previously-active preset (C14). No-op if none recorded. */
    fun restorePreviousPreset() = viewModelScope.launch {
        prefs.update { com.openlumen.prefs.PresetCycle.restorePrevious(it) }
    }

    /** Save the current configuration into the named-profile library (C31). */
    fun saveProfileAs(name: String) = viewModelScope.launch {
        prefs.update { com.openlumen.prefs.Profiles.saveCurrentAs(it, name) }
    }

    fun loadProfile(name: String) = viewModelScope.launch {
        prefs.update { com.openlumen.prefs.Profiles.loadByName(it, name) }
    }

    fun deleteProfile(name: String) = viewModelScope.launch {
        prefs.update { com.openlumen.prefs.Profiles.delete(it, name) }
    }

    fun setScheduleMode(mode: ScheduleModeDto) = viewModelScope.launch {
        prefs.update { it.copy(schedule = it.schedule.copy(mode = mode)) }
    }

    fun setScheduleTimes(startH: Int, startM: Int, endH: Int, endM: Int) = viewModelScope.launch {
        prefs.update {
            it.copy(schedule = it.schedule.copy(
                startHour = startH, startMinute = startM, endHour = endH, endMinute = endM
            ))
        }
    }

    fun setLocation(lat: Double, lng: Double) = viewModelScope.launch {
        prefs.update { it.copy(schedule = it.schedule.copy(latitude = lat, longitude = lng)) }
    }

    fun setEngine(kind: EngineKindDto) = viewModelScope.launch {
        prefs.update { it.copy(engine = availableEngineOrAuto(kind)) }
    }

    fun setIntensity(value: Float) = viewModelScope.launch {
        prefs.update { it.copy(presetIntensity = value.coerceIn(0f, 1f)) }
    }

    fun setDim(value: Float) = viewModelScope.launch {
        prefs.update { it.copy(dim = value.coerceIn(0f, 0.95f)) }
    }

    /** AMOLED true-black clamp (C66). Off by default; safe no-op on LCD. */
    fun setAmoledBlackClamp(enabled: Boolean) = viewModelScope.launch {
        prefs.update { it.copy(amoledBlackClamp = enabled) }
    }

    /** Contrast multiplier (C64). 1.0 = identity. */
    fun setContrast(value: Float) = viewModelScope.launch {
        prefs.update {
            it.copy(
                contrast = value.coerceIn(
                    com.openlumen.prefs.Preferences.CONTRAST_MIN,
                    com.openlumen.prefs.Preferences.CONTRAST_MAX
                )
            )
        }
    }

    fun setCustomRgb(r: Float, g: Float, b: Float) = viewModelScope.launch {
        prefs.update {
            it.copy(
                activePresetKey = "custom",
                customMatrix = it.customMatrix.copy(
                    r = r.coerceIn(0f, 1f),
                    g = g.coerceIn(0f, 1f),
                    b = b.coerceIn(0f, 1f)
                )
            )
        }
    }

    /**
     * Kelvin-temperature input (C65). Converts to an RGB triplet via the
     * Tanner Helland approximation and writes through `setCustomRgb`. Slider
     * range is clamped at the [com.openlumen.engine.Kelvin] bounds before
     * conversion.
     */
    fun setCustomKelvin(kelvin: Int) = viewModelScope.launch {
        val rgb = com.openlumen.engine.Kelvin.toRgb(kelvin)
        prefs.update {
            it.copy(
                activePresetKey = "custom",
                customMatrix = it.customMatrix.copy(r = rgb.r, g = rgb.g, b = rgb.b)
            )
        }
    }

    fun setGamma(r: Float, g: Float, b: Float) = viewModelScope.launch {
        prefs.update {
            it.copy(
                customMatrix = it.customMatrix.copy(
                    gammaR = r.coerceIn(0.5f, 2.5f),
                    gammaG = g.coerceIn(0.5f, 2.5f),
                    gammaB = b.coerceIn(0.5f, 2.5f)
                )
            )
        }
    }

    fun setScheduleOffsets(sunsetMin: Int, sunriseMin: Int) = viewModelScope.launch {
        prefs.update {
            it.copy(schedule = it.schedule.copy(
                sunsetOffsetMin = sunsetMin.coerceIn(-180, 180),
                sunriseOffsetMin = sunriseMin.coerceIn(-180, 180)
            ))
        }
    }

    fun setLightSensor(enabled: Boolean, threshold: Float) = viewModelScope.launch {
        prefs.update {
            it.copy(
                lightSensorEnabled = enabled,
                lightSensorLuxThreshold = threshold.coerceAtLeast(0f)
            )
        }
    }

    /**
     * Smooth-transition duration (C23/C24). Sanitization clamps the value
     * into 0..TRANSITION_MAX_MS; 0 disables the ramp entirely.
     */
    fun setTransitionDuration(durationMs: Long) = viewModelScope.launch {
        prefs.update { it.copy(transitionDurationMs = durationMs) }
    }

    fun refreshProbes() = viewModelScope.launch {
        // Invalidate the per-process su availability cache before re-probing:
        // a user who grants Magisk root after first launch should be able to
        // see root-only engines light up without restarting the app.
        com.openlumen.engine.Su.resetCache()
        val results = probe.probeAll(getApplication())
        _probes.value = results
        val current = state.value.engine
        if (current != EngineKindDto.Auto && results.isUnavailable(current)) {
            prefs.update { prefs ->
                if (prefs.engine == current) prefs.copy(engine = EngineKindDto.Auto) else prefs
            }
        }
    }

    /**
     * Synchronous snapshot of the human-readable driver report.
     *
     * Tied to roadmap candidate C02. Composition: latest probe results from
     * [_probes], current [state] preferences, plus device + permission info
     * pulled from the Android `Context`. No I/O.
     */
    fun buildDriverReport(): String =
        DriverReport.build(getApplication(), state.value, _probes.value)

    private val _exportResult = MutableStateFlow<String?>(null)
    val exportResult: StateFlow<String?> = _exportResult.asStateFlow()

    fun exportTo(uri: Uri) = viewModelScope.launch {
        val result = prefs.exportTo(uri)
        _exportResult.value = if (result.isSuccess) "Exported" else "Export failed: ${result.exceptionOrNull()?.message}"
    }

    fun importFrom(uri: Uri) = viewModelScope.launch {
        val result = prefs.importFrom(uri)
        _exportResult.value = if (result.isSuccess) {
            importMessage(result.getOrThrow())
        } else {
            "Import failed: ${result.exceptionOrNull()?.message}"
        }
    }

    private fun importMessage(summary: ImportSummary): String =
        if (summary.droppedDuplicateNames.isEmpty()) {
            "Imported"
        } else {
            "Imported; skipped duplicate profiles: ${summary.droppedDuplicateNames.joinToString(", ")}"
        }

    fun consumeExportResult() { _exportResult.value = null }

    /**
     * Import preview (C30). Decodes + migrates + sanitizes the incoming
     * profile without writing it. UI uses the [ImportSummary] for a diff
     * view and duplicate-profile warning; if the user confirms, the same URI
     * goes through [importFrom] to apply.
     */
    private val _pendingImport = MutableStateFlow<PendingImport?>(null)
    val pendingImport: StateFlow<PendingImport?> = _pendingImport.asStateFlow()

    data class PendingImport(val uri: Uri, val summary: ImportSummary) {
        val decoded: Preferences get() = summary.preferences
    }

    fun beginImportPreview(uri: Uri) = viewModelScope.launch {
        val result = prefs.previewImport(uri)
        if (result.isSuccess) {
            _pendingImport.value = PendingImport(uri, result.getOrThrow())
        } else {
            _exportResult.value = "Import failed: ${result.exceptionOrNull()?.message}"
        }
    }

    fun confirmPendingImport() = viewModelScope.launch {
        val pending = _pendingImport.value ?: return@launch
        _pendingImport.value = null
        importFrom(pending.uri)
    }

    fun cancelPendingImport() {
        _pendingImport.value = null
    }

    /**
     * Favorites toggle (C15). Used by the Presets screen and by upcoming
     * notification-cycle / 4x1 widget command surfaces.
     */
    fun toggleFavorite(key: String) = viewModelScope.launch {
        prefs.update { current ->
            val next = if (key in current.favoritePresetKeys) {
                current.favoritePresetKeys - key
            } else {
                current.favoritePresetKeys + key
            }
            current.copy(favoritePresetKeys = next)
        }
    }

    private fun startService(): Boolean {
        val ctx = getApplication<Application>()
        return LumenServiceStarter.start(ctx, Intent(ctx, LumenService::class.java), tag).started
    }

    private fun stopService() {
        val ctx = getApplication<Application>()
        runCatching { ctx.stopService(Intent(ctx, LumenService::class.java)) }
            .onFailure { Log.w(tag, "Failed to stop LumenService: ${it.message}", it) }
    }

    private fun normalizeEnabledFilterState() = viewModelScope.launch {
        prefs.update { it.normalizedEnabledFilterState() }
    }
}

private fun List<DriverProbe.Probe>.isUnavailable(kind: EngineKindDto): Boolean {
    val engineKind = kind.toEngineKind() ?: return false
    return firstOrNull { it.engine.kind == engineKind }?.available == false
}

private fun OpenLumenViewModel.availableEngineOrAuto(kind: EngineKindDto): EngineKindDto =
    if (probes.value.isUnavailable(kind)) EngineKindDto.Auto else kind

private fun EngineKindDto.toEngineKind(): com.openlumen.engine.EngineKind? = when (this) {
    EngineKindDto.Auto -> null
    EngineKindDto.ColorDisplayManager -> com.openlumen.engine.EngineKind.COLOR_DISPLAY_MANAGER
    EngineKindDto.SurfaceFlinger -> com.openlumen.engine.EngineKind.SURFACE_FLINGER
    EngineKindDto.Kcal -> com.openlumen.engine.EngineKind.KCAL
    EngineKindDto.Overlay -> com.openlumen.engine.EngineKind.OVERLAY
}
