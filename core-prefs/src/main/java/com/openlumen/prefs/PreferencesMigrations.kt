package com.openlumen.prefs

/**
 * Versioned schema migrations for [Preferences].
 *
 * Tied to roadmap candidate **C29** (Versioned preference migrations).
 *
 * Migration policy:
 *
 * 1. **Additive changes don't need a migration.** Adding a field with a Kotlin
 *    default is transparently absorbed by kotlinx-serialization on decode.
 *    Removing a field is similarly absorbed because the store uses
 *    `ignoreUnknownKeys = true`.
 *
 * 2. **Reinterpreting an existing field needs a migration.** For example, if
 *    `presetIntensity` ever flips from a 0..1 lerp factor to a 0..100 percent,
 *    a migration must rescale it on read.
 *
 * 3. **A bump always pairs with a migration entry.** Bump
 *    [Preferences.CURRENT_SCHEMA_VERSION] in `Preferences.kt`, then add a
 *    `Migration(from = oldVersion, to = newVersion) { p -> … }` entry to
 *    [steps]. The migration runner walks consecutively, so a v0 blob that
 *    needs to reach v5 goes through every step.
 *
 * 4. **Migrations are pure functions.** They receive a [Preferences] and return
 *    a [Preferences]. They never do I/O. [PreferencesStore.sanitize] runs
 *    after migrations and is the final clamp.
 *
 * 5. **Migrations stay forever.** Don't delete an old migration even after
 *    every plausible user has upgraded — a downgrade-then-upgrade or an old
 *    JSON import will need it.
 */
object PreferencesMigrations {

    data class Migration(val from: Int, val to: Int, val apply: (Preferences) -> Preferences)

    /**
     * Ordered list. Each entry covers the gap `from -> to = from + 1`.
     * The runner walks consecutively, so partial gaps are an error in this
     * list, not a runtime concern.
     */
    val steps: List<Migration> = listOf(
        // v0 (pre-C29 builds, no schemaVersion field on disk) -> v1.
        //
        // No data change required; the v1 schema is structurally a superset
        // of v0 (only new field is `favoritePresetKeys`, which defaults to
        // [Preferences.DEFAULT_FAVORITES] on decode). The migration's job
        // here is to *stamp* the schema version so future reads can tell
        // this blob has already been seen.
        Migration(from = 0, to = 1) { p ->
            p.copy(schemaVersion = 1)
        }
        // Future migrations append here.
    )

    /**
     * Run consecutive migrations from `prefs.schemaVersion` to
     * [Preferences.CURRENT_SCHEMA_VERSION]. Returns the (possibly updated)
     * preferences. If `prefs.schemaVersion` is already current, this is a
     * no-op.
     *
     * If a step is missing or the recorded version is *newer* than the app
     * supports (downgrade scenario), the function returns the input
     * unchanged. Sanitization downstream will still clamp invalid values;
     * we don't crash on unknown-future blobs.
     */
    fun migrate(prefs: Preferences): Preferences {
        var current = prefs
        // The on-disk default for a pre-C29 blob is `schemaVersion = 1` from
        // the constructor default, NOT 0 — because we serialize the field
        // and deserialize fills the default in. To honor real v0 blobs we
        // look for the field's absence at the JSON layer in
        // [PreferencesStore], where it can detect "schemaVersion key not
        // present" and pass `current = 0` here.
        val target = Preferences.CURRENT_SCHEMA_VERSION
        if (current.schemaVersion >= target) return current
        while (current.schemaVersion < target) {
            val step = steps.firstOrNull { it.from == current.schemaVersion }
                ?: return current // missing step: bail rather than loop
            val next = step.apply(current)
            if (next.schemaVersion == current.schemaVersion) {
                // Defensive: a migration that didn't stamp the version
                // would loop forever. Stamp it from our side and continue.
                current = next.copy(schemaVersion = step.to)
            } else {
                current = next
            }
        }
        return current
    }
}
