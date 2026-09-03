package com.openlumen.engine

import com.google.common.truth.Truth.assertThat
import com.openlumen.engine.engines.SurfaceFlingerEngine
import org.junit.Test

/**
 * Pure-JVM tests for the SF candidate registry (roadmap C03). We can't actually
 * exercise `service call SurfaceFlinger` from a unit test — that needs su and a
 * device — but we can guarantee the candidate list shape is correct so a
 * misconfiguration in one of the API ladders doesn't ship.
 */
class SurfaceFlingerEngineTest {

    private val engine = SurfaceFlingerEngine()

    @Test fun `only the AOSP-verified colour matrix code is a candidate on every API`() {
        // Replaces four earlier assertions (list starts with 1015, entries are
        // distinct, the list never shrinks, pre-29 tries only 1015). Those were
        // written against a per-API ladder; with the ladder reduced to a single
        // sourced code they can no longer fail, so they were removed rather
        // than left as decoration.
        //
        // 1014 is the daltonizer, 1022 saturation, 1023 colour mode. Earlier
        // releases listed 1023, 1030 and 1036 with no source behind them.
        for (api in API_LADDER) {
            assertThat(engine.candidatesFor(api).toList()).containsExactly(1015)
        }
    }

    @Test fun `an empty reply parcel is success, because 1015 writes no reply`() {
        // Parcel::print emits NULL for any reply with dataSize() == 0, and the
        // 1015 backdoor is a void case that never writes one. This is the shape
        // an accepted call actually produces on a device.
        assertThat(
            SurfaceFlingerEngine.isSuccessfulServiceCall(
                Su.SuResult(exitCode = 0, stdout = "Result: Parcel(NULL)", stderr = "")
            )
        ).isTrue()
    }

    @Test fun `a written reply parcel is success`() {
        assertThat(
            SurfaceFlingerEngine.isSuccessfulServiceCall(
                Su.SuResult(
                    exitCode = 0,
                    stdout = "Result: Parcel(00000000    '....')",
                    stderr = ""
                )
            )
        ).isTrue()
    }

    @Test fun `an error reply parcel is a rejected transaction`() {
        // IPCThreadState::waitForResponse calls reply->setError(err) on a failed
        // transaction, and Parcel::print renders that as Error: 0x... — this is
        // the only signal distinguishing a rejected code from an accepted one,
        // because service.cpp discards the transact status and still exits 0.
        assertThat(
            SurfaceFlingerEngine.isSuccessfulServiceCall(
                Su.SuResult(
                    exitCode = 0,
                    stdout = "Result: Parcel(Error: 0xffffffff \"Operation not permitted\")",
                    stderr = ""
                )
            )
        ).isFalse()
        assertThat(
            SurfaceFlingerEngine.isSuccessfulServiceCall(
                Su.SuResult(
                    exitCode = 0,
                    stdout = "Result: Parcel(Error: 0x80000002 \"Unknown error\")",
                    stderr = ""
                )
            )
        ).isFalse()
    }

    @Test fun `a missing service is a failure via exit 10 and the stderr message`() {
        // service.cpp is the only path that sets a non-zero exit: result = 10
        // with "Service <name> does not exist" on stderr, which Su merges into
        // stdout because it redirects the error stream.
        assertThat(
            SurfaceFlingerEngine.isSuccessfulServiceCall(
                Su.SuResult(
                    exitCode = 10,
                    stdout = "service: Service SurfaceFlinger does not exist",
                    stderr = ""
                )
            )
        ).isFalse()
    }

    @Test fun `a su failure is never mistaken for an accepted transaction`() {
        // 127 is "su not on PATH", -1 is the process timeout.
        assertThat(
            SurfaceFlingerEngine.isSuccessfulServiceCall(
                Su.SuResult(exitCode = 127, stdout = "", stderr = "su not on PATH")
            )
        ).isFalse()
        assertThat(
            SurfaceFlingerEngine.isSuccessfulServiceCall(
                Su.SuResult(exitCode = -1, stdout = "", stderr = "")
            )
        ).isFalse()
    }

    @Test fun `apply service call writes enable flag and sixteen float slots`() {
        val command = SurfaceFlingerEngine.buildServiceCallCommand(1015, LumenMatrix.IDENTITY)

        assertThat(command).startsWith("service call SurfaceFlinger 1015 i32 1")
        assertThat(Regex(" i32 ").findAll(command).count()).isEqualTo(17)
        assertThat(command).contains("i32 1065353216")
    }

    @Test fun `disable service call writes only disable flag`() {
        val command = SurfaceFlingerEngine.buildDisableServiceCallCommand(1015)

        assertThat(command).isEqualTo("service call SurfaceFlinger 1015 i32 0")
    }

    private companion object {
        val API_LADDER = listOf(26, 28, 29, 30, 31, 32, 33, 34, 35, 36)
    }
}
