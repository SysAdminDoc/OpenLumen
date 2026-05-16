package com.openlumen.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.item
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.openlumen.R
import com.openlumen.engine.Presets
import com.openlumen.ui.components.LumenTextButton
import com.openlumen.viewmodel.OpenLumenViewModel

@Composable
fun PresetsScreen(vm: OpenLumenViewModel = hiltViewModel()) {
    val prefs by vm.state.collectAsState()
    val favorites = prefs.favoritePresetKeys.toSet()

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Undo affordance (C14). Shown only when there's something to restore
        // and the previous key resolves to a real preset — guards against a
        // stale `previousPresetKey` after a preset is renamed or removed.
        val previousEntry = prefs.previousPresetKey
            ?.takeIf { it != prefs.activePresetKey }
            ?.let(Presets::byKey)
        if (previousEntry != null) {
            item {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Previous: ${previousEntry.displayName}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        LumenTextButton(onClick = { vm.restorePreviousPreset() }) {
                            Text(stringResource(R.string.preset_restore_previous))
                        }
                    }
                }
            }
        }

        items(Presets.ALL) { entry ->
            val selected = entry.key == prefs.activePresetKey
            val isFavorite = entry.key in favorites
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                                     else MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth().clickable { vm.selectPreset(entry.key) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(
                                color = swatchOf(entry.matrix.r, entry.matrix.g, entry.matrix.b),
                                shape = RoundedCornerShape(6.dp)
                            )
                    )
                    Spacer(Modifier.size(12.dp))
                    Text(
                        text = entry.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.weight(1f)
                    )
                    // Favorite toggle (C15). Independent of selection — a
                    // user can favorite presets they don't currently have
                    // active so the notification/widget cycle (C16/C20) has
                    // a useful list to walk.
                    IconButton(onClick = { vm.toggleFavorite(entry.key) }) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = stringResource(
                                if (isFavorite) R.string.preset_unfavorite
                                else R.string.preset_favorite
                            )
                        )
                    }
                    RadioButton(
                        selected = selected,
                        onClick = { vm.selectPreset(entry.key) }
                    )
                }
            }
        }
    }
}

private fun swatchOf(r: Float, g: Float, b: Float): Color =
    Color(red = r.coerceIn(0f, 1f), green = g.coerceIn(0f, 1f), blue = b.coerceIn(0f, 1f), alpha = 1f)
