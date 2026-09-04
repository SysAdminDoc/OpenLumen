package com.openlumen.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.openlumen.PresetKeyResolver
import com.openlumen.R
import com.openlumen.diagnostics.DriverReport
import com.openlumen.engine.DriverProbe
import com.openlumen.prefs.EngineKindDto
import com.openlumen.prefs.ImportSummary
import com.openlumen.prefs.NamedProfile
import com.openlumen.prefs.Preferences
import com.openlumen.prefs.PreferencesRecovery
import com.openlumen.prefs.PreferencesStore
import com.openlumen.prefs.PresetPackImportSummary
import com.openlumen.prefs.PresetSortOrder
import com.openlumen.prefs.ScheduleModeDto
import com.openlumen.prefs.touchPreset
import com.openlumen.prefs.withFilterEnabled
import com.openlumen.schedule.LightSensorAdapter
import com.openlumen.schedule.isValidSolarLocation
import com.openlumen.service.EngineController
import com.openlumen.service.LumenService
import com.openlumen.service.LumenServiceStarter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import javax.inject.Inject

@HiltViewModel
class OpenLumenViewModel @Inject constructor(
    application: Application,
    private val prefs: PreferencesStore,
    private val probe: DriverProbe,
    private val lightSensor: LightSensorAdapter
) : AndroidViewModel(application), OpenLumenScreenModel {

    private val tag = "OpenLumen/ViewModel"

    override val state: StateFlow<Preferences> = prefs.flow
        .stateIn(viewModelScope, SharingStarted.Eagerly, Preferences())
    override val preferenceRecovery: StateFlow<PreferencesRecovery?> = prefs.recovery

    private val _probes = MutableStateFlow<List<DriverProbe.Probe>>(emptyList())
    override val probes: StateFlow<List<DriverProbe.Probe>> = _probes.asStateFlow()

    private val probeMutex = Mutex()
    private val _probesRefreshing = MutableStateFlow(false)
    override val probesRefreshing: StateFlow<Boolean> = _probesRefreshing.asStateFlow()
    private val _probeError = MutableStateFlow<String?>(null)
    override val probeError: StateFlow<String?> = _probeError.asStateFlow()

    /** Live ambient-light lux reading. -1 means no sensor / not yet emitted. */
    override val lux: StateFlow<Float> = lightSensor.lux()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), -1f)
    override val lightSensorAvailable: StateFlow<Boolean> = lightSensor.availability

    init {
        viewModelScope.launch {
            lightSensor.availability
                .collect { available ->
                    if (!available) {
                        prefs.update { current ->
                            if (current.lightSensorEnabled) {
                                current.copy(lightSensorEnabled = false)
                            } else {
                                current
                            }
                        }
                    }
                }
        }
        refreshProbes()
    }

    override fun setEnabled(enabled: Boolean) {
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

    override fun selectPreset(key: String) = viewModelScope.launch {
        prefs.update {
            if (!PresetKeyResolver.isKnown(key)) {
                it
            } else {
                com.openlumen.prefs.PresetCycle
                    .setActiveKey(it, key, PresetKeyResolver::isKnown)
                    .touchPreset(key)
            }
        }
    }

    /** Restore the previously-active preset (C14). No-op if none recorded. */
    override fun restorePreviousPreset() = viewModelScope.launch {
        prefs.update {
            com.openlumen.prefs.PresetCycle.restorePrevious(it, PresetKeyResolver::isKnown)
        }
    }

    /** Save the current configuration into the named-profile library (C31). */
    override fun saveProfileAs(name: String, replaceExisting: Boolean) = viewModelScope.launch {
        prefs.update { current ->
            com.openlumen.prefs.Profiles.saveCurrentAs(
                current,
                name,
                replaceExisting = replaceExisting
            )
        }
    }

    override fun renameProfile(oldName: String, newName: String) = viewModelScope.launch {
        prefs.update { current ->
            com.openlumen.prefs.Profiles.rename(current, oldName, newName)
        }
    }

    override fun loadProfile(name: String) = viewModelScope.launch {
        prefs.update { current ->
            val loaded = com.openlumen.prefs.Profiles.loadByName(current, name)
            loaded.activePresetKey
                .takeIf(PresetKeyResolver::isKnown)
                ?.let { loaded.touchPreset(it) }
                ?: loaded
        }
    }

    override fun deleteProfile(name: String) = viewModelScope.launch {
        prefs.update { com.openlumen.prefs.Profiles.delete(it, name) }
    }

    override fun restoreDeletedProfile(profile: NamedProfile) = viewModelScope.launch {
        prefs.update { com.openlumen.prefs.Profiles.restoreDeleted(it, profile) }
    }

    override fun setScheduleMode(mode: ScheduleModeDto) = viewModelScope.launch {
        prefs.update { current ->
            if (mode == ScheduleModeDto.Solar &&
                !isValidSolarLocation(current.schedule.latitude, current.schedule.longitude)
            ) {
                current
            } else {
                current.copy(schedule = current.schedule.copy(mode = mode))
            }
        }
    }

    override fun setScheduleTimes(startH: Int, startM: Int, endH: Int, endM: Int) = viewModelScope.launch {
        prefs.update {
            it.copy(schedule = it.schedule.copy(
                startHour = startH, startMinute = startM, endHour = endH, endMinute = endM
            ))
        }
    }

    override fun setLocation(lat: Double, lng: Double, solarTimezone: String?) = viewModelScope.launch {
        if (!isValidSolarLocation(lat, lng)) return@launch
        prefs.update {
            it.copy(
                schedule = it.schedule.copy(
                    latitude = lat,
                    longitude = lng,
                    solarTimezone = solarTimezone
                )
            )
        }
    }

    override fun setEngine(kind: EngineKindDto) = viewModelScope.launch {
        prefs.update { current ->
            // C253: with force-pin on, an unavailable probe result is not
            // grounds to refuse the selection. The user is deliberately
            // overriding a detection they believe is wrong.
            val resolved = if (current.forcePinnedEngine) kind else availableEngineOrAuto(kind)
            current.copy(engine = resolved)
        }
    }

    /**
     * C253 / closed issue #16. Turning this on while Auto is selected would do
     * nothing, so it is only stored; the Driver tab hides the control unless a
     * specific driver is pinned.
     */
    override fun setForcePinnedEngine(force: Boolean) = viewModelScope.launch {
        prefs.update { it.copy(forcePinnedEngine = force) }
    }

    override fun setIntensity(value: Float) = viewModelScope.launch {
        prefs.update { it.copy(presetIntensity = value.coerceIn(0f, 1f)) }
    }

    override fun setDim(value: Float) = viewModelScope.launch {
        prefs.update { it.copy(dim = value.coerceIn(0f, 0.95f)) }
    }

    /** AMOLED true-black clamp (C66). Off by default; safe no-op on LCD. */
    override fun setAmoledBlackClamp(enabled: Boolean) = viewModelScope.launch {
        prefs.update { it.copy(amoledBlackClamp = enabled) }
    }

    /** Contrast multiplier (C64). 1.0 = identity. */
    override fun setContrast(value: Float) = viewModelScope.launch {
        prefs.update {
            it.copy(
                contrast = value.coerceIn(
                    com.openlumen.prefs.Preferences.CONTRAST_MIN,
                    com.openlumen.prefs.Preferences.CONTRAST_MAX
                )
            )
        }
    }

    override fun setCustomRgb(r: Float, g: Float, b: Float) = viewModelScope.launch {
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
    override fun setCustomKelvin(kelvin: Int) = viewModelScope.launch {
        val rgb = com.openlumen.engine.Kelvin.toRgb(kelvin)
        prefs.update {
            it.copy(
                activePresetKey = "custom",
                customMatrix = it.customMatrix.copy(r = rgb.r, g = rgb.g, b = rgb.b)
            )
        }
    }

    override fun setGamma(r: Float, g: Float, b: Float) = viewModelScope.launch {
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

    override fun setScheduleOffsets(sunsetMin: Int, sunriseMin: Int) = viewModelScope.launch {
        prefs.update {
            it.copy(schedule = it.schedule.copy(
                sunsetOffsetMin = sunsetMin.coerceIn(-180, 180),
                sunriseOffsetMin = sunriseMin.coerceIn(-180, 180)
            ))
        }
    }

    /**
     * Lifecycle fallback for exact-alarm permission changes. The system
     * broadcast covers the background case; returning from Settings covers
     * OEMs that omit or delay that broadcast, especially after revocation.
     */
    override fun reconcileExactAlarmPermission() = viewModelScope.launch {
        val current = prefs.flow.first()
        if (!com.openlumen.service.ExactAlarmPermissionReceiver.shouldReconcile(current)) return@launch
        val ctx = getApplication<Application>()
        val result = LumenServiceStarter.start(
            ctx,
            Intent(ctx, LumenService::class.java)
                .setAction(LumenService.ACTION_RECONCILE_EXACT_ALARM),
            tag
        )
        if (!result.started) {
            Log.w(tag, "Exact-alarm permission reconciliation could not start service: ${result.error?.message}")
        }
    }

    override fun setLightSensor(enabled: Boolean, threshold: Float) = viewModelScope.launch {
        if (enabled && !lightSensor.availability.value) return@launch
        prefs.update {
            it.copy(
                lightSensorEnabled = enabled,
                lightSensorLuxThreshold = threshold.coerceAtLeast(0f)
            )
        }
    }

    /**
     * Smooth-transition duration (C23/C24). The DataStore sanitizer
     * clamps the persisted value into 0..TRANSITION_MAX_MS; 0 disables
     * the ramp entirely.
     */
    override fun setTransitionDuration(durationMs: Long) = viewModelScope.launch {
        prefs.update {
            it.copy(transitionDurationMs = durationMs.coerceIn(0L, Preferences.TRANSITION_MAX_MS))
        }
    }

    override fun setAutomationEnabled(enabled: Boolean) = viewModelScope.launch {
        prefs.setAutomationEnabled(enabled)
    }

    override fun regenerateAutomationToken() = viewModelScope.launch {
        prefs.regenerateAutomationToken()
    }

    override fun refreshProbes() = viewModelScope.launch {
        // Keep repeated taps single-flight at the UI boundary. DriverProbe
        // also serializes this generation with service-side resolution.
        if (!probeMutex.tryLock()) return@launch
        _probesRefreshing.value = true
        _probeError.value = null
        try {
            // Invalidate the per-process su availability cache before re-probing:
            // a user who grants Magisk root after first launch should be able to
            // see root-only engines light up without restarting the app.
            val results = probe.probeAll(getApplication(), invalidateCaches = true)
            _probes.value = results
            val current = state.value.engine
            if (
                shouldRevertPinnedEngineToAuto(
                    selected = current,
                    forcePinned = state.value.forcePinnedEngine,
                    probeSaysAvailable = !results.isUnavailable(current)
                )
            ) {
                prefs.update { snapshot ->
                    val revert = snapshot.engine == current &&
                        shouldRevertPinnedEngineToAuto(
                            selected = snapshot.engine,
                            forcePinned = snapshot.forcePinnedEngine,
                            probeSaysAvailable = !results.isUnavailable(snapshot.engine)
                        )
                    if (revert) snapshot.copy(engine = EngineKindDto.Auto) else snapshot
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Log.w(tag, "Driver probe failed", error)
            _probeError.value = getApplication<Application>().getString(R.string.driver_probe_failed)
        } finally {
            _probesRefreshing.value = false
            probeMutex.unlock()
        }
    }

    /**
     * Synchronous snapshot of the human-readable driver report.
     *
     * Tied to roadmap candidate C02. Composition: latest probe results from
     * [_probes], current [state] preferences, plus device + permission info
     * pulled from the Android `Context`. No I/O.
     */
    override fun buildDriverReport(): String =
        DriverReport.build(getApplication(), state.value, _probes.value)

    private val _exportResult = MutableStateFlow<String?>(null)
    override val exportResult: StateFlow<String?> = _exportResult.asStateFlow()

    override fun exportTo(uri: Uri) = viewModelScope.launch {
        val result = prefs.exportTo(uri)
        _exportResult.value = if (result.isSuccess) {
            getString(R.string.backup_exported)
        } else {
            getString(R.string.backup_export_failed, result.errorText())
        }
    }

    override fun exportCorruptPreferencesTo(uri: Uri) = viewModelScope.launch {
        val result = prefs.exportCorruptTo(uri)
        _exportResult.value = if (result.isSuccess) {
            getString(R.string.backup_recovery_exported)
        } else {
            getString(R.string.backup_export_failed, result.errorText())
        }
    }

    override fun resetCorruptPreferences() = viewModelScope.launch {
        val result = prefs.resetCorruptPreferences()
        _exportResult.value = if (result.isSuccess) {
            getString(R.string.backup_recovery_reset)
        } else {
            getString(R.string.backup_export_failed, result.errorText())
        }
    }

    override fun importFrom(uri: Uri) = viewModelScope.launch {
        val result = prefs.importFrom(uri)
        _exportResult.value = if (result.isSuccess) {
            importMessage(result.getOrThrow())
        } else {
            getString(R.string.backup_import_failed, result.errorText())
        }
    }

    private fun importMessage(summary: ImportSummary): String =
        if (summary.droppedDuplicateNames.isEmpty()) {
            getString(R.string.backup_imported)
        } else {
            getString(
                R.string.backup_imported_skipped_duplicates,
                summary.droppedDuplicateNames.joinToString(", ")
            )
        }

    private fun getString(resId: Int, vararg args: Any): String =
        getApplication<Application>().getString(resId, *args)

    /** Human-readable failure text for a failed [Result], never null. */
    private fun Result<*>.errorText(): String =
        exceptionOrNull()?.localizedMessage
            ?: exceptionOrNull()?.javaClass?.simpleName
            ?: getApplication<Application>().getString(R.string.error_unknown)

    override fun consumeExportResult() { _exportResult.value = null }

    /**
     * Import preview (C30). Decodes + migrates + sanitizes the incoming
     * profile without writing it. UI uses the [ImportSummary] for a diff
     * view and duplicate-profile warning; if the user confirms, that exact
     * sanitized snapshot is applied rather than reopening the external URI.
     */
    private val _pendingImport = MutableStateFlow<PendingImport?>(null)
    override val pendingImport: StateFlow<PendingImport?> = _pendingImport.asStateFlow()

    data class PendingImport(val summary: ImportSummary) {
        val decoded: Preferences get() = summary.preferences
    }

    override fun beginImportPreview(uri: Uri) = viewModelScope.launch {
        val result = prefs.previewImport(uri)
        if (result.isSuccess) {
            _pendingImport.value = PendingImport(result.getOrThrow())
        } else {
            _exportResult.value = getString(R.string.backup_import_failed, result.errorText())
        }
    }

    override fun confirmPendingImport() = viewModelScope.launch {
        val pending = _pendingImport.value ?: return@launch
        val result = prefs.applyImport(pending.summary)
        if (result.isSuccess) {
            if (_pendingImport.value == pending) _pendingImport.value = null
            _exportResult.value = importMessage(result.getOrThrow())
        } else {
            _exportResult.value = getString(R.string.backup_import_failed, result.errorText())
        }
    }

    override fun cancelPendingImport() {
        _pendingImport.value = null
    }

    /**
     * Favorites toggle (C15). Used by the Presets screen and by upcoming
     * notification-cycle / 4x1 widget command surfaces.
     */
    override fun toggleFavorite(key: String) = viewModelScope.launch {
        if (!PresetKeyResolver.isKnown(key)) return@launch
        prefs.update { current ->
            val next = if (key in current.favoritePresetKeys) {
                current.favoritePresetKeys - key
            } else {
                current.favoritePresetKeys + key
            }
            current.copy(favoritePresetKeys = next)
        }
    }

    override fun renamePreset(key: String, name: String) = viewModelScope.launch {
        if (!PresetKeyResolver.isKnown(key)) return@launch
        prefs.update { current ->
            val cleanName = name.trim().take(Preferences.MAX_PROFILE_NAME_LENGTH)
            val overrides = if (cleanName.isBlank()) {
                current.presetNameOverrides - key
            } else {
                current.presetNameOverrides + (key to cleanName)
            }
            current.copy(presetNameOverrides = overrides)
        }
    }

    override fun setPresetSortOrder(order: PresetSortOrder) = viewModelScope.launch {
        prefs.update { it.copy(presetSortOrder = order) }
    }

    override fun exportPresetPack(uri: Uri) = viewModelScope.launch {
        val result = prefs.exportPresetPack(uri)
        _exportResult.value = if (result.isSuccess) {
            getString(R.string.backup_preset_pack_exported)
        } else {
            getString(R.string.backup_export_failed, result.errorText())
        }
    }

    private val _pendingPresetPack = MutableStateFlow<PendingPresetPack?>(null)
    override val pendingPresetPack: StateFlow<PendingPresetPack?> = _pendingPresetPack.asStateFlow()

    data class PendingPresetPack(val summary: PresetPackImportSummary)

    override fun beginPresetPackPreview(uri: Uri) = viewModelScope.launch {
        val result = prefs.previewPresetPack(uri)
        if (result.isSuccess) {
            _pendingPresetPack.value = PendingPresetPack(result.getOrThrow())
        } else {
            _exportResult.value = getString(R.string.backup_preset_pack_import_failed, result.errorText())
        }
    }

    override fun confirmPendingPresetPack() = viewModelScope.launch {
        val pending = _pendingPresetPack.value ?: return@launch
        val result = prefs.applyPresetPack(pending.summary)
        if (result.isSuccess) {
            if (_pendingPresetPack.value == pending) _pendingPresetPack.value = null
            val summary = result.getOrThrow()
            _exportResult.value = getString(
                R.string.backup_preset_pack_imported,
                summary.importedProfileNames.size,
                summary.replacedProfileNames.size
            )
        } else {
            _exportResult.value = getString(R.string.backup_preset_pack_import_failed, result.errorText())
        }
    }

    override fun cancelPendingPresetPack() {
        _pendingPresetPack.value = null
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

}

/**
 * Whether a probe result should send the user's pinned driver back to Auto.
 *
 * C292: this used to be `selected != Auto && probe says unavailable`, with no
 * regard for the force-pin override. That override exists because root-hiding
 * setups make `su` detection report no root, and a Magisk prompt still on
 * screen reads the same way — which is exactly the verdict that lands here. So
 * opening the app silently undid the user's selection and hid the switch that
 * had set it, since the Driver tab only offers the override while a driver is
 * pinned.
 *
 * Defers to [EngineController.honourPinnedEngine] rather than restating the
 * rule, because the service asks the same question when it resolves the engine
 * to run and the two answers must not disagree.
 */
internal fun shouldRevertPinnedEngineToAuto(
    selected: EngineKindDto,
    forcePinned: Boolean,
    probeSaysAvailable: Boolean
): Boolean = selected != EngineKindDto.Auto &&
    !EngineController.honourPinnedEngine(
        forcePinned = forcePinned,
        probeSaysAvailable = probeSaysAvailable
    )

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
