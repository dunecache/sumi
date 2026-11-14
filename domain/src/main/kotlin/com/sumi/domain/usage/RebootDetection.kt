package com.sumi.domain.usage

/**
 * Boot detection from the monotonic clock (ARCHITECTURE.md §5, R5 support).
 *
 * `BOOT_COMPLETED` can be dropped by OEM kill policies, `directBootAware` gaps, and
 * force-stopped apps. `SystemClock.elapsedRealtime()` resets on reboot, which makes it a
 * reliable local signal: when it regresses between two observations of the same policy,
 * a restart happened in between, and the boot wall instant is about
 * `currentWallMs - currentElapsedMs`.
 *
 * Pure math — the platform layer feeds the observations.
 */
object RebootDetection {

    /**
     * Returns the wall-clock instant of the boot when the monotonic clock regressed
     * between two observations — or null on the first run, on normal forward progress,
     * or when the inferred instant is implausible (a wall-clock correction can look like
     * a regression otherwise). An implausibly old inferred boot is ignored.
     */
    fun detectBootInstant(
        storedElapsedMs: Long,
        storedWallMs: Long,
        currentElapsedMs: Long,
        currentWallMs: Long,
    ): Long? {
        if (storedElapsedMs <= 0L) return null
        if (currentElapsedMs >= storedElapsedMs) return null
        val bootInstant = currentWallMs - currentElapsedMs
        // The boot cannot post-date the current observation, and must not predate the
        // stored wall sample by more than a month (clock corrections, not reboots).
        if (bootInstant > currentWallMs) return null
        if (bootInstant < storedWallMs - SANITY_WINDOW_MS) return null
        return bootInstant
    }

    private const val SANITY_WINDOW_MS = 30L * 24L * 60L * 60L * 1000L
}
