package com.openlumen.prefs

/**
 * Applies the app's preset catalog boundary to persisted and imported keys.
 * The catalog is supplied by the application layer so core-prefs does not
 * depend on the display-engine module.
 */
internal object PresetKeySanitizer {

    fun active(key: String, knownKeys: Set<String>): String =
        syntacticallyValid(key)?.takeIf { it == Preferences.CUSTOM_PRESET_KEY || it in knownKeys }
            ?: Preferences.CUSTOM_PRESET_KEY

    fun previous(key: String, knownKeys: Set<String>): String? =
        syntacticallyValid(key)?.takeIf { it in knownKeys }

    fun favorites(keys: List<String>, knownKeys: Set<String>): List<String> =
        keys.asSequence()
            .mapNotNull(::syntacticallyValid)
            .filter { it in knownKeys }
            .distinct()
            .take(MAX_FAVORITES)
            .toList()

    private fun syntacticallyValid(key: String): String? =
        key.takeIf { it.isNotBlank() && it.length <= MAX_KEY_LENGTH && it.none(Char::isISOControl) }

    private const val MAX_FAVORITES = 8
    private const val MAX_KEY_LENGTH = 64
}
