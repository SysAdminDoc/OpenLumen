package com.openlumen.prefs

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test

class PreferencesSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        allowSpecialFloatingPointValues = true
        encodeDefaults = true
        prettyPrint = true
    }

    @Test fun `default preferences encode without NaN coordinates`() {
        val encoded = json.encodeToString(Preferences())

        assertThat(encoded).doesNotContain("NaN")
        assertThat(encoded).contains("\"latitude\": null")
        assertThat(encoded).contains("\"longitude\": null")
    }

    @Test fun `default preferences round-trip through JSON`() {
        val encoded = json.encodeToString(Preferences())
        val decoded = json.decodeFromString(Preferences.serializer(), encoded)

        assertThat(decoded.schedule.latitude).isNull()
        assertThat(decoded.schedule.longitude).isNull()
        assertThat(decoded.enabled).isFalse()
    }

    @Test fun `defaults include the canonical favorites list`() {
        val defaults = Preferences()

        assertThat(defaults.favoritePresetKeys).containsExactly("night", "amber", "red", "deep").inOrder()
        assertThat(defaults.schemaVersion).isEqualTo(Preferences.CURRENT_SCHEMA_VERSION)
    }
}

