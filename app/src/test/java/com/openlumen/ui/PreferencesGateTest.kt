package com.openlumen.ui

import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Test

/**
 * C329. DataStore is a file read, so the first frame after a cold start had
 * nothing to draw and drew `Preferences()`: the master switch read Off on a
 * device where the filter was on, every slider sat at its default, and the
 * whole screen jumped a frame later. Off is not a neutral thing to show for a
 * filter, and a screen reader announced it.
 *
 * What the placeholder looks like is pinned by the `ProductionHomeLoading`
 * screenshot baseline, which goes red if any screen stops gating. These pin
 * the wiring that baseline cannot see: that every screen gates, that the
 * interface default leaves preview and screenshot models ungated, and that the
 * real flow starts closed.
 */
class PreferencesGateTest {

    private val screens = listOf(
        "HomeScreen.kt",
        "ScheduleScreen.kt",
        "PresetsScreen.kt",
        "DriverScreen.kt",
        "AboutScreen.kt"
    )

    private fun source(path: String) = File("src/main/java/com/openlumen/$path").readText()

    @Test fun `every screen waits for its preferences`() {
        for (screen in screens) {
            val text = source("ui/screens/$screen")
            assertWithMessage("$screen reads the flag")
                .that(text).contains("vm.preferencesLoaded")
            assertWithMessage("$screen draws the placeholder instead of defaults")
                .that(text).contains("PreferencesPlaceholder()")
        }
    }

    @Test fun `the interface default leaves a supplied model ungated`() {
        // A preview or a screenshot harness hands over a ready set of
        // preferences, and gating those behind a state they never leave would
        // render every one of them as a spinner.
        val model = source("viewmodel/OpenLumenScreenModel.kt")

        assertWithMessage("the default is loaded")
            .that(model).contains("get() = ALWAYS_LOADED")
        assertWithMessage("and it is one shared instance, like the others")
            .that(model).contains("private val ALWAYS_LOADED: StateFlow<Boolean> = MutableStateFlow(true)")
    }

    @Test fun `the real flow starts closed and opens on the first emission`() {
        // Derived from the preferences flow itself, so it cannot report loaded
        // for a set of values the state flow has not seen.
        val vm = source("viewmodel/OpenLumenViewModel.kt")

        assertWithMessage("it starts false")
            .that(vm).contains("SharingStarted.Eagerly, false)")
        assertWithMessage("and it is the preferences flow that opens it")
            .that(vm).contains("override val preferencesLoaded: StateFlow<Boolean> = prefs.flow")
    }

    @Test fun `the placeholder says what it is doing`() {
        // A spinner with no name is nothing at all to a screen reader, and
        // this is the one frame where the screen has no other content.
        val placeholder = source("ui/components/PreferencesPlaceholder.kt")

        assertWithMessage("it carries a content description")
            .that(placeholder).contains("contentDescription = label")
        assertWithMessage("from a string resource")
            .that(placeholder).contains("R.string.loading_preferences")
    }
}
