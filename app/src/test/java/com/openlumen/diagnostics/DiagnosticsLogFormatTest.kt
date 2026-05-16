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
}
