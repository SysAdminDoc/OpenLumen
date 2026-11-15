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

/**
 * C287. Each saved package name is handed to a command run through Shizuku,
 * and the list is put back at every restart, so what the store returns has to
 * be well-formed and bounded whatever the blob says. The list belongs to this
 * phone: exports leave it out and imports keep the one already here.
 */
@RunWith(RobolectricTestRunner::class)
class AppSaturationPreferenceTest {

    private val context get() = RuntimeEnvironment.getApplication()
    private val store by lazy { PreferencesStore(context, setOf("night", "amber")) }

    private fun seed(raw: String) = runBlocking {
        context.dataStore.edit { it[stringPreferencesKey("prefs-v1")] = raw }
    }

    @Test fun `a blob saved before per-app saturation decodes with no apps`() {
        seed("""{"schemaVersion":3,"activePresetKey":"night"}""")

        assertThat(runBlocking { store.flow.first() }.appSaturation).isEmpty()
    }

    @Test fun `saved levels come back as they were`() {
        seed("""{"schemaVersion":3,"appSaturation":{"com.example.reader":0,"org.example.chat":40}}""")

        assertThat(runBlocking { store.flow.first() }.appSaturation)
            .containsExactly("com.example.reader", 0, "org.example.chat", 40)
    }

    @Test fun `a hand-edited blob loses malformed names and out-of-range levels`() {
        seed(
            """{"schemaVersion":3,"appSaturation":{""" +
                """"com.example.ok":-20,"org.example.bright":250,""" +
                """"nodots":0,"com.example.app; reboot":0,"1com.example":0,"com..example":0}}"""
        )

        assertThat(runBlocking { store.flow.first() }.appSaturation)
            .containsExactly("com.example.ok", 0, "org.example.bright", Preferences.APP_SATURATION_FULL)
    }

    @Test fun `the list is cut at the cap`() {
        val many = (1..Preferences.APP_SATURATION_MAX_APPS + 5).joinToString(",") { "\"com.example.app$it\":0" }
        seed("""{"schemaVersion":3,"appSaturation":{$many}}""")

        assertThat(runBlocking { store.flow.first() }.appSaturation).hasSize(Preferences.APP_SATURATION_MAX_APPS)
    }

    @Test fun `an export leaves the list out and an import keeps the one already here`() {
        runBlocking {
            store.update { it.copy(dim = 0.3f, appSaturation = mapOf("com.example.reader" to 0)) }
        }
        val out = ByteArrayOutputStream()
        val exportUri = Uri.parse("content://test/saturation-export.json")
        shadowOf(context.contentResolver).registerOutputStream(exportUri, out)
        runBlocking { store.exportTo(exportUri) }
        val exported = out.toString(Charsets.UTF_8.name())
        assertThat(exported).doesNotContain("com.example.reader")
        // Positive control: the backup still carries the settings it is for.
        assertThat(exported).contains("\"dim\": 0.3")

        // A file from another phone that does name apps changes nothing here.
        val foreign = exported.replaceFirst(
            Regex(""""appSaturation": \{\s*\}"""),
            """"appSaturation": {"com.example.other": 0}"""
        )
        assertThat(foreign).isNotEqualTo(exported)
        runBlocking { store.update { it.copy(appSaturation = mapOf("com.example.mine" to 30)) } }
        val importUri = Uri.parse("content://test/saturation-import.json")
        shadowOf(context.contentResolver).registerInputStream(importUri, foreign.byteInputStream(Charsets.UTF_8))
        val result = runBlocking { store.importFrom(importUri) }

        assertThat(result.isSuccess).isTrue()
        assertThat(runBlocking { store.flow.first() }.appSaturation).containsExactly("com.example.mine", 30)
    }
}
