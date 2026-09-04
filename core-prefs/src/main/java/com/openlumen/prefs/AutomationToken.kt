package com.openlumen.prefs

import java.security.SecureRandom

/**
 * Shared secret for the exported automation surface (roadmap **C250**).
 *
 * A `BroadcastReceiver` cannot learn who sent it a broadcast, so the token is
 * the authentication mechanism. It is generated locally and is never sent
 * anywhere: no network call carries it, and the profile export redacts it
 * so a shared backup file cannot hand it to anyone.
 *
 * It does ride Android's own backup and device transfer, because it lives
 * in the preferences blob those copy. A restored install is detected by a
 * marker in the no-backup directory and closes the surface rather than
 * inheriting the previous device's token. See AutomationRestoreGuard.
 */
object AutomationToken {

    private val HEX = "0123456789abcdef".toCharArray()

    /** 16 bytes of `SecureRandom` rendered as [Preferences.AUTOMATION_TOKEN_LENGTH] hex characters. */
    fun generate(random: SecureRandom = SecureRandom()): String {
        val bytes = ByteArray(Preferences.AUTOMATION_TOKEN_LENGTH / 2)
        random.nextBytes(bytes)
        val out = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            out[i * 2] = HEX[v ushr 4]
            out[i * 2 + 1] = HEX[v and 0x0F]
        }
        return String(out)
    }

    /**
     * A stored token is only ever something [generate] produced. Anything
     * else — a truncated blob, an imported file, a hand-edited DataStore
     * entry — is discarded rather than trusted at a shorter length.
     */
    fun isWellFormed(token: String): Boolean =
        token.length == Preferences.AUTOMATION_TOKEN_LENGTH &&
            token.all { it in '0'..'9' || it in 'a'..'f' }

    /** Drops anything [isWellFormed] rejects, so sanitize can never persist a malformed secret. */
    fun sanitize(token: String): String = if (isWellFormed(token)) token else ""

    /**
     * Length-independent comparison. Both operands are fixed-length hex in
     * practice, but an attacker controls the presented value, so the loop
     * must not exit early on the first mismatching character.
     */
    fun matches(presented: String, stored: String): Boolean {
        if (stored.isEmpty()) return false
        var diff = presented.length xor stored.length
        val n = minOf(presented.length, stored.length)
        for (i in 0 until n) {
            diff = diff or (presented[i].code xor stored[i].code)
        }
        return diff == 0
    }
}
