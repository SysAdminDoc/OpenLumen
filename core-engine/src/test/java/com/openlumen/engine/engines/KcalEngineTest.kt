package com.openlumen.engine.engines

import com.google.common.truth.Truth.assertThat
import com.openlumen.engine.EngineResult
import org.junit.Test

class KcalEngineTest {

    @Test fun `the min restore record round-trips`() {
        val record = KcalEngine.MinRestore(originalMin = 35, raised = true)

        assertThat(KcalEngine.MinRestore.decode(record.encode())).isEqualTo(record)
        assertThat(KcalEngine.MinRestore.decode(KcalEngine.MinRestore(0, false).encode()))
            .isEqualTo(KcalEngine.MinRestore(0, false))
    }

    @Test fun `a corrupt or out-of-range restore record is discarded, not guessed at`() {
        // Writing a garbage value back into kcal_min would be worse than not
        // restoring at all, so anything that does not parse exactly is dropped.
        for (raw in listOf("", "35", "35 true extra", "abc true", "35 yes", "-1 true", "999 true")) {
            assertThat(KcalEngine.MinRestore.decode(raw)).isNull()
        }
    }

    @Test fun `a record survives being written and read back as text`() {
        // This is the property that matters after a crash: the value has to
        // come back out of storage byte-identical, including the raised latch.
        val original = KcalEngine.MinRestore(originalMin = 12, raised = true)
        val fromDisk = KcalEngine.MinRestore.decode(original.encode() + "\n")

        assertThat(fromDisk).isEqualTo(original)
        assertThat(fromDisk!!.raised).isTrue()
        assertThat(fromDisk.originalMin).isEqualTo(12)
    }

    @Test fun `an untouched engine reports an unnecessary clear as success`() {
        assertThat(KcalEngine.clearWithoutPaths(appliedNonIdentity = false))
            .isEqualTo(EngineResult.Success)
    }

    @Test fun `clearing a written panel with no resolvable sysfs path reports failure`() {
        // C256: invalidateOnFailure drops resolvedPaths after a failed write,
        // which is exactly when the panel is still tinted. Returning Success
        // there told the service the panel was clean when it was not.
        val result = KcalEngine.clearWithoutPaths(appliedNonIdentity = true)

        assertThat(result).isInstanceOf(EngineResult.Failure::class.java)
        assertThat((result as EngineResult.Failure).message).contains("no sysfs path")
    }

    @Test fun `KCAL scalar uses standard 0 to 255 range`() {
        assertThat(KcalEngine.toKcalScalar(1f, 0)).isEqualTo(255)
        assertThat(KcalEngine.toKcalScalar(0.5f, 0)).isEqualTo(127)
        assertThat(KcalEngine.toKcalScalar(0f, 0)).isEqualTo(0)
    }

    @Test fun `KCAL scalar honors app-level floor inside standard range`() {
        assertThat(KcalEngine.toKcalScalar(0f, KcalEngine.SAFETY_MIN))
            .isEqualTo(KcalEngine.SAFETY_MIN)
        assertThat(KcalEngine.toKcalScalar(1f, KcalEngine.SAFETY_MIN))
            .isEqualTo(255)
    }

    @Test fun `KCAL scalar clamps non-finite and out-of-range input`() {
        assertThat(KcalEngine.toKcalScalar(Float.NaN, 0)).isEqualTo(255)
        assertThat(KcalEngine.toKcalScalar(Float.POSITIVE_INFINITY, 0)).isEqualTo(255)
        assertThat(KcalEngine.toKcalScalar(-1f, 0)).isEqualTo(0)
        assertThat(KcalEngine.toKcalScalar(2f, 0)).isEqualTo(255)
    }
}
