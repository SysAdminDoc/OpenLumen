package com.openlumen.engine.engines

import android.provider.Settings
import com.google.common.truth.Truth.assertThat
import com.openlumen.engine.EngineResult
import com.openlumen.engine.Presets
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * C339. `night_display_activated` and its neighbours are persistent system
 * settings, but the record of who set them lived only in an engine instance.
 * A process killed by the OS therefore left a tint behind that the next
 * process could not tell from something the user had chosen, so it declined to
 * touch it and the filter reported itself off with the screen still orange.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SecureSettingsOwnershipRecordTest {

    private val context get() = RuntimeEnvironment.getApplication()
    private val resolver get() = context.contentResolver
    private val record get() = File(context.filesDir, "secure-settings-ownership")

    @Before fun grantSecureSettings() {
        shadowOf(context).grantPermissions(android.Manifest.permission.WRITE_SECURE_SETTINGS)
    }

    private fun secure(key: String, default: Int = MISSING) =
        Settings.Secure.getInt(resolver, key, default)

    /** The user's own state before OpenLumen ever ran. */
    private fun seedUserNightLight(active: Int, temperature: Int) {
        Settings.Secure.putInt(resolver, SecureSettingsEngine.KEY_NIGHT_AUTO_MODE, 0)
        Settings.Secure.putInt(resolver, SecureSettingsEngine.KEY_NIGHT_TEMPERATURE, temperature)
        Settings.Secure.putInt(resolver, SecureSettingsEngine.KEY_NIGHT_ACTIVATED, active)
    }

    @Test fun `applying a tint writes a record and clearing removes it`() = runBlocking {
        seedUserNightLight(active = 0, temperature = 6500)
        val engine = SecureSettingsEngine()

        engine.apply(context, Presets.NIGHT)
        assertThat(record.isFile).isTrue()

        assertThat(engine.clear(context)).isEqualTo(EngineResult.Success)
        assertThat(record.isFile).isFalse()
    }

    @Test fun `a fresh process clears a tint the previous one left behind`() = runBlocking {
        seedUserNightLight(active = 1, temperature = 4000)
        // The process that applied the tint is gone; only the rows and the
        // record survive it.
        SecureSettingsEngine().apply(context, Presets.NIGHT)
        assertThat(secure(SecureSettingsEngine.KEY_NIGHT_TEMPERATURE)).isNotEqualTo(4000)

        val afterRestart = SecureSettingsEngine()
        assertThat(afterRestart.clear(context)).isEqualTo(EngineResult.Success)

        assertThat(secure(SecureSettingsEngine.KEY_NIGHT_ACTIVATED)).isEqualTo(1)
        assertThat(secure(SecureSettingsEngine.KEY_NIGHT_TEMPERATURE)).isEqualTo(4000)
        assertThat(record.isFile).isFalse()
    }

    @Test fun `a fresh process hands the tint back on the next neutral apply`() = runBlocking {
        // The schedule is off when the new process starts, so the first thing
        // it does is apply the identity matrix. That has to release the row.
        seedUserNightLight(active = 0, temperature = 6500)
        SecureSettingsEngine().apply(context, Presets.NIGHT)

        SecureSettingsEngine().apply(context, Presets.OFF)

        assertThat(secure(SecureSettingsEngine.KEY_NIGHT_ACTIVATED)).isEqualTo(0)
        assertThat(secure(SecureSettingsEngine.KEY_NIGHT_TEMPERATURE)).isEqualTo(6500)
    }

    @Test fun `a record that no longer matches the rows restores nothing`() = runBlocking {
        seedUserNightLight(active = 0, temperature = 6500)
        SecureSettingsEngine().apply(context, Presets.NIGHT)

        // The user changed Night Light by hand after the process died. Their
        // choice outranks a record from a session that is no longer running.
        Settings.Secure.putInt(resolver, SecureSettingsEngine.KEY_NIGHT_TEMPERATURE, 3600)
        Settings.Secure.putInt(resolver, SecureSettingsEngine.KEY_NIGHT_ACTIVATED, 1)

        SecureSettingsEngine().clear(context)

        assertThat(secure(SecureSettingsEngine.KEY_NIGHT_ACTIVATED)).isEqualTo(1)
        assertThat(secure(SecureSettingsEngine.KEY_NIGHT_TEMPERATURE)).isEqualTo(3600)
    }

    @Test fun `a corrupt or foreign record is ignored`() = runBlocking {
        seedUserNightLight(active = 1, temperature = 4000)
        record.writeText("99 not a record at all")

        val engine = SecureSettingsEngine()
        assertThat(engine.clear(context)).isEqualTo(EngineResult.Success)

        // Nothing was adopted, so nothing was written over the user's rows.
        assertThat(secure(SecureSettingsEngine.KEY_NIGHT_ACTIVATED)).isEqualTo(1)
        assertThat(secure(SecureSettingsEngine.KEY_NIGHT_TEMPERATURE)).isEqualTo(4000)
    }

    @Test fun `a session that owns nothing leaves no record behind`() = runBlocking {
        seedUserNightLight(active = 0, temperature = 6500)

        SecureSettingsEngine().apply(context, Presets.OFF)

        assertThat(record.isFile).isFalse()
    }

    private companion object {
        const val MISSING = -999
    }
}
