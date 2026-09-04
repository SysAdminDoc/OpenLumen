package com.openlumen.engine

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * C286. "Root not available" was one answer for four different situations,
 * and the next step is different for each. Under KernelSU and APatch there is
 * no prompt by design, so an app that is not on the allowlist gets exit 127
 * and that is final until someone flips a toggle in the manager. Magisk's deny
 * is 13, and its prompt can be held for about a minute when User
 * Authentication is on, so a timeout there means try again.
 *
 * Telling all of those users to wait for a prompt is right for exactly one of
 * them.
 */
class SuOutcomeTest {

    @Test fun `a successful probe is granted`() {
        assertThat(SuOutcome.of(0)).isEqualTo(SuOutcome.GRANTED)
    }

    @Test fun `Magisk's deny is a decision, not a missing answer`() {
        // su.cpp prints strerror(EACCES) and returns EACCES on deny.
        assertThat(SuOutcome.of(13, "su: Permission denied")).isEqualTo(SuOutcome.DENIED)
        assertThat(SuOutcome.of(13)).isEqualTo(SuOutcome.DENIED)
        assertThat(SuOutcome.DENIED.isInconclusive).isFalse()
    }

    @Test fun `not being on the allowlist reads as not permitted`() {
        // KernelSU's sucompat returns "inaccessible or not found", exit 127,
        // for an app it does not permit. A Magisk DenyList entry looks the
        // same. It stays inconclusive because a toggle can change it.
        assertThat(SuOutcome.of(127, "su: inaccessible or not found"))
            .isEqualTo(SuOutcome.NOT_PERMITTED)
        assertThat(SuOutcome.NOT_PERMITTED.isInconclusive).isTrue()
    }

    @Test fun `a held prompt is a timeout, not a refusal`() {
        assertThat(SuOutcome.of(-1)).isEqualTo(SuOutcome.TIMED_OUT)
        assertThat(SuOutcome.TIMED_OUT.isInconclusive).isTrue()
    }

    @Test fun `anything else is no root at all`() {
        for (code in listOf(1, 2, 126, 255)) {
            assertThat(SuOutcome.of(code)).isEqualTo(SuOutcome.NO_ROOT)
        }
        assertThat(SuOutcome.NO_ROOT.isInconclusive).isFalse()
    }

    @Test fun `the classes are distinct`() {
        // Positive control: a table that collapsed everything to one value
        // would satisfy every assertion above that only checks isInconclusive.
        val classified = listOf(0, 13, 127, -1, 1).map { SuOutcome.of(it) }

        assertThat(classified).containsNoDuplicates()
    }

    @Test fun `the probe's own inconclusive rule follows the classes`() {
        // Su.isInconclusive is what re-probes on the next call, so it has to
        // agree: a deny is final, an allowlist problem and a timeout are not.
        assertThat(Su.isInconclusive(127)).isTrue()
        assertThat(Su.isInconclusive(-1)).isTrue()
        assertThat(Su.isInconclusive(13)).isFalse()
        assertThat(Su.isInconclusive(0)).isFalse()
    }

    @Test fun `every root manager this app can name has a package`() {
        assertThat(RootManager.packages).containsExactly(
            "com.topjohnwu.magisk",
            "me.weishu.kernelsu",
            "me.bmax.apatch"
        )
    }
}
