package com.openlumen.widget

import android.content.Context
import com.openlumen.prefs.PreferencesStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

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
