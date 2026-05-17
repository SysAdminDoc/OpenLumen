package com.openlumen.di

import android.content.Context
import com.openlumen.engine.DriverProbe
import com.openlumen.prefs.DirectBootStateStore
import com.openlumen.prefs.PreferencesStore
import com.openlumen.schedule.LightSensorAdapter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun providePrefs(@ApplicationContext ctx: Context): PreferencesStore = PreferencesStore(ctx)

    @Provides @Singleton
    fun provideDirectBootState(@ApplicationContext ctx: Context): DirectBootStateStore =
        DirectBootStateStore(ctx)

    @Provides @Singleton
    fun provideDriverProbe(): DriverProbe = DriverProbe()

    @Provides @Singleton
    fun provideLightSensor(@ApplicationContext ctx: Context): LightSensorAdapter =
        LightSensorAdapter(ctx)
}
