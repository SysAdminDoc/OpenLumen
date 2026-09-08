package com.openlumen.ui.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CoordParsingTest {

    @Test fun `dot separated decimal parses to finite double`() {
        assertThat(CoordParsing.parse("52.5200")).isEqualTo(52.52)
        assertThat(CoordParsing.parse("-74.0060")).isEqualTo(-74.0060)
    }

    @Test fun `single comma decimal is normalized to dot`() {
        // Regression: pre-fix, a user on a German / French / Spanish locale
        // typing "52,5200" — or having the picker auto-fill it via
        // `String.format(locale, "%.4f", ...)` — saw the Save button stay
        // disabled because `String.toDoubleOrNull()` only accepts `.`.
        assertThat(CoordParsing.parse("52,5200")).isEqualTo(52.52)
        assertThat(CoordParsing.parse("-74,0060")).isEqualTo(-74.0060)
    }

    @Test fun `mixed comma and dot is rejected to avoid swapping meaning`() {
        // "1,234.5" could be US thousands+decimal or a typo. Reject rather
        // than guess; the field expects -90..90 / -180..180 anyway so the
        // user can re-type without the thousands separator.
        assertThat(CoordParsing.parse("1,234.5")).isNull()
        assertThat(CoordParsing.parse("1.234,5")).isNull()
    }

    @Test fun `blank input parses to null without throwing`() {
        assertThat(CoordParsing.parse("")).isNull()
        assertThat(CoordParsing.parse("   ")).isNull()
        assertThat(CoordParsing.parse("\t")).isNull()
    }

    @Test fun `non numeric input parses to null`() {
        assertThat(CoordParsing.parse("north")).isNull()
        assertThat(CoordParsing.parse("52N")).isNull()
        assertThat(CoordParsing.parse("--52")).isNull()
    }

    @Test fun `NaN and infinity are not accepted as finite`() {
        // toDoubleOrNull happily parses these JVM tokens. They make no
        // physical sense as coordinates so rule them out at the parser.
        assertThat(CoordParsing.parse("NaN")).isNull()
        assertThat(CoordParsing.parse("Infinity")).isNull()
        assertThat(CoordParsing.parse("-Infinity")).isNull()
    }

    @Test fun `format always emits dot decimal regardless of system locale`() {
        // System-locale-driven format is the bug that broke comma-decimal
        // locales. format() must stay Locale.ROOT to keep exported profile
        // JSON portable across locales.
        val rendered = CoordParsing.format(52.5200)
        assertThat(rendered).isEqualTo("52.5200")
        assertThat(rendered).doesNotContain(",")
    }
}
