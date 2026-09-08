package com.openlumen.prefs

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PreferencesRecoveryTest {

    @Test
    fun recovery_copy_is_bounded_without_truncating_normal_payloads() {
        val oversized = "x".repeat(MAX_RECOVERY_CHARS + 1)

        assertThat(boundedRecoveryText(oversized)).hasLength(MAX_RECOVERY_CHARS)
        assertThat(boundedRecoveryText("{\"enabled\":false}"))
            .isEqualTo("{\"enabled\":false}")
    }
}
