package com.openlumen.di

import android.content.Context
import android.content.pm.PackageManager
import com.openlumen.PresetKeyResolver
import com.openlumen.engine.DriverProbe
import com.openlumen.prefs.DirectBootStateStore
import com.openlumen.prefs.PreferencesStore
import com.openlumen.schedule.LightSensorAdapter
import com.openlumen.shizuku.RikkaShizukuGateway
import com.openlumen.shizuku.ShizukuGrant
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
    fun providePrefs(@ApplicationContext ctx: Context): PreferencesStore =
        PreferencesStore(ctx, PresetKeyResolver.knownKeys)

    @Provides @Singleton
    fun provideDirectBootState(@ApplicationContext ctx: Context): DirectBootStateStore =
        DirectBootStateStore(ctx)

    @Provides @Singleton
    fun provideDriverProbe(): DriverProbe = DriverProbe()

    @Provides @Singleton
    fun provideLightSensor(@ApplicationContext ctx: Context): LightSensorAdapter =
        LightSensorAdapter(ctx)

    /** C254. One per process: the gateway registers process-lifetime listeners. */
    @Provides @Singleton
    fun provideShizukuGrant(@ApplicationContext ctx: Context): ShizukuGrant =
        ShizukuGrant(
            gateway = RikkaShizukuGateway(ctx),
            packageName = ctx.packageName,
            secureSettingsGranted = {
                ctx.checkSelfPermission(ShizukuGrant.WRITE_SECURE_SETTINGS) ==
                    PackageManager.PERMISSION_GRANTED
            }
        )
}
