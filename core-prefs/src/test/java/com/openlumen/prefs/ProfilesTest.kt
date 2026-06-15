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

    @Test fun `restoreDeleted reinstates deleted profile without duplicating names`() {
        var p = Preferences(activePresetKey = "night")
        p = Profiles.saveCurrentAs(p, "evening")
        val deleted = p.savedProfiles.single()

        val afterDelete = Profiles.delete(p, "evening")
        val restored = Profiles.restoreDeleted(afterDelete, deleted)
        val restoredAgain = Profiles.restoreDeleted(restored, deleted)

        assertThat(restored.savedProfiles).containsExactly(deleted)
        assertThat(restoredAgain.savedProfiles).containsExactly(deleted)
    }

    @Test fun `import duplicate summary reports profile names dropped by last-write-wins`() {
        val first = NamedProfile("evening", Profiles.snapshot(Preferences(activePresetKey = "night")))
        val second = NamedProfile("  evening  ", Profiles.snapshot(Preferences(activePresetKey = "amber")))
        val third = NamedProfile("morning", Profiles.snapshot(Preferences(activePresetKey = "red")))

        val dropped = droppedDuplicateProfileNames(listOf(first, second, third))

        assertThat(dropped).containsExactly("evening")
    }

    @Test fun `saveCurrentAs past MAX_PROFILES drops the oldest entry and keeps the new one`() {
        // Regression: the cap is enforced via `takeLast(MAX_PROFILES)` so the
        // newest entry is always preserved and the head of the list is the
        // one that gets dropped. Without test coverage, a future refactor
        // could silently switch to `take(MAX_PROFILES)` and start dropping
        // the new profile instead of the oldest — a destructive UX bug.
        var p = Preferences()
        // Fill exactly to the cap with deterministic names.
        repeat(Preferences.MAX_PROFILES) { i ->
            p = Profiles.saveCurrentAs(p, "profile-%02d".format(i))
        }
        assertThat(p.savedProfiles).hasSize(Preferences.MAX_PROFILES)
        assertThat(p.savedProfiles.first().name).isEqualTo("profile-00")
        assertThat(p.savedProfiles.last().name).isEqualTo("profile-%02d".format(Preferences.MAX_PROFILES - 1))

        // One more save should drop the oldest, not the newest.
        val after = Profiles.saveCurrentAs(p, "the-newest")

        assertThat(after.savedProfiles).hasSize(Preferences.MAX_PROFILES)
        assertThat(after.savedProfiles.first().name).isEqualTo("profile-01")
        assertThat(after.savedProfiles.last().name).isEqualTo("the-newest")
    }

    @Test fun `saveCurrentAs overwrites without growing past the cap`() {
        // Saving an already-present name shouldn't both drop an old entry
        // AND fail the cap — overwrite should be net-zero in size.
        var p = Preferences()
        repeat(Preferences.MAX_PROFILES) { i ->
            p = Profiles.saveCurrentAs(p, "profile-%02d".format(i))
        }
        val sizeBeforeOverwrite = p.savedProfiles.size

        // Update the active preset, then overwrite an existing profile name.
        val updated = Profiles.saveCurrentAs(
            p.copy(activePresetKey = "amber"),
            "profile-00"
        )

        assertThat(updated.savedProfiles).hasSize(sizeBeforeOverwrite)
        // The overwritten entry must now sit at the END (it was re-added
        // after the filter-not), not at its original position.
        assertThat(updated.savedProfiles.last().name).isEqualTo("profile-00")
        assertThat(updated.savedProfiles.last().snapshot.activePresetKey).isEqualTo("amber")
    }
}
