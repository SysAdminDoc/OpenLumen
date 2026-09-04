package com.openlumen.engine

/**
 * What a failed `su` probe actually means.
 *
 * "Root not available" is not one answer, and the next step differs by root
 * manager. Under KernelSU and APatch there is no prompt by design: an app that
 * is not on the allowlist gets `su: inaccessible or not found` and exit 127,
 * which is definitive and is fixed by a toggle in the manager. Magisk's deny
 * is exit 13 with "Permission denied", and its prompt can be held for about a
 * minute when User Authentication is on, so a timeout there means try again
 * rather than anything is wrong. A Magisk DenyList entry looks like 127 too.
 *
 * Treating all of that as one inconclusive result told a user with a fixable
 * allowlist problem to wait for a prompt that is never coming.
 */
enum class SuOutcome {
    /** The probe ran and returned success. */
    GRANTED,

    /** The manager asked and the user, or a policy, said no. Magisk's 13. */
    DENIED,

    /**
     * `su` was not reachable: not on the allowlist, or hidden from this app.
     * KernelSU and APatch answer this way for an app they do not permit, and
     * so does a Magisk DenyList entry.
     */
    NOT_PERMITTED,

    /** No answer arrived in time. A held Magisk prompt looks like this. */
    TIMED_OUT,

    /** Nothing on this device answers to `su` at all. */
    NO_ROOT;

    /** True when a later probe could still say yes. */
    val isInconclusive: Boolean
        get() = this == TIMED_OUT || this == NOT_PERMITTED

    companion object {
        /**
         * Classify a probe from its exit code and whatever it wrote.
         *
         * The message matters for 13: Magisk prints `strerror(EACCES)` on a
         * deny, and other tools reuse 13 for their own reasons.
         */
        fun of(exitCode: Int, message: String? = null): SuOutcome = when {
            exitCode == 0 -> GRANTED
            exitCode == -1 -> TIMED_OUT
            exitCode == 127 -> NOT_PERMITTED
            exitCode == 13 && message.orEmpty().contains("permission denied", ignoreCase = true) ->
                DENIED
            exitCode == 13 -> DENIED
            else -> NO_ROOT
        }
    }
}

/**
 * The root managers worth naming in a next step, by package.
 *
 * Resolved through PackageManager, which is why the app declares them in its
 * manifest queries: without that, an installed manager reads as absent.
 */
enum class RootManager(val packageName: String) {
    MAGISK("com.topjohnwu.magisk"),
    KERNEL_SU("me.weishu.kernelsu"),
    APATCH("me.bmax.apatch");

    companion object {
        val packages: List<String> = entries.map { it.packageName }
    }
}
