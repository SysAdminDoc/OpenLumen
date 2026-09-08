package com.openlumen.widget

import android.content.res.Configuration
import com.google.common.truth.Truth.assertThat
import com.openlumen.R
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * C321. The widgets were hard-wired to Mocha, so a light launcher got two dark
 * tiles in a pale tray; the picker preview was a white glyph on nothing, which
 * on a light picker is white on white; and the active-preset ring was a
 * surface tone about 1.6:1 against the tile behind it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WidgetThemingTest {

    private fun colorIn(nightMode: Boolean, id: Int): Int {
        val base = RuntimeEnvironment.getApplication()
        val config = Configuration(base.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (nightMode) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        return base.createConfigurationContext(config).resources.getColor(id, null)
    }

    @Test fun `the widget surface follows the launcher's theme`() {
        val day = colorIn(nightMode = false, R.color.widget_surface)
        val night = colorIn(nightMode = true, R.color.widget_surface)

        assertThat(day).isNotEqualTo(night)
    }

    @Test fun `widget text is readable on the surface it sits on`() {
        for (night in listOf(false, true)) {
            val surface = colorIn(night, R.color.widget_surface)
            val onSurface = colorIn(night, R.color.widget_on_surface)

            assertThat(contrast(onSurface, surface)).isGreaterThan(4.5)
        }
    }

    @Test fun `the picker preview is not a bare glyph`() {
        // previewImage pointed at ic_lumen_tile, a white shape with no
        // backplate. The replacement draws it on the widget's own surface.
        for (name in listOf("widget_toggle_info", "widget_preset_info")) {
            val xml = File("src/main/res/xml/$name.xml").readText()

            assertThat(xml).contains("@drawable/ic_widget_preview")
            assertThat(xml).doesNotContain("previewImage=\"@drawable/ic_lumen_tile\"")
        }
    }

    @Test fun `the fallback layouts colour their own text`() {
        // These are RemoteViews, so they inherit nothing from the app theme
        // and a missing textColor means whatever the launcher decides.
        for (name in listOf("widget_toggle", "widget_preset")) {
            val xml = File("src/main/res/layout/$name.xml").readText()

            assertThat(xml).contains("@color/widget_on_surface")
        }
    }

    @Test fun `every preset slot says its name and whether it is running`() {
        val context = RuntimeEnvironment.getApplication()

        val inactive = context.getString(R.string.widget_preset_slot, "Amber")
        val active = context.getString(R.string.widget_preset_slot_active, "Amber")

        assertThat(inactive).contains("Amber")
        assertThat(active).contains("Amber")
        assertThat(active).isNotEqualTo(inactive)
    }

    private fun contrast(a: Int, b: Int): Double {
        fun luminance(color: Int): Double {
            fun channel(v: Int): Double {
                val s = v / 255.0
                return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
            }
            return 0.2126 * channel((color shr 16) and 0xFF) +
                0.7152 * channel((color shr 8) and 0xFF) +
                0.0722 * channel(color and 0xFF)
        }
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }
}
