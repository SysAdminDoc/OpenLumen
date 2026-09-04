package com.openlumen.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.openlumen.R

/**
 * What a screen shows while its stored preferences are still on their way.
 *
 * DataStore is a file read, so the first frame after a cold start used to draw
 * `Preferences()`: the master switch read Off and every slider sat at its
 * default, then the whole screen jumped to the user's values. Off is not a
 * neutral thing to show for a filter, and a screen reader announced it.
 */
@Composable
fun PreferencesPlaceholder(modifier: Modifier = Modifier) {
    val label = stringResource(R.string.loading_preferences)
    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}
