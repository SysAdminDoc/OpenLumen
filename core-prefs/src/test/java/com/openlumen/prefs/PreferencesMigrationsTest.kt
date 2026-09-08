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

    @Test fun `the chain reaches the version the app writes`() {
        // Contiguity alone does not catch bumping CURRENT_SCHEMA_VERSION and
        // forgetting the step: migrate then falls through and returns the blob
        // unchanged, still stamped with the old version.
        assertThat(PreferencesMigrations.steps.maxOf { it.to })
            .isEqualTo(Preferences.CURRENT_SCHEMA_VERSION)
    }

    @Test fun `upgrading closes the automation surface and drops the token`() {
        // C250. Before v3 the exported automation surface accepted any local
        // broadcast, so an upgrading install carries an implicit "anyone may
        // drive this app" posture that was recorded nowhere. Every upgrade has
        // to land closed and tokenless, exactly like a fresh install.
        val migrated = PreferencesMigrations.migrate(
            Preferences(
                schemaVersion = 2,
                automationEnabled = true,
                automationToken = "a".repeat(32)
            )
        )

        assertThat(migrated.automationEnabled).isFalse()
        assertThat(migrated.automationToken).isEmpty()
        assertThat(migrated.schemaVersion).isEqualTo(Preferences.CURRENT_SCHEMA_VERSION)
    }

    @Test fun `a fresh install is not walked back through the automation step`() {
        // Positive control: the step above must fire on the version gap, not
        // on every read, or a user who enables automation loses it on the next
        // launch.
        val current = Preferences(
            schemaVersion = Preferences.CURRENT_SCHEMA_VERSION,
            automationEnabled = true,
            automationToken = "b".repeat(32)
        )

        assertThat(PreferencesMigrations.migrate(current)).isEqualTo(current)
    }

    @Test fun `the driver a v1 blob could still use survives the engine rewrite`() {
        // 1->2 dropped the two root drivers because they were recorded before
        // the probe existed. The rootless one and Auto were always valid and
        // must come through untouched.
        for (engine in listOf(EngineKindDto.ColorDisplayManager, EngineKindDto.Auto)) {
            val migrated = PreferencesMigrations.migrate(
                Preferences(schemaVersion = 1, engine = engine)
            )

            assertThat(migrated.engine).isEqualTo(engine)
        }
    }
}
