package com.openlumen.prefs

import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Test

class DirectBootStateSerializerTest {

    @Test fun `direct boot state round trips through serializer`() = runBlocking {
        val state = DirectBootState(
            enabled = true,
            active = true,
            engine = EngineKindDto.Overlay,
            matrix = MatrixDto(r = 0.82f, g = 0.58f, b = 0.34f, dim = 0.2f),
            amoledBlackClamp = true
        )

        val out = ByteArrayOutputStream()
        DirectBootStateSerializer.writeTo(state, out)

        val decoded = DirectBootStateSerializer.readFrom(ByteArrayInputStream(out.toByteArray()))

        assertThat(decoded).isEqualTo(state)
    }

    @Test fun `blank direct boot state decodes to disabled default`() = runBlocking {
        val decoded = DirectBootStateSerializer.readFrom(ByteArrayInputStream(ByteArray(0)))

        assertThat(decoded).isEqualTo(DirectBootState())
    }

    @Test fun `oversized direct boot payload is rejected within the byte cap`() = runBlocking {
        val oversized = ByteArray(MAX_DIRECT_BOOT_BYTES + 1) { 120.toByte() }

        val decoded = DirectBootStateSerializer.readFrom(ByteArrayInputStream(oversized))

        assertThat(decoded).isEqualTo(DirectBootState())
    }

    @Test fun `direct boot matrix values are clamped before persist`() = runBlocking {
        // C193: r/g/b channels clamp to 0..1 (matching PreferencesStore), not
        // the prior 0..2 drift that let a restored tint exceed the canonical
        // range the engine and main store agree on.
        val state = DirectBootState(
            enabled = true,
            active = true,
            matrix = MatrixDto(
                r = 99f,
                g = Float.NaN,
                b = -4f,
                biasR = 7f,
                gammaB = 99f
            )
        )

        val out = ByteArrayOutputStream()
        DirectBootStateSerializer.writeTo(state, out)
        val decoded = DirectBootStateSerializer.readFrom(ByteArrayInputStream(out.toByteArray()))

        assertThat(decoded.matrix.r).isEqualTo(1f)
        assertThat(decoded.matrix.g).isEqualTo(1f)
        assertThat(decoded.matrix.b).isEqualTo(0f)
        assertThat(decoded.matrix.biasR).isEqualTo(1f)
        assertThat(decoded.matrix.gammaB).isEqualTo(5f)
    }

    @Test fun `direct boot in-range channel that exceeds canonical store is clamped`() = runBlocking {
        // 1.5 is a value that never occurs through normal app interaction
        // (PreferencesStore caps at 1.0) but previously survived the mirror's
        // looser 0..2 clamp. Both stores must agree on the visual result.
        val state = DirectBootState(
            enabled = true,
            active = true,
            matrix = MatrixDto(r = 1.5f, g = 1.5f, b = 1.5f)
        )

        val out = ByteArrayOutputStream()
        DirectBootStateSerializer.writeTo(state, out)
        val decoded = DirectBootStateSerializer.readFrom(ByteArrayInputStream(out.toByteArray()))

        assertThat(decoded.matrix.r).isEqualTo(1f)
        assertThat(decoded.matrix.g).isEqualTo(1f)
        assertThat(decoded.matrix.b).isEqualTo(1f)
    }

    @Test fun `direct boot matrix CVD coefficients are clamped and NaN-defaulted`() = runBlocking {
        // Regression: pre-fix the sanitizer left the 9 cross-channel matrix
        // coefficients un-clamped, so a corrupted mirror payload could feed
        // NaN / out-of-range values straight into the engine on Locked Boot
        // restore.
        val state = DirectBootState(
            enabled = true,
            active = true,
            matrix = MatrixDto(
                hasColorMatrix = true,
                matrixRr = Float.NaN,
                matrixRg = 99f,
                matrixGb = Float.NEGATIVE_INFINITY,
                matrixBb = -99f
            )
        )

        val out = ByteArrayOutputStream()
        DirectBootStateSerializer.writeTo(state, out)
        val decoded = DirectBootStateSerializer.readFrom(ByteArrayInputStream(out.toByteArray()))

        assertThat(decoded.matrix.matrixRr).isEqualTo(1f)
        assertThat(decoded.matrix.matrixRg).isEqualTo(4f)
        assertThat(decoded.matrix.matrixGb).isEqualTo(0f)
        assertThat(decoded.matrix.matrixBb).isEqualTo(-4f)
    }

    @Test fun `a malformed correction mode name is dropped before persist`() = runBlocking {
        // The name is resolved against the engine's enum on the way out, which
        // already degrades an unknown value to none. This bounds the shape so
        // a tampered mirror cannot carry an arbitrary string that far.
        val out = ByteArrayOutputStream()
        DirectBootStateSerializer.writeTo(
            DirectBootState(matrix = MatrixDto(daltonizer = "a".repeat(64))),
            out
        )

        val decoded = DirectBootStateSerializer.readFrom(ByteArrayInputStream(out.toByteArray()))

        assertThat(decoded.matrix.daltonizer).isNull()
    }

    @Test fun `a well-formed correction mode name survives persist`() = runBlocking {
        // Positive control: the check above has to reject the shape, not the
        // field.
        val out = ByteArrayOutputStream()
        DirectBootStateSerializer.writeTo(
            DirectBootState(matrix = MatrixDto(daltonizer = "MONOCHROMACY")),
            out
        )

        val decoded = DirectBootStateSerializer.readFrom(ByteArrayInputStream(out.toByteArray()))

        assertThat(decoded.matrix.daltonizer).isEqualTo("MONOCHROMACY")
    }

    @Test fun `unreadable bytes decode to safe default rather than throwing`() = runBlocking {
        val garbage = "{ this is not json :: }".toByteArray()

        val decoded = DirectBootStateSerializer.readFrom(ByteArrayInputStream(garbage))

        assertThat(decoded).isEqualTo(DirectBootState())
    }

    @Test fun `upcoming steps round trip, sorted and capped`() = runBlocking {
        val state = DirectBootState(
            enabled = true,
            active = true,
            upcoming = listOf(
                DirectBootStep(atEpochMs = 3_000L, active = true, matrix = MatrixDto(r = 0.5f)),
                DirectBootStep(atEpochMs = 1_000L, active = false),
                DirectBootStep(atEpochMs = 2_000L, active = true, matrix = MatrixDto(g = 7f))
            )
        )

        val out = ByteArrayOutputStream()
        DirectBootStateSerializer.writeTo(state, out)
        val decoded = DirectBootStateSerializer.readFrom(ByteArrayInputStream(out.toByteArray()))

        assertThat(decoded.upcoming.map { it.atEpochMs }).containsExactly(1_000L, 2_000L).inOrder()
        assertThat(decoded.upcoming[1].matrix.g).isEqualTo(1f)
    }

    @Test fun `a mirror written before steps existed decodes with none`() = runBlocking {
        val old = """{"schemaVersion":1,"enabled":true,"active":true,"engine":"Auto",""" +
            """"matrix":{"r":1.0,"g":0.5,"b":0.2},"amoledBlackClamp":false}"""

        val decoded = DirectBootStateSerializer.readFrom(ByteArrayInputStream(old.toByteArray()))

        assertThat(decoded.active).isTrue()
        assertThat(decoded.upcoming).isEmpty()
        assertThat(decoded.at(Long.MAX_VALUE)).isEqualTo(decoded)
    }

    @Test fun `the state at a moment takes the last step that has begun`() {
        val state = DirectBootState(
            enabled = true,
            active = true,
            matrix = MatrixDto(r = 1f, g = 0.1f, b = 0f),
            upcoming = listOf(
                DirectBootStep(atEpochMs = 1_000L, active = false),
                DirectBootStep(atEpochMs = 2_000L, active = true, matrix = MatrixDto(b = 0.3f))
            )
        )

        assertThat(state.at(999L)).isEqualTo(state)
        assertThat(state.at(1_000L).active).isFalse()
        assertThat(state.at(1_500L).upcoming.map { it.atEpochMs }).containsExactly(2_000L)
        assertThat(state.at(2_500L).matrix.b).isEqualTo(0.3f)
        assertThat(state.at(2_500L).upcoming).isEmpty()
        assertThat(state.tintsAtSomePoint).isTrue()
        assertThat(state.at(1_000L).tintsAtSomePoint).isTrue()
    }
}
