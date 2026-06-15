package com.openlumen.ui.components

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.openlumen.R

/**
 * Rationale card shown when the rootless Overlay driver is the only path we'd be able
 * to use but the user hasn't granted SYSTEM_ALERT_WINDOW yet. Tap "Open settings" jumps
 * to the per-app overlay setting screen.
 *
 * When [requiredByActiveEngine] is false the card is suppressed even if overlay
 * permission is missing — for example a root user who pinned SurfaceFlinger /
 * KCAL doesn't need overlay and shouldn't see a permission nag. Default is
 * true so callers that don't know which engine the user picked still get the
 * safety-net card.
 *
 * The `Settings.canDrawOverlays(...)` check is a Binder roundtrip; we cache its
 * result and only re-query on `ON_RESUME` (the user just returned from the
 * system settings screen and may have granted) and `ON_START`. Without this
 * cache, every recomposition of the Home screen — including every slider tick
 * — would issue a fresh Binder call. Tied to roadmap candidate **C168**.
 */
@Composable
fun OverlayPermissionCard(
    modifier: Modifier = Modifier,
    requiredByActiveEngine: Boolean = true
) {
    if (!requiredByActiveEngine) return
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Cache the canDrawOverlays result and refresh only on lifecycle edges.
    var canDrawOverlays by remember {
        mutableStateOf(Settings.canDrawOverlays(ctx))
    }
    DisposableEffect(lifecycleOwner, ctx) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_RESUME) {
                canDrawOverlays = Settings.canDrawOverlays(ctx)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Also re-query immediately when the composable enters composition for
    // the first time, so a screen rotation or navigation back here doesn't
    // wait for the next ON_RESUME tick.
    LaunchedEffect(Unit) {
        canDrawOverlays = Settings.canDrawOverlays(ctx)
    }

    if (canDrawOverlays) return

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stringResource(R.string.perm_overlay_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                stringResource(R.string.perm_overlay_rationale),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            LumenButton(onClick = {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    ("package:" + ctx.packageName).toUri()
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(intent)
            }) {
                Text(stringResource(R.string.perm_overlay_grant))
            }
        }
    }
}
