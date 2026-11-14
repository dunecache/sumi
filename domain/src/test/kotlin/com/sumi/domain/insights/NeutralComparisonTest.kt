package com.sumi.domain.insights

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NeutralComparisonTest {

    @Test
    fun `deltas under five minutes read as unchanged`() {
        assertThat(NeutralComparison.neutralComparison(10L * 60_000L, 8L * 60_000L, "yesterday"))
            .isEqualTo("About the same as yesterday.")
        assertThat(NeutralComparison.neutralComparison(8L * 60_000L, 10L * 60_000L, "yesterday"))
            .isEqualTo("About the same as yesterday.")
    }

    @Test
    fun `minutes deltas are phrased neutrally with the window label`() {
        assertThat(NeutralComparison.neutralComparison(2L * 60_000L * 60, 2L * 60_000L * 60 + 20L * 60_000L, "yesterday"))
            .isEqualTo("About 20m less than yesterday.")
        assertThat(NeutralComparison.neutralComparison(2L * 60_000L * 60 + 20L * 60_000L, 2L * 60_000L * 60, "the previous 7 days"))
            .isEqualTo("About 20m more than the previous 7 days.")
    }

    @Test
    fun `hours deltas reuse duration formatting`() {
        assertThat(NeutralComparison.neutralComparison(3L * 60_000L * 60 + 15L * 60_000L, 60_000L * 60, "the previous 30 days"))
            .isEqualTo("About 2h 15m more than the previous 30 days.")
    }

    @Test
    fun `whole hours drop the zero minutes`() {
        assertThat(NeutralComparison.neutralComparison(5L * 60_000L * 60, 0L, "yesterday"))
            .isEqualTo("About 5h more than yesterday.")
    }
}
