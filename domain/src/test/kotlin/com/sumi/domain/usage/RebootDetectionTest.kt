package com.sumi.domain.usage

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RebootDetectionTest {

    private val t09: Long = 1762410000000L

    @Test
    fun `first run has no baseline and reports nothing`() {
        assertThat(RebootDetection.detectBootInstant(0L, 0L, 50_000L, t09)).isNull()
    }

    @Test
    fun `forward monotonic clock means no reboot`() {
        assertThat(RebootDetection.detectBootInstant(1_000L, t09, 5_000L, t09 + 4_000L)).isNull()
    }

    @Test
    fun `regressed monotonic clock yields the boot instant`() {
        // 40 h of monotonic time since boot; across a reboot the monotonic clock
        // restarts at ~0, so a later observation regresses.
        val storedElapsed = 40L * 60L * 60L * 1000L
        assertThat(
            RebootDetection.detectBootInstant(
                storedElapsedMs = storedElapsed,
                storedWallMs = t09,
                currentElapsedMs = 2L * 60L * 60L * 1000L, // 2 h since the reboot
                currentWallMs = t09 + 42L * 60L * 60L * 1000L,
            ),
        ).isEqualTo(t09 + 40L * 60L * 60L * 1000L)
    }

    @Test
    fun `implausibly ancient inferred boot is ignored`() {
        // Wall clock jumped backwards by 60 days; monotonic regressed too — not a reboot.
        assertThat(
            RebootDetection.detectBootInstant(
                storedElapsedMs = 5L * 60L * 60L * 1000L,
                storedWallMs = t09,
                currentElapsedMs = 1L * 60L * 60L * 1000L,
                currentWallMs = t09 - 60L * 24L * 60L * 60L * 1000L,
            ),
        ).isNull()
    }
}
