package com.sumi.core.common

import com.sumi.domain.common.Clock
import com.sumi.domain.common.TimeZoneProvider
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemClock @Inject constructor() : Clock {
    override fun nowUtcMs(): Long = System.currentTimeMillis()
}

@Singleton
class SystemTimeZoneProvider @Inject constructor() : TimeZoneProvider {
    override fun currentZone(): ZoneId = ZoneId.systemDefault()
}
