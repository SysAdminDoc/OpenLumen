package com.openlumen.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.openlumen.R
import com.openlumen.engine.EngineKind
import com.openlumen.prefs.EngineKindDto
import com.openlumen.ui.components.LumenButton
import com.openlumen.viewmodel.OpenLumenViewModel

@Composable
fun DriverScreen(vm: OpenLumenViewModel = hiltViewModel()) {
    val prefs by vm.state.collectAsState()
    val probes by vm.probes.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PaddingValues(16.dp)),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(stringResource(R.string.driver_title), style = MaterialTheme.typography.titleMedium)

        val choices = listOf(
            EngineKindDto.Auto to stringResource(R.string.driver_auto),
            EngineKindDto.ColorDisplayManager to stringResource(R.string.driver_color_display),
            EngineKindDto.SurfaceFlinger to stringResource(R.string.driver_surfaceflinger),
            EngineKindDto.Kcal to stringResource(R.string.driver_kcal),
            EngineKindDto.Overlay to stringResource(R.string.driver_overlay)
        )

        choices.forEach { (kind, label) ->
            val availability = kind.toEngineKind()
                ?.let { engineKind -> probes.firstOrNull { it.engine.kind == engineKind }?.available }
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = if (prefs.engine == kind)
                        MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth().clickable { vm.setEngine(kind) }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = prefs.engine == kind,
                        onClick = { vm.setEngine(kind) }
                    )
                    Column(Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                        if (kind != EngineKindDto.Auto && availability != null) {
                            Text(
                                if (availability) "Available" else "Not available on this device",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        LumenButton(onClick = { vm.refreshProbes() }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.driver_refresh))
        }

        Text(
            stringResource(R.string.driver_grant_secure_settings),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "adb shell pm grant com.openlumen android.permission.WRITE_SECURE_SETTINGS",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun EngineKindDto.toEngineKind(): EngineKind? = when (this) {
    EngineKindDto.Auto -> null
    EngineKindDto.ColorDisplayManager -> EngineKind.COLOR_DISPLAY_MANAGER
    EngineKindDto.SurfaceFlinger -> EngineKind.SURFACE_FLINGER
    EngineKindDto.Kcal -> EngineKind.KCAL
    EngineKindDto.Overlay -> EngineKind.OVERLAY
}
