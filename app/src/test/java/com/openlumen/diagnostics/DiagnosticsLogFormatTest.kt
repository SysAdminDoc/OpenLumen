package com.openlumen.diagnostics

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Pure-format tests for [DiagnosticsLog.formatLine]. The actual file-backed
 * write path needs an Android Context and is tested manually.
 */
class DiagnosticsLogFormatTest {

    @Test fun `format produces space-separated level category message`() {
        val line = DiagnosticsLog.formatLine(
            DiagnosticsLog.Level.WARN,
            DiagnosticsLog.Category.SCHEDULE,
            "deferred 60s"
        )
        // ISO-8601 instant, then "WARN SCHEDULE deferred 60s"
        assertThat(line).contains(" WARN SCHEDULE deferred 60s")
    }

    @Test fun `format clamps very long messages to 512 chars`() {
        val long = "x".repeat(2000)
        val line = DiagnosticsLog.formatLine(
            DiagnosticsLog.Level.INFO,
            DiagnosticsLog.Category.ENGINE,
            long
        )
        // The leading instant + level + category fit in ~40 chars; the message
        // suffix should be at most 512.
        val suffix = line.substringAfter(" ENGINE ")
        assertThat(suffix).hasLength(512)
    }

    @Test fun `category names are stable identifiers (no spaces, all caps)`() {
        // Diagnostics log lines are grepable; category names must not change
        // shape without bumping a documented log version.
        for (cat in DiagnosticsLog.Category.values()) {
            assertThat(cat.name.all { it.isUpperCase() || it == '_' || it.isDigit() }).isTrue()
        }
    }

    // C53 stretch: filter helper tests. The About-tab dialog filters log
    // lines using `DiagnosticsLog.lineMatches`; these tests prove the
    // helper handles malformed input and the round-trip from formatLine.

    @Test fun `lineMatches accepts a well-formed line whose level and category are selected`() {
        val line = DiagnosticsLog.formatLine(
            DiagnosticsLog.Level.WARN,
            DiagnosticsLog.Category.SCHEDULE,
            "fallback to inexact alarm"
        )
        val ok = DiagnosticsLog.lineMatches(line, setOf("WARN"), setOf("SCHEDULE"))
        assertThat(ok).isTrue()
    }

    @Test fun `lineMatches rejects when level is not in the selected set`() {
        val line = DiagnosticsLog.formatLine(
            DiagnosticsLog.Level.DEBUG,
            DiagnosticsLog.Category.SERVICE,
            "onCreate"
        )
        val ok = DiagnosticsLog.lineMatches(line, setOf("WARN", "ERROR"), setOf("SERVICE"))
        assertThat(ok).isFalse()
    }

    @Test fun `lineMatches rejects when category is not in the selected set`() {
        val line = DiagnosticsLog.formatLine(
            DiagnosticsLog.Level.INFO,
            DiagnosticsLog.Category.WIDGET,
            "refresh broadcast"
        )
        val ok = DiagnosticsLog.lineMatches(line, setOf("INFO"), setOf("SERVICE"))
        assertThat(ok).isFalse()
    }

    @Test fun `lineMatches rejects blank and malformed lines`() {
        assertThat(DiagnosticsLog.lineMatches("", setOf("INFO"), setOf("SERVICE"))).isFalse()
        assertThat(DiagnosticsLog.lineMatches("   ", setOf("INFO"), setOf("SERVICE"))).isFalse()
        assertThat(DiagnosticsLog.lineMatches("notalogline", setOf("INFO"), setOf("SERVICE"))).isFalse()
        // Two tokens isn't enough to extract a level + category.
        assertThat(DiagnosticsLog.lineMatches("instant WARN", setOf("WARN"), setOf("SCHEDULE"))).isFalse()
    }

    @Test fun `lineMatches preserves a message containing spaces`() {
        // Sanity check that split(limit=4) keeps the message intact rather
        // than splitting on every space; otherwise a multi-word message
        // would push CATEGORY into the message slot and break the filter.
        val line = "2026-05-17T22:00:00Z WARN ENGINE this message has many spaces in it"
        val ok = DiagnosticsLog.lineMatches(line, setOf("WARN"), setOf("ENGINE"))
        assertThat(ok).isTrue()
    }
}
