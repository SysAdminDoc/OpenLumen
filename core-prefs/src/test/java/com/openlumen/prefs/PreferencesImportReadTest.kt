package com.openlumen.prefs

import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import org.junit.Assert.assertThrows
import org.junit.Test

class PreferencesImportReadTest {

    @Test fun `import read accepts exactly the byte limit`() {
        val bytes = byteArrayOf(0x7b, 0x7d, 0x0a, 0x20)

        val read = readImportBytes(ByteArrayInputStream(bytes), maxBytes = bytes.size)

        assertThat(read.asList()).containsExactlyElementsIn(bytes.asList()).inOrder()
    }

    @Test fun `import read rejects one byte over the limit before decoding`() {
        val bytes = byteArrayOf(
            0xf0.toByte(),
            0x9f.toByte(),
            0x99.toByte(),
            0x82.toByte(),
            0x7b
        )

        val thrown = assertThrows(IllegalStateException::class.java) {
            readImportBytes(ByteArrayInputStream(bytes), maxBytes = 4)
        }

        assertThat(thrown).hasMessageThat().contains("exceeds 4 bytes")
    }
}
