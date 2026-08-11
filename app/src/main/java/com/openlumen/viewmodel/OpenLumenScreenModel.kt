package com.openlumen.viewmodel

import android.net.Uri
import com.openlumen.engine.DriverProbe
import com.openlumen.prefs.EngineKindDto
import com.openlumen.prefs.NamedProfile
import com.openlumen.prefs.Preferences
import com.openlumen.prefs.PreferencesRecovery
import com.openlumen.prefs.ScheduleModeDto
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow

/**
 * State and actions consumed by the production screens.
 *
 * Keeping the screen boundary smaller than the Android/Hilt ViewModel lets
 * screenshot and preview harnesses supply deterministic state without
 * constructing DataStore, sensors, or system activity launchers.
 */
interface OpenLumenScreenModel {
    val state: StateFlow<Preferences>
    val preferenceRecovery: StateFlow<PreferencesRecovery?>
    val probes: StateFlow<List<DriverProbe.Probe>>
    val probesRefreshing: StateFlow<Boolean>
    val probeError: StateFlow<String?>
    val lux: StateFlow<Float>
    val lightSensorAvailable: StateFlow<Boolean>
    val exportResult: StateFlow<String?>
    val pendingImport: StateFlow<OpenLumenViewModel.PendingImport?>

    fun setEnabled(enabled: Boolean)
    fun selectPreset(key: String): Job
    fun restorePreviousPreset(): Job
    fun saveProfileAs(name: String, replaceExisting: Boolean = false): Job
    fun loadProfile(name: String): Job
    fun deleteProfile(name: String): Job
    fun restoreDeletedProfile(profile: NamedProfile): Job
    fun setScheduleMode(mode: ScheduleModeDto): Job
    fun setScheduleTimes(startH: Int, startM: Int, endH: Int, endM: Int): Job
    fun setLocation(lat: Double, lng: Double, solarTimezone: String? = null): Job
    fun setEngine(kind: EngineKindDto): Job
    fun setIntensity(value: Float): Job
    fun setDim(value: Float): Job
    fun setAmoledBlackClamp(enabled: Boolean): Job
    fun setContrast(value: Float): Job
    fun setCustomRgb(r: Float, g: Float, b: Float): Job
    fun setCustomKelvin(kelvin: Int): Job
    fun setGamma(r: Float, g: Float, b: Float): Job
    fun setScheduleOffsets(sunsetMin: Int, sunriseMin: Int): Job
    fun reconcileExactAlarmPermission(): Job
    fun setLightSensor(enabled: Boolean, threshold: Float): Job
    fun setTransitionDuration(durationMs: Long): Job
    fun refreshProbes(): Job
    fun buildDriverReport(): String
    fun exportTo(uri: Uri): Job
    fun exportCorruptPreferencesTo(uri: Uri): Job
    fun resetCorruptPreferences(): Job
    fun importFrom(uri: Uri): Job
    fun consumeExportResult()
    fun beginImportPreview(uri: Uri): Job
    fun confirmPendingImport(): Job
    fun cancelPendingImport()
    fun toggleFavorite(key: String): Job
}
