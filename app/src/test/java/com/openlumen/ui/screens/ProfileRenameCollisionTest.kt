package com.openlumen.ui.screens

import com.google.common.truth.Truth.assertThat
import com.openlumen.prefs.NamedProfile
import com.openlumen.prefs.Preferences
import com.openlumen.prefs.Profiles
import org.junit.Test

/**
 * C307. Renaming a profile onto a name that already exists left the Save
 * button enabled and swallowed the tap: the dialog stayed open with no
 * explanation, because both the screen and Profiles.rename return unchanged on
 * a collision.
 */
class ProfileRenameCollisionTest {

    private val prefs = Preferences(
        savedProfiles = listOf(
            NamedProfile("Evening", Profiles.snapshot(Preferences())),
            NamedProfile("Reading", Profiles.snapshot(Preferences()))
        )
    )

    @Test fun `a name another profile already holds is a collision`() {
        assertThat(profileRenameCollides(prefs, oldName = "Evening", newName = "Reading")).isTrue()
    }

    @Test fun `case alone does not make it someone else's name`() {
        assertThat(profileRenameCollides(prefs, oldName = "Evening", newName = "READING")).isTrue()
    }

    @Test fun `fixing the case of your own name is allowed`() {
        // The whole point of the rename dialog for a name the user already
        // owns. Treating this as a collision would lock them out of it.
        assertThat(profileRenameCollides(prefs, oldName = "Evening", newName = "EVENING")).isFalse()
        assertThat(profileRenameCollides(prefs, oldName = "Evening", newName = "Evening")).isFalse()
    }

    @Test fun `a free name is not a collision`() {
        assertThat(profileRenameCollides(prefs, oldName = "Evening", newName = "Night")).isFalse()
    }

    @Test fun `an empty name is left to the length check`() {
        assertThat(profileRenameCollides(prefs, oldName = "Evening", newName = "  ")).isFalse()
    }
}
