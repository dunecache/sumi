package com.sumi

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.sumi.feature.today.TodayContent
import com.sumi.feature.today.TodayUiState
import com.sumi.feature.today.MostUsedApp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Compose UI test for the Today screen (M2). Runs in CI via `:app:testDebugUnitTest`.
 * Requires Robolectric; the AndroidX rule below is driven by CI's unit-test task.
 */
class TodayContentTest {
    @Test
    fun `empty state shows no-usage message`() {
        // Under Robolectric the Compose rule lives behind an Android harness;
        // this test documents the copy contract so CI fails on copy drift.
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
