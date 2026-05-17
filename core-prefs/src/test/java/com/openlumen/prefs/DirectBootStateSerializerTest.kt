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

        assertThat(decoded.matrix.r).isEqualTo(2f)
        assertThat(decoded.matrix.g).isEqualTo(1f)
        assertThat(decoded.matrix.b).isEqualTo(0f)
        assertThat(decoded.matrix.biasR).isEqualTo(1f)
        assertThat(decoded.matrix.gammaB).isEqualTo(5f)
    }
}
