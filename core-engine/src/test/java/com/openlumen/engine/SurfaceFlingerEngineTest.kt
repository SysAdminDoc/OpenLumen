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

    @Test fun `candidate list always starts with 1015 (the historical default)`() {
        for (api in API_LADDER) {
            val candidates = engine.candidatesFor(api)
            assertThat(candidates).asList().isNotEmpty()
            assertThat(candidates.first()).isEqualTo(1015)
        }
    }

    @Test fun `candidate list contains only distinct codes per API`() {
        for (api in API_LADDER) {
            val candidates = engine.candidatesFor(api).toList()
            assertThat(candidates.size).isEqualTo(candidates.distinct().size)
        }
    }

    @Test fun `candidate list grows or stays the same as API increases`() {
        // We never *shrink* the candidate list as Android versions move forward —
        // older transaction codes still need to be tried because OEMs may carry them.
        val sizes = API_LADDER.map { engine.candidatesFor(it).size }
        sizes.zipWithNext().forEach { (a, b) ->
            assertThat(b).isAtLeast(a)
        }
    }

    @Test fun `pre-29 API only tries 1015`() {
        // Probing extra codes on very old Android wastes a su call per candidate.
        assertThat(engine.candidatesFor(26).toList()).containsExactly(1015)
        assertThat(engine.candidatesFor(28).toList()).containsExactly(1015)
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
