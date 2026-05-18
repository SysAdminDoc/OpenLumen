package com.openlumen.ui.components

import java.util.Locale

/**
 * Locale-tolerant parse/format for decimal-degree coordinates.
 *
 * Persisted coordinate strings always use `.` as the decimal separator so a
 * profile JSON exported on one locale round-trips on another. The picker UI
 * also accepts the user's locale comma as a one-character convenience, but
 * we never emit a comma — that would break the export contract.
 *
 * Lives outside [LocationEntryDialog] so it can be unit-tested without a
 * Composable harness.
 */
internal object CoordParsing {

    /** Render [value] with four decimal places, locale-independent. */
    fun format(value: Double): String = String.format(Locale.ROOT, "%.4f", value)

    /**
     * Parse a user-entered string into a finite Double. Returns null when the
     * input is blank, contains a non-numeric trail, has both `.` and `,`
     * (ambiguous: thousands or decimals?), or parses to NaN / infinity.
     *
     * Comma-decimal locales: a single `,` with no `.` is treated as a decimal
     * separator. Anything else with a `,` is rejected — we'd rather refuse
     * than silently swap meanings.
     */
    fun parse(raw: String): Double? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val normalized = when {
            trimmed.count { it == ',' } == 1 && '.' !in trimmed -> trimmed.replace(',', '.')
            ',' in trimmed -> return null
            else -> trimmed
        }
        val v = normalized.toDoubleOrNull() ?: return null
        return v.takeIf { it.isFinite() }
    }
}
