package com.openlumen.prefs

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProfilesTest {

    @Test fun `snapshot captures user-tunable fields and excludes runtime fields`() {
        val p = Preferences(
            enabled = true,
            firstRunComplete = true,
            previousPresetKey = "amber",
            schemaVersion = 5,
            activePresetKey = "night",
            dim = 0.5f,
            transitionDurationMs = 5L * 60_000,
            savedProfiles = listOf(NamedProfile("x", Profiles.snapshot(Preferences())))
        )

        val snap = Profiles.snapshot(p)

        assertThat(snap.activePresetKey).isEqualTo("night")
        assertThat(snap.dim).isEqualTo(0.5f)
        assertThat(snap.transitionDurationMs).isEqualTo(5L * 60_000)
        // Sanity: the snapshot's data class does NOT carry enabled or
        // savedProfiles — the type system enforces this; the test just
        // documents the intent so a future field-add to the snapshot
        // class triggers a review.
    }

    @Test fun `apply moves current preset into previousPresetKey only if it differs`() {
        val current = Preferences(activePresetKey = "night", previousPresetKey = "amber")
        val snap = Profiles.snapshot(Preferences(activePresetKey = "red"))

        val applied = Profiles.apply(current, snap)

        assertThat(applied.activePresetKey).isEqualTo("red")
        assertThat(applied.previousPresetKey).isEqualTo("night")
    }

    @Test fun `apply preserves previous when snapshot has the same active preset`() {
        val current = Preferences(activePresetKey = "night", previousPresetKey = "amber")
        val snap = Profiles.snapshot(current)

        val applied = Profiles.apply(current, snap)

        assertThat(applied.activePresetKey).isEqualTo("night")
        assertThat(applied.previousPresetKey).isEqualTo("amber")
    }

    @Test fun `apply preserves runtime fields (enabled, savedProfiles, schemaVersion)`() {
        val mySaved = NamedProfile("mine", Profiles.snapshot(Preferences()))
        val current = Preferences(
            enabled = true,
            firstRunComplete = true,
            schemaVersion = 7,
            savedProfiles = listOf(mySaved)
        )
        val snap = Profiles.snapshot(Preferences(activePresetKey = "amber"))

        val applied = Profiles.apply(current, snap)

        assertThat(applied.enabled).isTrue()
        assertThat(applied.firstRunComplete).isTrue()
        assertThat(applied.schemaVersion).isEqualTo(7)
        assertThat(applied.savedProfiles).containsExactly(mySaved)
    }

    @Test fun `saveCurrentAs rejects blank names`() {
        val p = Preferences()

        val saved = Profiles.saveCurrentAs(p, "   ")

        assertThat(saved.savedProfiles).isEmpty()
    }

    @Test fun `saveCurrentAs trims and truncates names`() {
        val p = Preferences()
        val longName = "a".repeat(200)

        val saved = Profiles.saveCurrentAs(p, "  $longName  ")

        assertThat(saved.savedProfiles).hasSize(1)
        assertThat(saved.savedProfiles[0].name).hasLength(Preferences.MAX_PROFILE_NAME_LENGTH)
    }

    @Test fun `saveCurrentAs overwrites an existing same-named profile`() {
        var p = Preferences(activePresetKey = "night")
        p = Profiles.saveCurrentAs(p, "evening")
        p = p.copy(activePresetKey = "amber")
        p = Profiles.saveCurrentAs(p, "evening")

        assertThat(p.savedProfiles).hasSize(1)
        assertThat(p.savedProfiles[0].snapshot.activePresetKey).isEqualTo("amber")
    }

    @Test fun `loadByName flips active preset and stamps previousPresetKey`() {
        var p = Preferences(activePresetKey = "night")
        p = Profiles.saveCurrentAs(p, "evening")
        p = p.copy(activePresetKey = "amber")

        val loaded = Profiles.loadByName(p, "evening")

        assertThat(loaded.activePresetKey).isEqualTo("night")
        assertThat(loaded.previousPresetKey).isEqualTo("amber")
    }

    @Test fun `loadByName is a no-op for an unknown name`() {
        val p = Preferences(activePresetKey = "amber")

        val loaded = Profiles.loadByName(p, "does-not-exist")

        assertThat(loaded).isEqualTo(p)
    }

    @Test fun `delete drops the named profile, leaves others alone`() {
        var p = Preferences()
        p = Profiles.saveCurrentAs(p, "a")
        p = Profiles.saveCurrentAs(p, "b")

        val after = Profiles.delete(p, "a")

        assertThat(after.savedProfiles.map { it.name }).containsExactly("b")
    }
}
