package com.openlumen

import androidx.work.Configuration
import com.google.common.truth.Truth.assertThat
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Test
import org.w3c.dom.Element

class WorkManagerStartupTest {
    @Test fun `application supplies workmanager configuration provider`() {
        assertThat(Configuration.Provider::class.java.isAssignableFrom(OpenLumenApp::class.java))
            .isTrue()
    }

    @Test fun `workmanager startup initializer stays disabled`() {
        val manifest = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(manifestFile())

        val startupProvider = manifest.getElementsByTagName("provider")
            .asElements()
            .single { it.androidAttribute("name") == "androidx.startup.InitializationProvider" }

        assertThat(startupProvider.toolsAttribute("node")).isEqualTo("merge")

        val initializerRemoval = startupProvider.getElementsByTagName("meta-data")
            .asElements()
            .single { it.androidAttribute("name") == "androidx.work.WorkManagerInitializer" }

        assertThat(initializerRemoval.androidAttribute("value")).isEqualTo("androidx.startup")
        assertThat(initializerRemoval.toolsAttribute("node")).isEqualTo("remove")
    }

    private fun manifestFile(): File {
        val candidates = listOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml")
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("Unable to locate AndroidManifest.xml from ${File(".").absolutePath}")
    }

    private fun org.w3c.dom.NodeList.asElements(): List<Element> =
        (0 until length).map { item(it) as Element }

    private fun Element.androidAttribute(name: String): String =
        getAttributeNS(ANDROID_NS, name)

    private fun Element.toolsAttribute(name: String): String =
        getAttributeNS(TOOLS_NS, name)

    private companion object {
        private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
        private const val TOOLS_NS = "http://schemas.android.com/tools"
    }
}
