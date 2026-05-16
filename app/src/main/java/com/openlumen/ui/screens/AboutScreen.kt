package com.openlumen.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.hilt.navigation.compose.hiltViewModel
import com.openlumen.BuildConfig
import com.openlumen.CrashLogger
import com.openlumen.R
import com.openlumen.ui.components.LumenButton
import com.openlumen.ui.components.LumenOutlinedButton
import com.openlumen.ui.components.LumenTextButton
import com.openlumen.viewmodel.OpenLumenViewModel

@Composable
fun AboutScreen(vm: OpenLumenViewModel = hiltViewModel()) {
    val ctx = LocalContext.current
    val result by vm.exportResult.collectAsState()
    var showCrashLog by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(vm::exportTo) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(vm::importFrom) }

    LaunchedEffect(result) {
        result?.let {
            Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show()
            vm.consumeExportResult()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PaddingValues(16.dp)),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Text("${stringResource(R.string.about_version)} ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.about_license), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.about_source), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.about_offline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(Modifier.height(16.dp))

        Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Backup", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Save or restore your preferences as a JSON file. Useful for moving " +
                        "between devices or sharing presets.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LumenButton(
                    onClick = { exportLauncher.launch("openlumen-profile-${java.time.LocalDate.now()}.json") },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Export profile") }
                LumenOutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Import profile") }
            }
        }

        Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Diagnostics", style = MaterialTheme.typography.titleMedium)
                Text(
                    "OpenLumen keeps a local crash log in app-private storage. It never " +
                        "leaves the device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LumenOutlinedButton(
                    onClick = { showCrashLog = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("View crash log") }
            }
        }
    }

    if (showCrashLog) {
        val log = remember { CrashLogger.read(ctx) }
        AlertDialog(
            onDismissRequest = { showCrashLog = false },
            title = { Text("Crash log") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        if (log.isBlank()) "No crashes recorded." else log,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                LumenTextButton(onClick = { showCrashLog = false }) { Text("Close") }
            },
            dismissButton = {
                LumenTextButton(onClick = {
                    CrashLogger.clear(ctx)
                    showCrashLog = false
                }) { Text("Clear") }
            }
        )
    }
}
