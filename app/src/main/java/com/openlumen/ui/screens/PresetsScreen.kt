package com.openlumen.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.ProgressBarRangeInfo
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.openlumen.R
import com.openlumen.engine.DriverProbe
import com.openlumen.engine.EngineCapability
import com.openlumen.engine.unsupportedBy
import com.openlumen.engine.Presets
import com.openlumen.engine.LumenMatrix
import com.openlumen.presetLabel
import com.openlumen.prefs.MatrixDto
import com.openlumen.prefs.NamedProfile
import com.openlumen.prefs.Preferences
import com.openlumen.prefs.PresetSortOrder
import com.openlumen.ui.components.LumenFilterChip
import com.openlumen.ui.components.LumenTextButton
import com.openlumen.ui.theme.lumenChannelColors
import com.openlumen.viewmodel.OpenLumenScreenModel
import com.openlumen.viewmodel.OpenLumenViewModel
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun PresetsScreen(
    vm: OpenLumenScreenModel = hiltViewModel<OpenLumenViewModel>(),
    enableSystemActions: Boolean = true
) {
    val prefs by vm.state.collectAsStateWithLifecycle()
    val probes by vm.probes.collectAsStateWithLifecycle()
    val driverCapabilities = DriverProbe.activeCapabilities(
        probes = probes,
        pinned = prefs.engine.toEngineKind(),
        forcePinned = prefs.forcePinnedEngine
    )
    val ctx = LocalContext.current
    val nameResetMessage = stringResource(R.string.preset_name_reset)
    val favorites = prefs.favoritePresetKeys.toSet()
    val scope = rememberCoroutineScope()
    val navigator = rememberListDetailPaneScaffoldNavigator<String>()
    var renamePresetKey by rememberSaveable { mutableStateOf<String?>(null) }
    var renamePresetValue by rememberSaveable { mutableStateOf("") }

    if (enableSystemActions) {
        BackHandler(navigator.canNavigateBack()) {
            scope.launch { navigator.navigateBack() }
        }
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
                        when {
                            key.startsWith(BUILTIN_DESTINATION_PREFIX) ->
                                vm.selectPreset(key.removePrefix(BUILTIN_DESTINATION_PREFIX))
                            key.startsWith(PROFILE_DESTINATION_PREFIX) ->
                                vm.loadProfile(key.removePrefix(PROFILE_DESTINATION_PREFIX))
                        }
                        scope.launch {
                            navigator.navigateTo(
                                ListDetailPaneScaffoldRole.Detail,
                                key
                            )
                        }
                    },
                    onFavoriteToggle = vm::toggleFavorite,
                    onRestorePrevious = vm::restorePreviousPreset,
                    onRenamePreset = { key ->
                        renamePresetKey = key
                        renamePresetValue = prefs.presetNameOverrides[key].orEmpty()
                    },
                    onSortOrderChange = vm::setPresetSortOrder
                )
            }
        },
        detailPane = {
            AnimatedPane {
                val destination = navigator.currentDestination?.contentKey
                val builtinKey = destination
                    ?.takeIf { it.startsWith(BUILTIN_DESTINATION_PREFIX) }
                    ?.removePrefix(BUILTIN_DESTINATION_PREFIX)
                val entry = builtinKey?.let(Presets::byKey)
                if (entry != null) {
                    val selectedKey = checkNotNull(builtinKey)
                    PresetDetailPane(
                        entry = entry,
                        isSelected = selectedKey == prefs.activePresetKey,
                        isFavorite = selectedKey in favorites,
                        onFavoriteToggle = { vm.toggleFavorite(selectedKey) },
                        labelOverride = prefs.presetNameOverrides[selectedKey],
                        driverCapabilities = driverCapabilities
                    )
                } else if (destination?.startsWith(PROFILE_DESTINATION_PREFIX) == true) {
                    val name = destination.removePrefix(PROFILE_DESTINATION_PREFIX)
                    prefs.savedProfiles.firstOrNull { it.name == name }?.let { profile ->
                        ProfileDetailPane(profile)
                    } ?: PresetDetailEmpty()
                } else {
                    PresetDetailEmpty()
                }
            }
        }
    )

    renamePresetKey?.let { key ->
        AlertDialog(
            onDismissRequest = { renamePresetKey = null },
            shape = MaterialTheme.shapes.large,
            title = { Text(stringResource(R.string.preset_rename_title)) },
            text = {
                OutlinedTextField(
                    value = renamePresetValue,
                    onValueChange = {
                        renamePresetValue = it.take(Preferences.MAX_PROFILE_NAME_LENGTH)
                    },
                    label = { Text(stringResource(R.string.preset_rename_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                LumenTextButton(
                    onClick = {
                        vm.renamePreset(key, renamePresetValue)
                        renamePresetKey = null
                    },
                    enabled = renamePresetValue.trim().isNotEmpty()
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                // FlowRow, not Row: AlertDialog flows its own buttons onto a
                // second line when they do not fit, and a plain Row inside the
                // slot defeats that, so Reset and Cancel were pushed off the
                // edge together at a large font scale.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (prefs.presetNameOverrides.containsKey(key)) {
                        LumenTextButton(
                            onClick = {
                                vm.renamePreset(key, "")
                                renamePresetKey = null
                                Toast.makeText(ctx, nameResetMessage, Toast.LENGTH_SHORT).show()
                            }
                        ) { Text(stringResource(R.string.preset_rename_reset)) }
                    }
                    LumenTextButton(onClick = { renamePresetKey = null }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            }
        )
    }
}

@Composable
private fun PresetListPane(
    prefs: Preferences,
    favorites: Set<String>,
    onPresetClick: (String) -> Unit,
    onFavoriteToggle: (String) -> Unit,
    onRestorePrevious: () -> Unit,
    onRenamePreset: (String) -> Unit,
    onSortOrderChange: (PresetSortOrder) -> Unit
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
                val previousLabel = presetLabel(
                    previousEntry.key,
                    previousEntry.displayName,
                    prefs.presetNameOverrides[previousEntry.key]
                )
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

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.presets_sort_label),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f)
                )
                LumenFilterChip(
                    selected = prefs.presetSortOrder == PresetSortOrder.Alphabetical,
                    onClick = { onSortOrderChange(PresetSortOrder.Alphabetical) },
                    label = { Text(stringResource(R.string.presets_sort_alphabetical)) }
                )
                LumenFilterChip(
                    selected = prefs.presetSortOrder == PresetSortOrder.Recent,
                    onClick = { onSortOrderChange(PresetSortOrder.Recent) },
                    label = { Text(stringResource(R.string.presets_sort_recent)) }
                )
            }
        }

        val sortedEntries = when (prefs.presetSortOrder) {
            PresetSortOrder.Alphabetical -> Presets.ALL.sortedBy {
                (prefs.presetNameOverrides[it.key] ?: it.displayName)
                    .lowercase(Locale.ROOT)
            }
            PresetSortOrder.Recent -> Presets.ALL.sortedWith(
                compareByDescending<Presets.Entry> { prefs.presetUsage[it.key] ?: 0L }
                    .thenBy {
                        (prefs.presetNameOverrides[it.key] ?: it.displayName)
                            .lowercase(Locale.ROOT)
                    }
            )
        }

        items(sortedEntries, key = { "$BUILTIN_DESTINATION_PREFIX${it.key}" }) { entry ->
            val selected = entry.key == prefs.activePresetKey
            val isFavorite = entry.key in favorites
            val entryLabel = presetLabel(
                entry.key,
                entry.displayName,
                prefs.presetNameOverrides[entry.key]
            )
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
                        onClick = { onPresetClick("$BUILTIN_DESTINATION_PREFIX${entry.key}") },
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
                    LumenTextButton(onClick = { onRenamePreset(entry.key) }) {
                        Text(
                            stringResource(R.string.preset_rename_action),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
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

        if (prefs.savedProfiles.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.presets_saved_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            val sortedProfiles = when (prefs.presetSortOrder) {
                PresetSortOrder.Alphabetical -> prefs.savedProfiles.sortedBy {
                    it.name.lowercase(Locale.ROOT)
                }
                PresetSortOrder.Recent -> prefs.savedProfiles.sortedWith(
                    compareByDescending<NamedProfile> { it.lastUsedAtEpochMs }
                        .thenBy { it.name.lowercase(Locale.ROOT) }
                )
            }
            items(sortedProfiles, key = { "$PROFILE_DESTINATION_PREFIX${it.name}" }) { profile ->
                val matrix = profileMatrix(profile)
                val loadProfileLabel = stringResource(R.string.about_profiles_load)
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        // Not selectable: a saved profile is never in a
                        // selected state, so every row announced "not
                        // selected, radio button" while its actual action is
                        // to load it. onClickLabel is what TalkBack reads as
                        // the action.
                        .clickable(
                            onClickLabel = loadProfileLabel,
                            role = Role.Button,
                            onClick = {
                                onPresetClick("$PROFILE_DESTINATION_PREFIX${profile.name}")
                            }
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
                                    color = swatchOf(matrix.r, matrix.g, matrix.b),
                                    shape = RoundedCornerShape(6.dp)
                                )
                        )
                        Spacer(Modifier.size(12.dp))
                        Text(
                            text = profile.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            stringResource(R.string.preset_saved_badge),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

}

private const val BUILTIN_DESTINATION_PREFIX = "builtin:"
private const val PROFILE_DESTINATION_PREFIX = "profile:"

@Composable
private fun PresetDetailPane(
    entry: Presets.Entry,
    isSelected: Boolean,
    isFavorite: Boolean,
    onFavoriteToggle: () -> Unit,
    labelOverride: String? = null,
    /** Null until a driver is resolved; no note is shown while it is unknown. */
    driverCapabilities: Set<EngineCapability>? = null
) {
    val label = presetLabel(entry.key, entry.displayName, labelOverride)
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
                    // The visible label is a single letter. The name a screen
                    // reader needs is the full one, and those strings already
                    // existed for the Home sliders.
                    ChannelRow(
                        stringResource(R.string.channel_red_short),
                        stringResource(R.string.home_rgb_red_name),
                        m.r,
                        channels.red
                    )
                    ChannelRow(
                        stringResource(R.string.channel_green_short),
                        stringResource(R.string.home_rgb_green_name),
                        m.g,
                        channels.green
                    )
                    ChannelRow(
                        stringResource(R.string.channel_blue_short),
                        stringResource(R.string.home_rgb_blue_name),
                        m.b,
                        channels.blue
                    )
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

        // C282: presets are written against the compositor. Anything less
        // capable approximates them, and the grayscale preset in particular
        // used to come out as a flat darkening with nothing saying so.
        val unsupported = driverCapabilities?.let { m.unsupportedBy(it) }.orEmpty()
        if (unsupported.isNotEmpty()) {
            item {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = stringResource(
                            R.string.preset_detail_approximated,
                            approximationReason(unsupported)
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Plain-language list of what the active driver will not reproduce. Ordered so
 * the most visible loss reads first.
 */
@Composable
private fun approximationReason(missing: Set<EngineCapability>): String = listOfNotNull(
    stringResource(R.string.preset_capability_color_matrix)
        .takeIf { EngineCapability.COLOR_MATRIX in missing },
    stringResource(R.string.preset_capability_system_correction)
        .takeIf { EngineCapability.SYSTEM_COLOR_CORRECTION in missing },
    stringResource(R.string.preset_capability_dim)
        .takeIf { EngineCapability.SUB_MINIMUM_DIM in missing },
    stringResource(R.string.preset_capability_gamma)
        .takeIf { EngineCapability.PER_CHANNEL_GAMMA in missing }
).joinToString(stringResource(R.string.preset_capability_separator))

@Composable
private fun ProfileDetailPane(profile: NamedProfile) {
    val matrix = profileMatrix(profile)
    LazyColumn(
        contentPadding = topLevelScrollPadding(horizontal = 24.dp, top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                profile.name,
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(
                        color = swatchOf(matrix.r, matrix.g, matrix.b),
                        shape = RoundedCornerShape(12.dp)
                    )
            )
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
                    ChannelRow(
                        stringResource(R.string.channel_red_short),
                        stringResource(R.string.home_rgb_red_name),
                        matrix.r,
                        channels.red
                    )
                    ChannelRow(
                        stringResource(R.string.channel_green_short),
                        stringResource(R.string.home_rgb_green_name),
                        matrix.g,
                        channels.green
                    )
                    ChannelRow(
                        stringResource(R.string.channel_blue_short),
                        stringResource(R.string.home_rgb_blue_name),
                        matrix.b,
                        channels.blue
                    )
                }
            }
        }
        item {
            Text(
                stringResource(R.string.preset_saved_detail),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun profileMatrix(profile: NamedProfile): LumenMatrix {
    Presets.byKey(profile.snapshot.activePresetKey)?.let { return it.matrix }
    val m: MatrixDto = profile.snapshot.customMatrix
    return LumenMatrix(
        r = m.r,
        g = m.g,
        b = m.b,
        biasR = m.biasR,
        biasG = m.biasG,
        biasB = m.biasB,
        dim = m.dim,
        gammaR = m.gammaR,
        gammaG = m.gammaG,
        gammaB = m.gammaB,
        hasColorMatrix = m.hasColorMatrix,
        matrixRr = m.matrixRr,
        matrixRg = m.matrixRg,
        matrixRb = m.matrixRb,
        matrixGr = m.matrixGr,
        matrixGg = m.matrixGg,
        matrixGb = m.matrixGb,
        matrixBr = m.matrixBr,
        matrixBg = m.matrixBg,
        matrixBb = m.matrixBb
    )
}

@Composable
private fun ChannelRow(
    label: String,
    accessibleName: String,
    value: Float,
    color: Color
) {
    val percent = (value.coerceIn(0f, 1f) * 100).toInt()
    val meterState = stringResource(R.string.home_percent_value, percent)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        // One row, one node. Without merging, the letter, the bar and the
        // percentage are three separate stops, so a screen reader reads
        // "R", then "R, 42 percent", then "42 percent".
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = accessibleName
            stateDescription = meterState
        }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            // widthIn, not width: at a large font scale a single letter needs
            // more than 24 dp and was being cut in half.
            modifier = Modifier.widthIn(min = 24.dp)
        )
        Spacer(Modifier.size(8.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                // Two nested Boxes are a picture as far as accessibility is
                // concerned. The row above carries the name and the value; this
                // is what makes it a progress bar rather than a label.
                .semantics {
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        current = value.coerceIn(0f, 1f),
                        range = 0f..1f
                    )
                }
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
            text = meterState,
            style = MaterialTheme.typography.bodyMedium,
            // "100%" does not fit 40 dp much past the default scale, and with
            // maxLines = 1 and no ellipsis it simply lost a character.
            modifier = Modifier.widthIn(min = 40.dp),
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PresetDetailEmpty() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                text = stringResource(R.string.preset_detail_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            )
        }
    }
}

private fun swatchOf(r: Float, g: Float, b: Float): Color =
    Color(red = r.coerceIn(0f, 1f), green = g.coerceIn(0f, 1f), blue = b.coerceIn(0f, 1f), alpha = 1f)
