package com.openlumen.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import com.google.common.truth.Truth.assertWithMessage
import com.openlumen.ui.theme.DarkColors
import com.openlumen.ui.theme.LightColors
import com.openlumen.ui.theme.OpenLumenTheme
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * On a tablet the navigation rail replaces the bottom bar, and in the dark
 * theme the headings on Schedule and Driver and the Sort label on Presets
 * were missing: the rail layout set a background but no content color, so
 * text that takes the default color was drawn black on black. Text beside
 * the rail has to come out in the theme's onBackground, which is what the
 * phone layout's Scaffold gives it.
 *
 * The rail layout is drawn with a plain label beside it rather than through
 * MainActivity, because under Robolectric the rail's screens are composed
 * but never placed, so they have no text layout to read.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w800dp-h1280dp-xhdpi")
class RailContentColorTest {

    private var controller: ActivityController<ComponentActivity>? = null

    @After fun tearDown() {
        controller?.pause()?.stop()?.destroy()
        ComposeSemantics.settle()
    }

    private fun assertOnBackground(dark: Boolean, scheme: ColorScheme) {
        val label = "Sort"
        val c = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        controller = c
        val activity = c.get()
        var themed: Color? = null
        activity.setContent {
            OpenLumenTheme(darkTheme = dark) {
                themed = MaterialTheme.colorScheme.onBackground
                LumenRailLayout(currentRoute = Dest.Home.route, onDestinationSelected = {}) { modifier ->
                    Text(label, modifier = modifier)
                }
            }
        }
        ComposeSemantics.settle()

        val node = ComposeSemantics.unmergedNodes(activity)
            .single { node -> node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == label } == true }
        val out = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action!!(out)

        assertWithMessage("the theme in use").that(themed).isEqualTo(scheme.onBackground)
        assertWithMessage("\"$label\" beside the rail").that(out.single().layoutInput.style.color)
            .isEqualTo(scheme.onBackground)
    }

    @Test fun `text beside the rail takes the dark theme's onBackground`() = assertOnBackground(true, DarkColors)

    @Test fun `text beside the rail takes the light theme's onBackground`() = assertOnBackground(false, LightColors)
}
