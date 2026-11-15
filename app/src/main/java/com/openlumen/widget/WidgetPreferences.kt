package com.openlumen.widget

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import com.openlumen.prefs.Preferences
import com.openlumen.prefs.PreferencesStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

internal fun widgetPreferencesStore(context: Context): PreferencesStore =
    EntryPointAccessors.fromApplication(
        context.applicationContext,
        WidgetPreferencesEntryPoint::class.java
    ).preferencesStore()

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface WidgetPreferencesEntryPoint {
    fun preferencesStore(): PreferencesStore
}

/** What a widget draws before the store answers, bounded so a stuck read cannot hold the launcher. */
internal suspend fun readWidgetPreferences(store: PreferencesStore): Preferences =
    runCatching {
        withTimeoutOrNull(WIDGET_READ_TIMEOUT_MS) { store.flow.first() }
    }.getOrNull() ?: Preferences()

/**
 * The preferences a widget's content draws from, kept current for as long as
 * its Glance session runs.
 *
 * Read inside the composition, not in provideGlance. An update to a widget
 * whose session is still running only recomposes the content it already has,
 * so a value captured before provideContent stayed whatever it was when the
 * session started: turning the filter off from the widget left it saying On.
 */
@Composable
internal fun rememberWidgetPreferences(
    preferences: Flow<Preferences>,
    initial: Preferences
): State<Preferences> {
    val live = remember(preferences) {
        preferences.catch { Log.w(TAG, "Widget preferences stopped updating: ${it.message}") }
    }
    return live.collectAsState(initial)
}

private const val TAG = "OpenLumen/Widget"
