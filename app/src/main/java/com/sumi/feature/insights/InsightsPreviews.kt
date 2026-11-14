package com.sumi.feature.insights

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.sumi.core.designsystem.component.CategorySegment
import com.sumi.core.designsystem.component.DayBar
import com.sumi.core.designsystem.theme.SumiTheme
import com.sumi.core.usage.TopUsedApp
import com.sumi.domain.insights.Insight
import com.sumi.domain.insights.InsightKind
import com.sumi.domain.insights.InsightsRange

/**
 * Previews for the Insights screen (AGENTS.md §5). Sample data only — previews never
 * ship. Covers light, dark, loading, empty, permission-missing and populated states,
 * plus a selected bar inspecting that day.
 */

private fun sampleBars(totals: List<Long>): List<DayBar> = totals.mapIndexed { index, value ->
    val labels = listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa")
    val weekdays = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
    DayBar(
        label = labels[index],
        valueMs = value,
        isPartial = index == totals.lastIndex,
        contentDescription = "${weekdays[index]}, ${value / 60_000L} minutes${if (index == totals.lastIndex) ", so far" else ""}",
    )
}

private fun sampleDetails(totals: List<Long>): List<DayDetail> {
    val weekdays = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
    return totals.mapIndexed { index, value ->
        DayDetail(
            dateKey = "2026-11-${8 + index}",
            title = "${weekdays[index]}, Nov ${8 + index}",
            totalMs = value,
            isPartial = index == totals.lastIndex,
            trackedMs = (value * 0.9f).toLong(),
            segments = listOf(
                CategorySegment("social", "Social", (value * 0.5f).toLong(), 0.5f, "matcha-4"),
                CategorySegment(null, "Uncategorized", (value * 0.4f).toLong(), 0.4f, "stone-1"),
            ).filter { it.durationMs > 0L },
        )
    }
}

private fun samplePopulatedState(): InsightsUiState {
    val totals = listOf(3_600_000L, 2_400_000L, 5_400_000L, 4_800_000L, 7_200_000L, 6_600_000L, 2_700_000L)
    return InsightsUiState(
        isLoading = false,
        hasUsageAccess = true,
        range = InsightsRange.WEEK,
        rangeCaption = "Nov 8 – Nov 14, 2026",
        totalMs = totals.sum(), // 32h 15m
        averageMs = totals.sum() / 7,
        comparison = "About 2h less than the previous 7 days.",
        trackedMs = (totals.sum() * 0.87f).toLong(),
        dailyTotals = sampleBars(totals),
        averageMsForBars = totals.sum() / 7,
        categories = listOf(
            CategorySegment("social", "Social", 12_600_000L, 0.48f, "matcha-4"),
            CategorySegment("entertainment", "Entertainment", 6_300_000L, 0.24f, "ochre"),
            CategorySegment("education", "Education", 1_530_000L, 0.06f, "wisteria"),
            CategorySegment(null, "Uncategorized", 5_940_000L, 0.23f, "stone-1"),
        ),
        uncategorizedMs = 5_940_000L,
        uncategorizedShare = 0.23f,
        social = SocialFacts(
            name = "Social",
            totalMs = 12_600_000L,
            share = 0.48f,
            dailyMs = listOf(1_800_000L, 1_200_000L, 2_400_000L, 2_100_000L, 2_700_000L, 1_800_000L, 600_000L),
            topApps = listOf(
                TopUsedApp("com.sample.feed", "Fieldnotes", 5_400_000L),
                TopUsedApp("com.sample.chat", "Chat", 3_600_000L),
            ),
            moreApps = 1,
            comparison = "About 40m more than the previous 7 days.",
        ),
        topApps = listOf(
            TopUsedApp("com.sample.feed", "Fieldnotes", 8_400_000L),
            TopUsedApp("com.sample.player", "Player", 4_800_000L),
            TopUsedApp("com.sample.browser", "Browser", 2_400_000L),
        ),
        hourly = List(24) { hour ->
            when (hour) {
                21, 22 -> 2_700_000L
                20, 8 -> 1_800_000L
                12, 13 -> 900_000L
                else -> 180_000L
            }
        },
        patterns = PatternFacts(
            busiestHour = 21,
            busiestHourMs = 2_700_000L,
            morningMs = 3_600_000L,
            eveningMs = 9_000_000L,
            weekdayMs = 23_400_000L,
            weekendMs = 8_700_000L,
            weekdayDays = 5,
            weekendDays = 2,
        ),
        coverageDays = 7,
        observations = listOf(
            Insight(InsightKind.FACT, "You had 29 sessions in this period (any length — a glance counts)."),
            Insight(InsightKind.FACT, "Your longest uninterrupted session was 48m."),
        ),
        dailyDetails = sampleDetails(totals),
        selectedDayIndex = 4,
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F2E8, name = "Insights · Loading")
@Composable
private fun PreviewInsightsLoading() {
    SumiTheme(darkTheme = false) { InsightsContent(state = InsightsUiState()) }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F2E8, name = "Insights · Permission missing")
@Composable
private fun PreviewInsightsPermissionMissing() {
    SumiTheme(darkTheme = false) {
        InsightsContent(state = samplePopulatedState().copy(hasUsageAccess = false))
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F2E8, name = "Insights · Empty range")
@Composable
private fun PreviewInsightsEmpty() {
    SumiTheme(darkTheme = false) {
        InsightsContent(state = samplePopulatedState().copy(
            coverageDays = 0,
            dailyDetails = emptyList(),
            dailyTotals = emptyList(),
        ))
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F2E8, name = "Insights · Populated")
@Composable
private fun PreviewInsightsPopulated() {
    SumiTheme(darkTheme = false) { InsightsContent(state = samplePopulatedState()) }
}

@Preview(showBackground = true, backgroundColor = 0xFF171F18, name = "Insights · Populated (dark)")
@Composable
private fun PreviewInsightsPopulatedDark() {
    SumiTheme(darkTheme = true) { InsightsContent(state = samplePopulatedState()) }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F2E8, name = "Insights · Thin history")
@Composable
private fun PreviewInsightsThinHistory() {
    SumiTheme(darkTheme = false) {
        InsightsContent(
            state = samplePopulatedState().copy(
                coverageDays = 2,
                patterns = PatternFacts(null, 0L, 0L, 0L, 0L, 0L, 0, 0),
                observations = emptyList(),
            ),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F2E8, name = "Insights · 30 days", fontScale = 1.0f)
@Composable
private fun PreviewInsightsMonth() {
    val totals = List(30) { index -> (1_800_000L + (index * 450_000L) % 9_000_000L) }
    val monthState = samplePopulatedState().copy(
        range = InsightsRange.MONTH,
        rangeCaption = "Oct 16 – Nov 14, 2026",
        totalMs = totals.sum(),
        averageMs = totals.sum() / 30,
        averageMsForBars = totals.sum() / 30,
        dailyTotals = totals.mapIndexed { index, value ->
            DayBar(
                label = (index + 1).toString(),
                valueMs = value,
                isPartial = index == totals.lastIndex,
                contentDescription = "Day ${index + 1}, ${value / 60_000L} minutes",
            )
        },
        dailyDetails = totals.mapIndexed { index, value ->
            DayDetail(
                dateKey = "2026-10-${(index + 1).coerceAtMost(28)}",
                title = "Day ${index + 1}",
                totalMs = value,
                isPartial = index == totals.lastIndex,
                trackedMs = value,
                segments = emptyList(),
            )
        },
        selectedDayIndex = null,
    )
    SumiTheme(darkTheme = false) { InsightsContent(state = monthState) }
}
