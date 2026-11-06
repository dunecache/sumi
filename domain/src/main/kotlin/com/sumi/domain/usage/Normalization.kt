package com.sumi.domain.usage

/**
 * Normalization (ARCHITECTURE.md §4.2 stage 2).
 * Drops blank packages for app events, dedupes exact duplicates (R9),
 * and stable-sorts by timestamp (R10).
 */
object Normalization {

    /** Exact duplicate = same package, type, timestamp (R9). */
    fun normalize(events: List<RawUsageEvent>): List<RawUsageEvent> {
        val deduped = events
            .filter { event ->
                when (event.type) {
                    UsageEventType.FOREGROUND,
                    UsageEventType.BACKGROUND,
                    -> event.packageName.isNotBlank()
                    UsageEventType.SCREEN_INTERACTIVE,
                    UsageEventType.SCREEN_NON_INTERACTIVE,
                    -> true
                }
            }
            .distinctBy { Triple(it.packageName, it.type, it.timestampUtcMs) }
        return deduped.sortedWith(compareBy<RawUsageEvent> { it.timestampUtcMs })
    }
}
