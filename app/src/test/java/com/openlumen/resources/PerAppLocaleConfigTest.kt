package com.openlumen.resources

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Test

class PerAppLocaleConfigTest {

    @Test
    fun `supported locale resources match the default string key set`() {
        val resDir = resourceDirectory()
        val baseKeys = stringKeys(File(resDir, "values/strings.xml"))

        supportedLocales.forEach { (locale, directory) ->
            val localizedFile = File(resDir, "$directory/strings.xml")
            assertThat(localizedFile.isFile).isTrue()
            assertThat(stringKeys(localizedFile))
                .containsExactlyElementsIn(baseKeys)
                .inOrder()
        }
    }

    @Test
    fun `locale configuration names the default and only shipped language directories`() {
        val resDir = resourceDirectory()
        val properties = Properties().apply {
            File(resDir, "resources.properties").inputStream().use(::load)
        }

        assertThat(properties.getProperty("unqualifiedResLocale")).isEqualTo("en")
        assertThat(supportedLocales.keys).containsExactly(
            "en", "de", "es", "fr", "ja", "pt"
        )

        val localizedDirectories = resDir.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("values-") }
            ?.mapNotNull { directory ->
                directory.name.substringAfter("values-")
                    .takeIf { it in supportedLocales.keys }
            }
            .orEmpty()

        assertThat(localizedDirectories).containsExactly("de", "es", "fr", "ja", "pt")
    }

    private fun stringKeys(file: File): List<String> {
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(file)
        val strings = document.getElementsByTagName("string")
        return (0 until strings.length)
            .mapNotNull { index ->
                val attributes = strings.item(index).attributes
                if (attributes.getNamedItem("translatable")?.nodeValue == "false") {
                    null
                } else {
                    attributes.getNamedItem("name").nodeValue
                }
            }
            .sorted()
    }

    private fun resourceDirectory(): File {
        val candidates = listOf(
            File("src/main/res"),
            File("app/src/main/res")
        )
        return candidates.firstOrNull { File(it, "values/strings.xml").isFile }
            ?: error("Unable to locate app resource directory from ${File(".").absolutePath}")
    }

    private companion object {
        private val supportedLocales = linkedMapOf(
            "en" to "values",
            "de" to "values-de",
            "es" to "values-es",
            "fr" to "values-fr",
            "ja" to "values-ja",
            "pt" to "values-pt"
        )
    }
}
