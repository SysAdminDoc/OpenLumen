package com.openlumen.ui

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/**
 * C334, C335. Six actions changed state and said nothing, which leaves a
 * screen-reader user with no way to tell whether the tap landed, and everyone
 * else guessing on a screen where the thing that changed is off the fold.
 *
 * These read the source rather than the rendered UI: a Toast is not
 * observable in a JVM test, and what has to hold is that each action still
 * reaches a message and that the message is a real string resource.
 */
class ActionFeedbackTest {

    private val res = File("src/main/res")

    private fun source(path: String) = File("src/main/java/com/openlumen/$path").readText()

    private fun stringsFor(locale: String) = File(res, "$locale/strings.xml").readText()

    private val locales = listOf("values", "values-de", "values-es", "values-fr", "values-ja", "values-pt")

    /** Message keys, and the source file whose action has to reach each one. */
    private val messages = mapOf(
        "about_crash_log_cleared" to "ui/screens/AboutScreen.kt",
        "about_diag_log_cleared" to "ui/screens/AboutScreen.kt",
        "about_diag_log_copied" to "ui/screens/AboutScreen.kt",
        "about_automation_token_regenerated" to "ui/screens/AboutScreen.kt",
        "preset_name_reset" to "ui/screens/PresetsScreen.kt",
        "light_sensor_threshold_set" to "ui/components/LightSensorCard.kt"
    )

    @Test fun `every action message is a string the code actually reads`() {
        for ((key, path) in messages) {
            assertThat(source(path)).contains("R.string.$key")
        }
    }

    @Test fun `every action message is translated everywhere`() {
        for (key in messages.keys) {
            for (locale in locales) {
                val marker = """<string name="$key">"""
                assertThat(stringsFor(locale)).contains(marker)
                // A key present but empty is the same as no message at all.
                val value = stringsFor(locale).substringAfter(marker).substringBefore("</string>")
                assertThat(value.trim()).isNotEmpty()
            }
        }
    }

    @Test fun `the two clears offer an undo rather than only an apology`() {
        // A message alone would still lose the log. Both clears hand their
        // snapshot to the restore the snackbar action calls.
        val about = source("ui/screens/AboutScreen.kt")

        assertThat(about).contains("DiagnosticsLog.restore(")
        assertThat(about).contains("CrashLogger.restore(")
        assertThat(about).contains("R.string.action_undo")
    }

    @Test fun `the diagnostics log can be selected and copied`() {
        // C334. The only other way off the device was the 3 KB tail in the
        // driver report, which is not the lines the user is looking at.
        val about = source("ui/screens/AboutScreen.kt")

        assertThat(about).contains("SelectionContainer")
        assertThat(about).contains("R.string.about_diag_log_copy")
    }

    @Test fun `no screen model default builds its flow in the getter`() {
        // `get() = MutableStateFlow(null)` handed out a new flow on every read,
        // so anything collecting one of these in composition re-subscribed on
        // every recomposition and never saw the same instance twice. The
        // defaults share one permanently empty flow now.
        //
        // Read from the source because the interface has 47 abstract members
        // and the only fake that implements them lives in the screenshot test
        // source set, which a unit test cannot see. That fake exercising the
        // real defaults is what `:app:validateDebugScreenshotTest` covers.
        val model = File(
            "src/main/java/com/openlumen/viewmodel/OpenLumenScreenModel.kt"
        ).readText()

        // Only the getters, so a comment quoting the old form cannot pass or
        // fail this on its own.
        val getters = model.lines().filter { it.trim().startsWith("get() =") }
        assertThat(getters).isNotEmpty()
        for (line in getters) {
            assertThat(line).doesNotContain("MutableStateFlow(")
        }
        assertThat(model).contains("private val NO_SERVICE_START_ERROR")
        assertThat(model).contains("private val NO_PENDING_PRESET_PACK")
    }
}
