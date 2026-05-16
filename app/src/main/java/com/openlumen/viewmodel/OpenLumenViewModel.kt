package com.openlumen.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.openlumen.diagnostics.DriverReport
import com.openlumen.engine.DriverProbe
import com.openlumen.prefs.EngineKindDto
import com.openlumen.prefs.Preferences
import com.openlumen.prefs.PreferencesStore
import com.openlumen.prefs.ScheduleModeDto
import com.openlumen.schedule.LightSensorAdapter
import com.openlumen.service.LumenService
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
        refreshProbes()
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.update { it.copy(enabled = enabled) }
            if (enabled) {
                if (!startService()) {
                    prefs.update { it.copy(enabled = false) }
                    _exportResult.value = "Could not start OpenLumen service"
                }
            } else {
                stopService()
            }
        }
    }

    fun selectPreset(key: String) = viewModelScope.launch {
        prefs.update { it.copy(activePresetKey = key) }
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
        prefs.update { it.copy(engine = kind) }
    }

    fun setIntensity(value: Float) = viewModelScope.launch {
        prefs.update { it.copy(presetIntensity = value.coerceIn(0f, 1f)) }
    }

    fun setDim(value: Float) = viewModelScope.launch {
        prefs.update { it.copy(dim = value.coerceIn(0f, 0.95f)) }
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

    fun refreshProbes() = viewModelScope.launch {
        _probes.value = probe.probeAll(getApplication())
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
        _exportResult.value = if (result.isSuccess) "Imported" else "Import failed: ${result.exceptionOrNull()?.message}"
    }

    fun consumeExportResult() { _exportResult.value = null }

    /**
     * Import preview (C30). Decodes + migrates + sanitizes the incoming
     * profile without writing it. UI uses the [Preferences] for a diff view;
     * if the user confirms, the same URI goes through [importFrom] to apply.
     */
    private val _pendingImport = MutableStateFlow<PendingImport?>(null)
    val pendingImport: StateFlow<PendingImport?> = _pendingImport.asStateFlow()

    data class PendingImport(val uri: Uri, val decoded: Preferences)

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
        val intent = Intent(ctx, LumenService::class.java)
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(intent)
            else ctx.startService(intent)
        }.onFailure {
            Log.e(tag, "Failed to start LumenService: ${it.message}", it)
        }.isSuccess
    }

    private fun stopService() {
        val ctx = getApplication<Application>()
        runCatching { ctx.stopService(Intent(ctx, LumenService::class.java)) }
            .onFailure { Log.w(tag, "Failed to stop LumenService: ${it.message}", it) }
    }
}
