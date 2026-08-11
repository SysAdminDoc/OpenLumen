package com.openlumen.prefs

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test

class PresetPacksTest {

    private val json = Json { encodeDefaults = true }

    @Test fun `pack JSON carries an explicit format marker`() {
        val encoded = json.encodeToString(PresetPack.serializer(), PresetPack())

        assertThat(encoded).contains("\"format\":\"${PresetPack.FORMAT}\"")
    }

    @Test fun `pack merge replaces same-name profiles and leaves runtime state alone`() {
        val existing = Profiles.saveCurrentAs(
            Preferences(enabled = true, activePresetKey = "night"),
            "Evening",
            nowMs = 1L
        )
        val incoming = NamedProfile(
            name = "  evening ",
            snapshot = Profiles.snapshot(Preferences(activePresetKey = "amber")),
            lastUsedAtEpochMs = 42L
        )

        val result = PresetPacks.merge(
            existing,
            PresetPack(
                profiles = listOf(incoming),
                presetNameOverrides = mapOf("night" to "Reading")
            )
        )

        assertThat(result.importedProfileNames).containsExactly("evening")
        assertThat(result.replacedProfileNames).containsExactly("Evening")
        assertThat(result.preferences.enabled).isTrue()
        assertThat(result.preferences.activePresetKey).isEqualTo("night")
        assertThat(result.preferences.savedProfiles.single().name).isEqualTo("evening")
        assertThat(result.preferences.savedProfiles.single().snapshot.activePresetKey)
            .isEqualTo("amber")
        assertThat(result.preferences.presetNameOverrides["night"]).isEqualTo("Reading")
    }

    @Test fun `preset touch records latest timestamp`() {
        val first = Preferences().touchPreset("night", nowMs = 5L)

        val second = first.touchPreset("night", nowMs = 9L)

        assertThat(second.presetUsage).containsEntry("night", 9L)
    }
}
