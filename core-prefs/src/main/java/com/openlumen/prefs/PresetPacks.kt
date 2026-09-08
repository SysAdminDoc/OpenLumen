package com.openlumen.prefs

/**
 * Pure transforms for the portable preset-pack format. A pack merges into the
 * existing named-profile library and label overrides; it never carries or
 * applies enabled state, schedule state, or the active preset.
 */
object PresetPacks {

    data class MergeResult(
        val preferences: Preferences,
        val importedProfileNames: List<String>,
        val replacedProfileNames: List<String>
    )

    fun merge(current: Preferences, pack: PresetPack): MergeResult {
        val incoming = uniqueProfiles(pack.profiles)
        val incomingKeys = incoming.mapNotNull { profileNameKey(it.name) }.toSet()
        val replaced = current.savedProfiles
            .filter { profileNameKey(it.name) in incomingKeys }
            .map { it.name }

        val merged = current.copy(
            savedProfiles = (current.savedProfiles.filterNot {
                profileNameKey(it.name) in incomingKeys
            } + incoming).takeLast(Preferences.MAX_PROFILES),
            presetNameOverrides = current.presetNameOverrides + pack.presetNameOverrides
        )
        return MergeResult(
            preferences = merged,
            importedProfileNames = incoming.map { it.name },
            replacedProfileNames = replaced
        )
    }

    private fun uniqueProfiles(profiles: List<NamedProfile>): List<NamedProfile> {
        val seen = mutableSetOf<String>()
        return profiles.asReversed()
            .asSequence()
            .mapNotNull { profile ->
                val cleanName = profileNameOrNull(profile.name) ?: return@mapNotNull null
                val key = profileNameKey(cleanName) ?: return@mapNotNull null
                if (!seen.add(key)) null else profile.copy(name = cleanName)
            }
            .toList()
            .asReversed()
    }
}
