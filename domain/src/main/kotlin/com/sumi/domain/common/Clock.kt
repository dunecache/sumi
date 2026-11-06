package com.sumi.domain.common

/**
 * Abstraction over the current time so domain and data code stays testable.
 * Implementations return **UTC epoch milliseconds**.
 */
fun interface Clock {
    fun nowUtcMs(): Long
}

/** Convenience [Clock] backed by [System.currentTimeMillis]. Prefer injecting in code under test. */
fun systemClock(): Clock = Clock { System.currentTimeMillis() }
