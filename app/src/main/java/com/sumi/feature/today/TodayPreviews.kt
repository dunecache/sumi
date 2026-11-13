package com.sumi.feature.today

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.sumi.core.designsystem.component.CategorySegment
import com.sumi.core.designsystem.theme.SumiTheme
import com.sumi.domain.goal.DailyGoal

/**
 * Previews for the Today screen and its daily-intention components (AGENTS.md §5).
 * Sample data only — previews never ship. Covers light, dark, loading,
 * error/permission-missing and every goal state.
 */

/** Sample day: 1h 30m used, goal 3h, per-app tracked share across two buckets. */
private fun samplePopulatedState() = TodayUiState(
    isLoading = false,
    hasUsageAccess = true,
    totalDurationMs = 5_400_000L, // 1h 30m
    previousDayDurationMs = 10_800_000L,
    comparison = "About 1h 30m less than yesterday.",
    mostUsed = listOf(
        MostUsedApp("com.sample.messages", "Messages", 2_400_000L),
        MostUsedApp("com.sample.browser", "Browser", 1_200_000L),
    ),
    observation = "1h 30m today — Messages took the largest share.",
    intention = DailyIntentionState.Progress(DailyGoal.evaluate(5_400_000L, 10_800_000L)),
    intentionEnabled = true,
    intentionGoalMinutes = 180,
    categories = listOf(
        CategorySegment("social", "Social", 3_000_000L, 0.71f, "matcha-4"),
        CategorySegment(null, "Uncategorized", 1_200_000L, 0.29f, "stone-1"),
    ),
    trackedDurationMs = 4_200_000L,
)

@Preview(showBackground = true, backgroundColor = 0xFFF6F2E8, name = "Today · Loading")
@Composable
private fun PreviewTodayLoading() {
    SumiTheme(darkTheme = false) { TodayContent(state = TodayUiState()) }
}

@Preview(showBackground = true, backgroundColor = 0xFF1B1D18, name = "Today · Loading (dark)")
@Composable
private fun PreviewTodayLoadingDark() {
    SumiTheme(darkTheme = true) { TodayContent(state = TodayUiState()) }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F2E8, name = "Today · Permission missing")
@Composable
private fun PreviewTodayPermissionMissing() {
    SumiTheme(darkTheme = false) {
        TodayContent(state = samplePopulatedState().copy(hasUsageAccess = false))
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F2E8, name = "Today · No usage yet")
@Composable
private fun PreviewTodayNoUsage() {
    SumiTheme(darkTheme = false) {
        TodayContent(
            state = samplePopulatedState().copy(
                totalDurationMs = 0L,
                mostUsed = emptyList(),
                previousDayDurationMs = null,
                comparison = null,
                intention = DailyIntentionState.Disabled,
                categories = emptyList(),
                trackedDurationMs = 0L,
            ),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F2E8, name = "Today · Populated")
@Composable
private fun PreviewTodayPopulated() {
    SumiTheme(darkTheme = false) { TodayContent(state = samplePopulatedState()) }
}

@Preview(showBackground = true, backgroundColor = 0xFF1B1D18, name = "Today · Populated (dark)")
@Composable
private fun PreviewTodayPopulatedDark() {
    SumiTheme(darkTheme = true) { TodayContent(state = samplePopulatedState()) }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F2E8, name = "Today · Large font", fontScale = 1.5f)
@Composable
private fun PreviewTodayLargeFont() {
    SumiTheme(darkTheme = false) { TodayContent(state = samplePopulatedState()) }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F2E8, name = "Daily intention · Unset")
@Composable
private fun PreviewIntentionUnset() {
    SumiTheme(darkTheme = false) {
        Column(Modifier.fillMaxWidth()) { DailyIntentionCard(state = DailyIntentionState.Disabled, onEdit = {}) }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F2E8, name = "Daily intention · On track")
@Composable
private fun PreviewIntentionOnTrack() {
    SumiTheme(darkTheme = false) {
        Column(Modifier.fillMaxWidth()) {
            DailyIntentionCard(state = samplePopulatedState().intention, onEdit = {})
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F2E8, name = "Daily intention · Reached")
@Composable
private fun PreviewIntentionReached() {
    SumiTheme(darkTheme = false) {
        Column(Modifier.fillMaxWidth()) {
            DailyIntentionCard(
                state = DailyIntentionState.Progress(
                    DailyGoal.evaluate(10_800_000L, 10_800_000L),
                ),
                onEdit = {},
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F2E8, name = "Daily intention · Over")
@Composable
private fun PreviewIntentionExceeded() {
    SumiTheme(darkTheme = false) {
        Column(Modifier.fillMaxWidth()) {
            DailyIntentionCard(
                state = DailyIntentionState.Progress(
                    DailyGoal.evaluate(12_600_000L, 10_800_000L),
                ),
                onEdit = {},
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1B1D18, name = "Daily intention · Today (dark)")
@Composable
private fun PreviewIntentionDark() {
    SumiTheme(darkTheme = true) {
        Column(Modifier.fillMaxWidth()) { DailyIntentionCard(state = samplePopulatedState().intention, onEdit = {}) }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F2E8, name = "Goal sheet · With existing goal")
@Composable
private fun PreviewGoalEditSheetWithGoal() {
    SumiTheme(darkTheme = false) {
        GoalEditSheet(
            currentMinutes = 180,
            enabled = true,
            onDismiss = {},
            onSave = {},
            onRemove = {},
            onEnabledChange = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F2E8, name = "Goal sheet · Fresh (no goal yet)")
@Composable
private fun PreviewGoalEditSheetFresh() {
    SumiTheme(darkTheme = false) {
        GoalEditSheet(
            currentMinutes = null,
            enabled = true,
            onDismiss = {},
            onSave = {},
            onRemove = {},
            onEnabledChange = {},
        )
    }
}
