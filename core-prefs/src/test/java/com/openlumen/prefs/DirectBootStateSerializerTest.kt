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

    @Test fun `unreadable bytes decode to safe default rather than throwing`() = runBlocking {
        val garbage = "{ this is not json :: }".toByteArray()

        val decoded = DirectBootStateSerializer.readFrom(ByteArrayInputStream(garbage))

        assertThat(decoded).isEqualTo(DirectBootState())
    }
}
