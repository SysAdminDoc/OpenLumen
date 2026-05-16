package com.openlumen.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.openlumen.R
import com.openlumen.engine.Presets
import com.openlumen.viewmodel.OpenLumenViewModel

@Composable
fun HomeScreen(vm: OpenLumenViewModel = hiltViewModel()) {
    val prefs by vm.state.collectAsState()
    val preset = Presets.byKey(prefs.activePresetKey)

    Column(
        modifier = Modifier.fillMaxSize().padding(PaddingValues(16.dp)),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth()
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
                        text = preset?.displayName ?: prefs.activePresetKey,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = prefs.enabled,
                    onCheckedChange = vm::setEnabled
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        Card(
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.home_intensity), style = MaterialTheme.typography.titleMedium)
                var intensity by remember { mutableStateOf(0.5f) }
                Slider(
                    value = intensity,
                    onValueChange = { intensity = it },
                    valueRange = 0f..1f
                )

                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.home_dim), style = MaterialTheme.typography.titleMedium)
                var dim by remember { mutableStateOf(0f) }
                Slider(
                    value = dim,
                    onValueChange = { dim = it },
                    valueRange = 0f..0.95f
                )
            }
        }
    }
}
