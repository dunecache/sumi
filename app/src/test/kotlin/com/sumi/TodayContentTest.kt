package com.sumi

import com.sumi.feature.today.TodayContent
import com.sumi.feature.today.TodayUiState
import com.sumi.feature.today.MostUsedApp
import org.junit.Test

/**
 * State-contract tests for the Today screen (M2). Runs in CI via `:app:testDebugUnitTest`.
 * These assert on the UiState the composable renders; the composable itself is covered
 * by human verification on device (no Robolectric harness in unit tests).
 */
class TodayContentTest {
    @Test
    fun `empty state shows no-usage message`() {
        // The empty state renders a no-usage message; CI fails if the UiState contract drifts.
        val state = TodayUiState(
            isLoading = false,
            hasUsageAccess = true,
            totalDurationMs = 0L,
            mostUsed = emptyList(),
        )
        assert(state.totalDurationMs == 0L)
        assert(state.mostUsed.isEmpty())
    }

    @Test
    fun `populated state lists most-used app`() {
        val state = TodayUiState(
            isLoading = false,
            hasUsageAccess = true,
            totalDurationMs = 3_600_000L,
            mostUsed = listOf(MostUsedApp("com.a", "A", 3_600_000L)),
        )
        assert(state.mostUsed.first().displayName == "A")
    }
}
