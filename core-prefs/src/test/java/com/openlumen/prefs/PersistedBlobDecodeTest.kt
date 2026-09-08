package com.openlumen.prefs

import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * C309. Every migration test built a Preferences object, so nothing ever fed
 * the store a real persisted blob. That left the whole decode path unexercised:
 * v0 detection, the corruption quarantine, a future schema version, and the
 * token redaction on export.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PersistedBlobDecodeTest {

    private val context get() = RuntimeEnvironment.getApplication()
    // One instance per test: recovery is state on the store, so reading it
    // from a freshly constructed one always says null and the corruption
    // assertions pass without proving anything.
    private val store by lazy { PreferencesStore(context, setOf("night", "amber")) }

    private fun seed(raw: String) = runBlocking {
        context.dataStore.edit { it[stringPreferencesKey("prefs-v1")] = raw }
    }

    @Test fun `a blob written before schema versions existed is walked through every step`() {
        // The version is read off the raw JSON, not off the decoded object,
        // because kotlinx fills a missing field with the current default.
        // Reading it from the object would silently mark a pre-C29 blob as
        // already current and skip every migration, so the assertions here are
        // the migrations' own effects rather than the stamped number: 1 -> 2
        // rewrites a root engine recorded before the probe existed, and
        // 2 -> 3 closes the automation surface.
        seed(
            """{"activePresetKey":"amber","engine":"SurfaceFlinger",""" +
                """"automationEnabled":true,"automationToken":"${"d".repeat(32)}"}"""
        )

        val loaded = runBlocking { store.flow.first() }

        assertThat(loaded.engine).isEqualTo(EngineKindDto.Auto)
        assertThat(loaded.automationEnabled).isFalse()
        assertThat(loaded.automationToken).isEmpty()
        assertThat(loaded.schemaVersion).isEqualTo(Preferences.CURRENT_SCHEMA_VERSION)
        assertThat(loaded.activePresetKey).isEqualTo("amber")
    }

    @Test fun `a blob that records the current version is not walked back through the steps`() {
        // Positive control for the case above: the steps have to fire on the
        // gap, not on every read, or a user who enables automation loses it on
        // the next launch.
        seed(
            """{"schemaVersion":3,"engine":"Kcal",""" +
                """"automationEnabled":true,"automationToken":"${"e".repeat(32)}"}"""
        )

        val loaded = runBlocking { store.flow.first() }

        assertThat(loaded.engine).isEqualTo(EngineKindDto.Kcal)
        assertThat(loaded.automationEnabled).isTrue()
    }

    @Test fun `an upgrading blob reaches the app with the automation surface closed`() {
        seed(
            """{"schemaVersion":2,"automationEnabled":true,"automationToken":"${"a".repeat(32)}"}"""
        )

        val loaded = runBlocking { store.flow.first() }

        assertThat(loaded.automationEnabled).isFalse()
        assertThat(loaded.automationToken).isEmpty()
    }

    @Test fun `a blob this build cannot parse leaves defaults running and a recovery record`() {
        seed("{ this is not json")

        val loaded = runBlocking { store.flow.first() }

        assertThat(loaded).isEqualTo(Preferences())
        val recovery = store.recovery.value
        assertThat(recovery).isNotNull()
        assertThat(recovery!!.errorType).isNotEmpty()
        assertThat(recovery.rawCharacterCount).isEqualTo("{ this is not json".length)
    }

    @Test fun `a blob from a newer build is left alone rather than rewritten`() {
        // Downgrade path: the app has no step for it, so it must not pretend
        // to migrate and must not throw.
        seed("""{"schemaVersion":99,"activePresetKey":"night"}""")

        val loaded = runBlocking { store.flow.first() }

        assertThat(loaded.schemaVersion).isEqualTo(99)
        assertThat(loaded.activePresetKey).isEqualTo("night")
    }

    @Test fun `an export never carries the automation token`() {
        // A backup is meant to be shared. The token is the only thing between
        // a hostile local app and full control of the filter, so it must not
        // travel with the file.
        val token = "c".repeat(32)
        seed("""{"schemaVersion":3,"automationEnabled":true,"automationToken":"$token"}""")
        val out = ByteArrayOutputStream()
        val uri = Uri.parse("content://test/backup.json")
        shadowOf(context.contentResolver).registerOutputStream(uri, out)

        val result = runBlocking { store.exportTo(uri) }

        assertThat(result.isSuccess).isTrue()
        val body = out.toString(Charsets.UTF_8.name())
        assertThat(body).doesNotContain(token)
        assertThat(body).contains("\"automationEnabled\": false")
    }

    @Test fun `an export still carries the settings it is a backup of`() {
        // Positive control: redaction must remove the token, not the file.
        seed("""{"schemaVersion":3,"activePresetKey":"night","dim":0.25}""")
        val out = ByteArrayOutputStream()
        val uri = Uri.parse("content://test/backup2.json")
        shadowOf(context.contentResolver).registerOutputStream(uri, out)

        runBlocking { store.exportTo(uri) }

        assertThat(out.toString(Charsets.UTF_8.name())).contains("\"activePresetKey\": \"night\"")
    }

    @Test fun `a good blob leaves no recovery record`() {
        // Positive control for the corruption case: the record has to come
        // from the failure, not from every read. Same store instance, so a
        // record appearing here would be this read's doing.
        seed("""{"schemaVersion":3,"activePresetKey":"night"}""")

        val loaded = runBlocking { store.flow.first() }

        assertThat(loaded.activePresetKey).isEqualTo("night")
        assertThat(store.recovery.value).isNull()
    }
}
