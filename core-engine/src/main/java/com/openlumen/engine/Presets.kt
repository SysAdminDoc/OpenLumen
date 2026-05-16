package com.openlumen.engine

/**
 * Canonical preset library. Mirrors CF.Lumen's named filters; values were hand-tuned
 * by reference to the v3.74 build's behavior on a Pixel 6.
 */
object Presets {
    val RED      = LumenMatrix(r = 1.00f, g = 0.10f, b = 0.00f)
    val AMBER    = LumenMatrix(r = 1.00f, g = 0.55f, b = 0.10f)
    val SALMON   = LumenMatrix(r = 1.00f, g = 0.40f, b = 0.35f)
    val SEPIA    = LumenMatrix(r = 0.90f, g = 0.72f, b = 0.50f)
    val GRAY     = LumenMatrix(r = 0.55f, g = 0.55f, b = 0.55f)
    val NIGHT    = LumenMatrix(r = 1.00f, g = 0.78f, b = 0.55f) // ~3200K
    val DEEP     = LumenMatrix(r = 1.00f, g = 0.45f, b = 0.20f, dim = 0.30f) // pre-bedtime
    val OFF      = LumenMatrix.IDENTITY

    /** Color-vision-deficiency remap presets (channel-shuffled rather than scaled). */
    val PROTAN   = LumenMatrix(r = 0.85f, g = 1.00f, b = 0.95f)
    val DEUTAN   = LumenMatrix(r = 1.00f, g = 0.85f, b = 0.95f)
    val TRITAN   = LumenMatrix(r = 1.00f, g = 0.95f, b = 0.85f)

    data class Entry(val key: String, val displayName: String, val matrix: LumenMatrix)

    val ALL: List<Entry> = listOf(
        Entry("off",      "Off",         OFF),
        Entry("night",    "Night",       NIGHT),
        Entry("amber",    "Amber",       AMBER),
        Entry("red",      "Red",         RED),
        Entry("salmon",   "Salmon",      SALMON),
        Entry("sepia",    "Sepia",       SEPIA),
        Entry("gray",     "Grayscale",   GRAY),
        Entry("deep",     "Deep Sleep",  DEEP),
        Entry("protan",   "Protan",      PROTAN),
        Entry("deutan",   "Deutan",      DEUTAN),
        Entry("tritan",   "Tritan",      TRITAN)
    )

    fun byKey(key: String): Entry? = ALL.firstOrNull { it.key == key }
}
