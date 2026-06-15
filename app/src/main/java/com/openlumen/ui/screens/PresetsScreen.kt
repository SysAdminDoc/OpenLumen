package com.openlumen.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.openlumen.R
import com.openlumen.engine.Presets
import com.openlumen.presetLabel
import com.openlumen.prefs.Preferences
import com.openlumen.ui.components.LumenTextButton
import com.openlumen.ui.theme.lumenChannelColors
import com.openlumen.viewmodel.OpenLumenViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun PresetsScreen(vm: OpenLumenViewModel = hiltViewModel()) {
    val prefs by vm.state.collectAsStateWithLifecycle()
    val favorites = prefs.favoritePresetKeys.toSet()
    val scope = rememberCoroutineScope()
    val navigator = rememberListDetailPaneScaffoldNavigator<String>()

    BackHandler(navigator.canNavigateBack()) {
        scope.launch { navigator.navigateBack() }
    }

    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        scaffoldState = navigator.scaffoldState,
        listPane = {
            AnimatedPane(modifier = Modifier.preferredWidth(320.dp)) {
                PresetListPane(
                    prefs = prefs,
                    favorites = favorites,
                    onPresetClick = { key ->
                        vm.selectPreset(key)
                        scope.launch {
                            navigator.navigateTo(
                                ListDetailPaneScaffoldRole.Detail,
                                key
                            )
                        }
                    },
                    onFavoriteToggle = vm::toggleFavorite,
                    onRestorePrevious = vm::restorePreviousPreset
                )
            }
        },
        detailPane = {
            AnimatedPane {
                val key = navigator.currentDestination?.contentKey
                val entry = key?.let(Presets::byKey)
                if (entry != null) {
                    PresetDetailPane(
                        entry = entry,
                        isSelected = key == prefs.activePresetKey,
                        isFavorite = key in favorites,
                        onFavoriteToggle = { vm.toggleFavorite(key) }
                    )
                } else {
                    PresetDetailEmpty()
                }
            }
        }
    )
}

@Composable
private fun PresetListPane(
    prefs: Preferences,
    favorites: Set<String>,
    onPresetClick: (String) -> Unit,
    onFavoriteToggle: (String) -> Unit,
    onRestorePrevious: () -> Unit
) {
    LazyColumn(
        contentPadding = topLevelScrollPadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val previousEntry = prefs.previousPresetKey
            ?.takeIf { it != prefs.activePresetKey }
            ?.let(Presets::byKey)
        if (previousEntry != null) {
            item {
                val previousLabel = presetLabel(previousEntry.key, previousEntry.displayName)
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
                            text = stringResource(R.string.preset_previous, previousLabel),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        LumenTextButton(onClick = onRestorePrevious) {
                            Text(stringResource(R.string.preset_restore_previous))
                        }
                    }
                }
            }
        }

        items(Presets.ALL, key = { it.key }) { entry ->
            val selected = entry.key == prefs.activePresetKey
            val isFavorite = entry.key in favorites
            val entryLabel = presetLabel(entry.key, entry.displayName)
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                                     else MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = selected,
                        onClick = { onPresetClick(entry.key) },
                        role = Role.RadioButton
                    )
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
                        text = entryLabel,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(onClick = { onFavoriteToggle(entry.key) }) {
                        Icon(
                            painter = painterResource(
                                if (isFavorite) R.drawable.ic_favorite_filled
                                else R.drawable.ic_favorite_border
                            ),
                            contentDescription = stringResource(
                                if (isFavorite) R.string.preset_unfavorite
                                else R.string.preset_favorite
                            )
                        )
                    }
                    // onClick = null: the Card's selectable() owns the
                    // selection semantics; the favorite IconButton above stays
                    // a separate accessible action.
                    RadioButton(
                        selected = selected,
                        onClick = null
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetDetailPane(
    entry: Presets.Entry,
    isSelected: Boolean,
    isFavorite: Boolean,
    onFavoriteToggle: () -> Unit
) {
    val label = presetLabel(entry.key, entry.displayName)
    val m = entry.matrix

    LazyColumn(
        contentPadding = topLevelScrollPadding(horizontal = 24.dp, top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(
                        color = swatchOf(m.r, m.g, m.b),
                        shape = RoundedCornerShape(12.dp)
                    )
            )
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (isSelected) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        Text(
                            text = stringResource(R.string.preset_detail_active),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
                Spacer(Modifier.size(8.dp))
                IconButton(onClick = onFavoriteToggle) {
                    Icon(
                        painter = painterResource(
                            if (isFavorite) R.drawable.ic_favorite_filled
                            else R.drawable.ic_favorite_border
                        ),
                        contentDescription = stringResource(
                            if (isFavorite) R.string.preset_unfavorite
                            else R.string.preset_favorite
                        )
                    )
                }
            }
        }

        item {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val channels = lumenChannelColors()
                    ChannelRow(stringResource(R.string.channel_red_short), m.r, channels.red)
                    ChannelRow(stringResource(R.string.channel_green_short), m.g, channels.green)
                    ChannelRow(stringResource(R.string.channel_blue_short), m.b, channels.blue)
                }
            }
        }

        if (m.dim > 0f) {
            item {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = stringResource(R.string.preset_detail_dim, (m.dim * 100).toInt()),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    )
                }
            }
        }

        if (m.hasColorMatrix) {
            item {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Text(
                        text = stringResource(R.string.preset_detail_cvd),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelRow(label: String, value: Float, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.width(24.dp)
        )
        Spacer(Modifier.size(8.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .background(color.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(value.coerceIn(0f, 1f))
                    .background(color, RoundedCornerShape(4.dp))
            )
        }
        Spacer(Modifier.size(8.dp))
        Text(
            text = "${(value * 100).toInt()}%",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(40.dp),
            textAlign = TextAlign.End,
            maxLines = 1
        )
    }
}

@Composable
private fun PresetDetailEmpty() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.preset_detail_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun swatchOf(r: Float, g: Float, b: Float): Color =
    Color(red = r.coerceIn(0f, 1f), green = g.coerceIn(0f, 1f), blue = b.coerceIn(0f, 1f), alpha = 1f)
