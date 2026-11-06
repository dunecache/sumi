package com.sumi.domain.usage

/**
 * Source of reboot instants (UTC epoch ms) for [SessionReconstruction] R5.
 * Platform implements it by recording `BOOT_COMPLETED` intents; sessions never
 * extend across a reboot. Empty when no reboot has been recorded.
 */
fun interface RebootBoundarySource {
    fun rebootInstantsUtcMs(): List<Long>
}
