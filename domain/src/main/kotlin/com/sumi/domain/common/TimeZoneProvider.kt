package com.sumi.domain.common

import java.time.ZoneId

/**
 * Provides the device's current time zone at call time.
 * Date keys are always derived with the zone in effect when aggregation runs, never stored as local times.
 */
fun interface TimeZoneProvider {
    fun currentZone(): ZoneId
}

/** Default provider backed by [ZoneId.systemDefault]. Prefer injecting in code under test. */
fun systemTimeZoneProvider(): TimeZoneProvider = TimeZoneProvider { ZoneId.systemDefault() }
