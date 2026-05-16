package com.openlumen.viewmodel

import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.openlumen.engine.DriverProbe
import com.openlumen.prefs.EngineKindDto
import com.openlumen.prefs.Preferences
import com.openlumen.prefs.PreferencesStore
import com.openlumen.prefs.ScheduleModeDto
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
    private val probe: DriverProbe
) : AndroidViewModel(application) {

    val state: StateFlow<Preferences> = prefs.flow
        .stateIn(viewModelScope, SharingStarted.Eagerly, Preferences())

    private val _probes = MutableStateFlow<List<DriverProbe.Probe>>(emptyList())
    val probes: StateFlow<List<DriverProbe.Probe>> = _probes.asStateFlow()

    init {
        refreshProbes()
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.update { it.copy(enabled = enabled) }
            if (enabled) startService() else stopService()
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

    fun refreshProbes() = viewModelScope.launch {
        _probes.value = probe.probeAll(getApplication())
    }

    private fun startService() {
        val ctx = getApplication<Application>()
        val intent = Intent(ctx, LumenService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(intent)
        else ctx.startService(intent)
    }

    private fun stopService() {
        val ctx = getApplication<Application>()
        ctx.stopService(Intent(ctx, LumenService::class.java))
    }
}
