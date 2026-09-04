package com.openlumen.resources

import android.content.res.Configuration
import android.content.res.Resources
import com.google.common.truth.Truth.assertThat
import com.openlumen.R
import java.util.Locale
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * C270. Three count-bearing strings were English "(s)" hacks or a bare %d in
 * front of a plural noun, which no other language can render correctly. French
 * is the useful case to check: it treats zero as singular, which a hand-rolled
 * suffix cannot express at all.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PluralFormsTest {

    private fun resourcesFor(locale: Locale): Resources {
        val base = RuntimeEnvironment.getApplication().resources
        val config = Configuration(base.configuration).apply { setLocale(locale) }
        return RuntimeEnvironment.getApplication().createConfigurationContext(config).resources
    }

    @Test fun `English says line for one and lines for more`() {
        val res = resourcesFor(Locale.US)

        assertThat(res.getQuantityString(R.plurals.about_diag_log_count, 1, 1, 1))
            .isEqualTo("Showing 1 of 1 line")
        assertThat(res.getQuantityString(R.plurals.about_diag_log_count, 4, 2, 4))
            .isEqualTo("Showing 2 of 4 lines")
        assertThat(res.getQuantityString(R.plurals.about_diag_log_count, 0, 0, 0))
            .isEqualTo("Showing 0 of 0 lines")
    }

    @Test fun `French takes the singular for zero, which a suffix cannot`() {
        val res = resourcesFor(Locale.FRANCE)

        // This is the whole reason for the change: in French, 0 is singular.
        assertThat(res.getQuantityString(R.plurals.about_diag_log_count, 0, 0, 0))
            .isEqualTo("Affichage de 0 ligne sur 0")
        assertThat(res.getQuantityString(R.plurals.about_diag_log_count, 1, 1, 1))
            .isEqualTo("Affichage de 1 ligne sur 1")
        assertThat(res.getQuantityString(R.plurals.about_diag_log_count, 5, 2, 5))
            .isEqualTo("Affichage de 2 lignes sur 5")
    }

    @Test fun `the pack preview counts each of its two totals separately`() {
        val res = resourcesFor(Locale.US)

        assertThat(res.getQuantityString(R.plurals.preset_pack_preview_added, 1, 1))
            .isEqualTo("1 saved preset added")
        assertThat(res.getQuantityString(R.plurals.preset_pack_preview_added, 3, 3))
            .isEqualTo("3 saved presets added")
        assertThat(res.getQuantityString(R.plurals.preset_pack_preview_replaced, 1, 1))
            .isEqualTo("1 existing name replaced")
        assertThat(res.getQuantityString(R.plurals.preset_pack_preview_replaced, 0, 0))
            .isEqualTo("0 existing names replaced")
    }

    @Test fun `a language with no plural still renders every count`() {
        // Positive control: Japanese supplies only "other", so a lookup that
        // demanded "one" would throw or fall back to the wrong locale.
        val res = resourcesFor(Locale.JAPAN)

        for (count in listOf(0, 1, 2, 11)) {
            assertThat(res.getQuantityString(R.plurals.about_diag_log_count, count, count, count))
                .contains(count.toString())
        }
    }

    @Test fun `every locale supplies every plural this app reads`() {
        // Guards the thing that goes wrong quietly: a translator adding a
        // language without its quantity forms, which resolves to the default
        // locale's text with no error.
        val plurals = listOf(
            R.plurals.about_diag_log_count,
            R.plurals.preset_pack_preview_added,
            R.plurals.preset_pack_preview_replaced
        )
        val english = resourcesFor(Locale.US)

        for (locale in listOf(Locale.GERMANY, Locale.FRANCE, Locale.JAPAN, Locale("es", "ES"), Locale("pt", "PT"))) {
            val res = resourcesFor(locale)
            for (plural in plurals) {
                for (count in listOf(1, 5)) {
                    val translated = res.getQuantityString(plural, count, count, count)
                    assertThat(translated).isNotEmpty()
                    assertThat(translated)
                        .isNotEqualTo(english.getQuantityString(plural, count, count, count))
                }
            }
        }
    }
}
