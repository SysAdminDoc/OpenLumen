package com.openlumen.resources

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.text.Normalizer
import org.junit.Test

/**
 * A word spelled two ways in the same translation is wrong one of those ways,
 * and it does not take a dictionary to find: strip the accents and look for
 * collisions.
 *
 * This is how the French file went out with "Debut" three lines from "Début"
 * and "Eclairage nocturne systeme" a few lines from "Éclairage nocturne
 * système". A per-string correction pass will always miss some; this finds
 * the ones it missed.
 */
class TranslationSpellingTest {

    /**
     * Pairs that differ by an accent and are genuinely different words, so a
     * file containing both is correct. Everything else that collides is a
     * typo in one of its spellings.
     */
    private val homographs = mapOf(
        "fr" to setOf("a", "la", "ou", "sur", "des", "du", "applique", "desactive", "active"),
        "es" to setOf("esta", "que", "cuando", "como", "donde", "el", "mas", "si", "se", "tu", "mi", "de"),
        "pt" to setOf("a", "e", "esta", "por", "pode", "para", "os", "as", "da", "de", "esta", "so")
    )

    private fun strip(word: String): String =
        Normalizer.normalize(word, Normalizer.Form.NFD)
            .filter { it.code !in 0x0300..0x036F }
            .lowercase()

    private fun valuesIn(locale: String): List<String> {
        val text = File("src/main/res/values-$locale/strings.xml").readText()
        return Regex("""<(?:string|item)[^>]*>(.*?)</(?:string|item)>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(text)
            .map { it.groupValues[1] }
            .toList()
    }

    private fun inconsistentWords(locale: String): Map<String, Set<String>> {
        val forms = mutableMapOf<String, MutableSet<String>>()
        for (value in valuesIn(locale)) {
            for (word in Regex("""\p{L}+""").findAll(value).map { it.value }) {
                forms.getOrPut(strip(word)) { mutableSetOf() }.add(word.lowercase())
            }
        }
        return forms
            .filterValues { it.size > 1 }
            .filterKeys { it !in homographs.getValue(locale) }
    }

    @Test fun `French spells each word one way`() {
        assertThat(inconsistentWords("fr")).isEmpty()
    }

    @Test fun `Spanish spells each word one way`() {
        assertThat(inconsistentWords("es")).isEmpty()
    }

    @Test fun `Portuguese spells each word one way`() {
        assertThat(inconsistentWords("pt")).isEmpty()
    }

    @Test fun `the check can tell an accent apart from no accent`() {
        // Positive control. A stripper that dropped nothing, or a comparison
        // that lowercased both sides into the same string, would make all
        // three assertions above pass on any input at all.
        assertThat(strip("désactivé")).isEqualTo("desactive")
        assertThat(strip("Aperçu")).isEqualTo("apercu")
        assertThat(strip("système")).isEqualTo("systeme")
        assertThat(strip("Début")).isNotEqualTo("Début")
    }

    @Test fun `Portuguese does not mix its two orthographies`() {
        // The file is Brazilian: ativar, registro. The European forms carry a
        // silent c and a different noun, and mixing them inside one screen is
        // as visible to a reader as a missing accent.
        val text = File("src/main/res/values-pt/strings.xml").readText()

        assertThat(text).doesNotContain("activar")
        assertThat(text).doesNotContain("activado")
        assertThat(text).doesNotContain("registo")
    }
}
