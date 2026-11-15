package com.openlumen.di

import android.content.Context
import android.content.pm.PackageManager
import com.openlumen.PresetKeyResolver
import com.openlumen.engine.DriverProbe
import com.openlumen.prefs.DirectBootStateStore
import com.openlumen.prefs.PreferencesStore
import com.openlumen.schedule.LightSensorAdapter
import com.openlumen.shizuku.AppSaturation
import com.openlumen.shizuku.RikkaShizukuGateway
import com.openlumen.shizuku.ShizukuGateway
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

    /** C254. Shared by the grant and per-app saturation. */
    @Provides @Singleton
    fun provideShizukuGateway(@ApplicationContext ctx: Context): ShizukuGateway =
        RikkaShizukuGateway(ctx)

    /** C254. One per process: it registers the gateway's process-lifetime listeners. */
    @Provides @Singleton
    fun provideShizukuGrant(@ApplicationContext ctx: Context, gateway: ShizukuGateway): ShizukuGrant =
        ShizukuGrant(
            gateway = gateway,
            packageName = ctx.packageName,
            secureSettingsGranted = {
                ctx.checkSelfPermission(ShizukuGrant.WRITE_SECURE_SETTINGS) ==
                    PackageManager.PERMISSION_GRANTED
            }
        )

    /**
     * C287. The grant is reached lazily: building it registers Shizuku
     * listeners, and a phone with no apps on the list has no reason to.
     */
    @Provides @Singleton
    fun provideAppSaturation(
        @ApplicationContext ctx: Context,
        gateway: ShizukuGateway,
        grant: dagger.Lazy<ShizukuGrant>
    ): AppSaturation = AppSaturation(
        gateway = gateway,
        callerPackage = ctx.packageName,
        shizukuState = { grant.get().refresh() }
    )
}
