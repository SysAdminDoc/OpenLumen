package com.openlumen.prefs

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PreferencesMigrationsTest {

    @Test fun `v0 blob is stamped with current schema version`() {
        val v0 = Preferences(schemaVersion = 0, enabled = true, activePresetKey = "amber")

        val migrated = PreferencesMigrations.migrate(v0)

        assertThat(migrated.schemaVersion).isEqualTo(Preferences.CURRENT_SCHEMA_VERSION)
        // Existing fields preserved.
        assertThat(migrated.enabled).isTrue()
        assertThat(migrated.activePresetKey).isEqualTo("amber")
    }

    @Test fun `already-current blob is a no-op`() {
        val current = Preferences(schemaVersion = Preferences.CURRENT_SCHEMA_VERSION)

        val migrated = PreferencesMigrations.migrate(current)

        assertThat(migrated).isSameInstanceAs(current)
    }

    @Test fun `v1 root driver selection resets to auto`() {
        val surfaceFlinger = Preferences(
            schemaVersion = 1,
            engine = EngineKindDto.SurfaceFlinger
        )
        val kcal = Preferences(
            schemaVersion = 1,
            engine = EngineKindDto.Kcal
        )

        assertThat(PreferencesMigrations.migrate(surfaceFlinger).engine).isEqualTo(EngineKindDto.Auto)
        assertThat(PreferencesMigrations.migrate(kcal).engine).isEqualTo(EngineKindDto.Auto)
    }

    @Test fun `v1 rootless driver selection is preserved`() {
        val overlay = Preferences(
            schemaVersion = 1,
            engine = EngineKindDto.Overlay
        )

        assertThat(PreferencesMigrations.migrate(overlay).engine).isEqualTo(EngineKindDto.Overlay)
    }

    @Test fun `future-version blob is returned unchanged`() {
        // Simulates a downgrade scenario: a newer build wrote schemaVersion = 99,
        // an older build is now reading it. We don't know how to migrate forward
        // so we leave the blob as-is and rely on sanitize() downstream.
        val future = Preferences(schemaVersion = 99, activePresetKey = "night")

        val migrated = PreferencesMigrations.migrate(future)

        assertThat(migrated.schemaVersion).isEqualTo(99)
        assertThat(migrated.activePresetKey).isEqualTo("night")
    }

    @Test fun `migration steps form a consecutive chain from version 0`() {
        // Guards against accidentally leaving a gap in the migration list,
        // e.g. defining 0->1 and 2->3 without 1->2.
        val sorted = PreferencesMigrations.steps.sortedBy { it.from }
        sorted.forEachIndexed { index, step ->
            assertThat(step.from).isEqualTo(index)
            assertThat(step.to).isEqualTo(index + 1)
        }
    }
}
