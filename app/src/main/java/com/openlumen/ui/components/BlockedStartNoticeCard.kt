package com.openlumen.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.openlumen.R
import com.openlumen.external.ExternalIntentResult
import com.openlumen.service.BlockedStartNotice

/**
 * What MainActivity hands Home for a refused tile or widget start: the
 * notice, and the activity's own retry, so a retry is the same single
 * visible start the activity makes by itself.
 */
class BlockedStartUi(
    val notice: BlockedStartNotice,
    val onRetry: () -> Unit
)

/**
 * Says on Home that a tile or widget could not start the filter, why, and
 * what to do. It replaces a silent app launch: the switch read on while
 * nothing ran. A live region, so TalkBack reads it when it appears over a
 * Home that was already open.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BlockedStartNoticeCard(
    notice: BlockedStartNotice,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    var settingsError by rememberSaveable(notice.id) { mutableStateOf(false) }
    val needsOverlay = notice.phase == BlockedStartNotice.Phase.NeedsOverlay

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stringResource(R.string.blocked_start_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                stringResource(
                    when (notice.source) {
                        BlockedStartNotice.Source.Tile -> R.string.blocked_start_reason_tile
                        BlockedStartNotice.Source.Widget -> R.string.blocked_start_reason_widget
                        BlockedStartNotice.Source.Other -> R.string.blocked_start_reason_other
                    }
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                stringResource(
                    if (needsOverlay) R.string.blocked_start_needs_overlay
                    else R.string.blocked_start_failed
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            if (settingsError) {
                Text(
                    stringResource(R.string.external_intent_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (needsOverlay) {
                    LumenButton(onClick = {
                        settingsError = openOverlaySettings(ctx) != ExternalIntentResult.Launched
                    }) {
                        Text(stringResource(R.string.perm_overlay_grant))
                    }
                    // Auto on a rooted phone may not need the overlay at
                    // all, so starting anyway stays one tap away.
                    LumenTextButton(onClick = onRetry) {
                        Text(stringResource(R.string.blocked_start_retry))
                    }
                } else {
                    LumenButton(onClick = onRetry) {
                        Text(stringResource(R.string.blocked_start_retry))
                    }
                }
            }
        }
    }
}
