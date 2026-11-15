package com.openlumen.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.Hyphens
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * C56 on a device at 2x font size in German: "OpenLumen-Benachricht / igungen"
 * in the notifications card and "Benachrichtigungsei / nstellungen öffnen" on
 * its button, cut at whatever letter the edge fell on.
 */
class LumenTypographyTest {

    private val base = Typography()

    private fun Typography.all(): List<TextStyle> = listOf(
        displayLarge, displayMedium, displaySmall,
        headlineLarge, headlineMedium, headlineSmall,
        titleLarge, titleMedium, titleSmall,
        bodyLarge, bodyMedium, bodySmall,
        labelLarge, labelMedium, labelSmall
    )

    @Test fun `german text hyphenates in every style`() {
        val styles = lumenTypography(base, "de").all()
        assertThat(styles.map { it.hyphens }.distinct()).containsExactly(Hyphens.Auto)
    }

    @Test fun `hyphenation changes nothing else about a style`() {
        val german = lumenTypography(base, "de").all()
        base.all().zip(german).forEach { (before, after) ->
            assertThat(after.copy(hyphens = before.hyphens)).isEqualTo(before)
        }
    }

    @Test fun `english and the other translations keep the stock typography`() {
        listOf("en", "es", "fr", "pt", "ja").forEach { language ->
            assertThat(lumenTypography(base, language)).isSameInstanceAs(base)
        }
    }
}
