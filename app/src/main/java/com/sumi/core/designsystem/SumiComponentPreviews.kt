package com.sumi.core.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.sumi.core.designsystem.component.AppUsageRow
import com.sumi.core.designsystem.component.CategoryDistributionCard
import com.sumi.core.designsystem.component.CategorySegment
import com.sumi.core.designsystem.component.TimelineStrip
import com.sumi.core.designsystem.component.WeeklyBars
import com.sumi.core.designsystem.theme.SumiTheme

/**
 * Previews for the shared usage components. Sample data only — previews never ship.
 * Covers light, dark, no-icon fallback, and large font scale (AGENTS.md §5).
 */

/** Hourly sample with a morning cluster, a midday dip, and an evening peak. */
private val SampleHourly = List(24) { hour ->
    when {
        hour in 9..11 -> 9L * 60_000
        hour in 14..16 -> 4L * 60_000
        hour in 19..22 -> 16L * 60_000
        else -> 0L
    }
}

private val SampleWeekly = listOf(3L, 5L, 2L, 8L, 6L, 4L, 7L).map { it * 60_000L }

@Composable
private fun UsageRowSample(loader: (suspend (String) -> android.graphics.drawable.Drawable?)? = null) {
    AppUsageRow(
        packageName = "sample.app.reader",
        displayName = "Sample Reader",
        durationMs = 72L * 60_000,
        shareOfTotal = 0.34f,
        iconLoader = loader,
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFF3F1E9, name = "Usage row · Light")
@Composable
private fun PreviewUsageRowLight() {
    SumiTheme(darkTheme = false) { Column { UsageRowSample() } }
}

@Preview(showBackground = true, backgroundColor = 0xFF171F18, name = "Usage row · Dark")
@Composable
private fun PreviewUsageRowDark() {
    SumiTheme(darkTheme = true) { Column { UsageRowSample() } }
}

@Preview(showBackground = true, name = "Usage row · Monogram fallback")
@Composable
private fun PreviewUsageRowMonogram() {
    SumiTheme(darkTheme = false) { Column { UsageRowSample(loader = null) } }
}

@Preview(showBackground = true, backgroundColor = 0xFFF3F1E9, name = "Timeline · Light")
@Composable
private fun PreviewTimelineLight() {
    SumiTheme(darkTheme = false) { Column { TimelineStrip(hourlyTotals = SampleHourly) } }
}

@Preview(showBackground = true, backgroundColor = 0xFF171F18, name = "Timeline · Dark")
@Composable
private fun PreviewTimelineDark() {
    SumiTheme(darkTheme = true) { Column { TimelineStrip(hourlyTotals = SampleHourly) } }
}

@Preview(showBackground = true, name = "Timeline · Empty day")
@Composable
private fun PreviewTimelineEmpty() {
    SumiTheme(darkTheme = false) { Column { TimelineStrip(hourlyTotals = List(24) { 0L }) } }
}

@Preview(showBackground = true, backgroundColor = 0xFFF3F1E9, name = "Weekly · Light")
@Composable
private fun PreviewWeeklyLight() {
    SumiTheme(darkTheme = false) { Column { WeeklyBars(dailyTotals = SampleWeekly) } }
}

@Preview(showBackground = true, backgroundColor = 0xFF171F18, name = "Weekly · Dark")
@Composable
private fun PreviewWeeklyDark() {
    SumiTheme(darkTheme = true) { Column { WeeklyBars(dailyTotals = SampleWeekly) } }
}

private val SampleCategorySegments = listOf(
    CategorySegment("social", "Social", 3_000_000L, 0.71f, "matcha-4"),
    CategorySegment("entertainment", "Entertainment", 900_000L, 0.21f, "mist"),
    CategorySegment(null, "Uncategorized", 300_000L, 0.08f, "stone-1"),
)

@Composable
private fun CategoryDistributionSample(segments: List<CategorySegment>) {
    CategoryDistributionCard(
        segments = segments,
        totalDurationMs = segments.sumOf { it.durationMs },
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFF3F1E9, name = "Distribution · Light")
@Composable
private fun PreviewDistributionLight() {
    SumiTheme(darkTheme = false) { Column { CategoryDistributionSample(SampleCategorySegments) } }
}

@Preview(showBackground = true, backgroundColor = 0xFF171F18, name = "Distribution · Dark")
@Composable
private fun PreviewDistributionDark() {
    SumiTheme(darkTheme = true) { Column { CategoryDistributionSample(SampleCategorySegments) } }
}

@Preview(showBackground = true, backgroundColor = 0xFFF3F1E9, name = "Distribution · Empty day")
@Composable
private fun PreviewDistributionEmpty() {
    SumiTheme(darkTheme = false) { Column { CategoryDistributionSample(emptyList()) } }
}

@Preview(showBackground = true, backgroundColor = 0xFFF3F1E9, name = "Distribution · Large font", fontScale = 1.5f)
@Composable
private fun PreviewDistributionLargeFont() {
    SumiTheme(darkTheme = false) { Column { CategoryDistributionSample(SampleCategorySegments) } }
}

@Preview(showBackground = true, name = "Usage row · Large font", fontScale = 1.5f)
@Composable
private fun PreviewUsageRowLargeFont() {
    SumiTheme(darkTheme = false) { Column { UsageRowSample() } }
}
