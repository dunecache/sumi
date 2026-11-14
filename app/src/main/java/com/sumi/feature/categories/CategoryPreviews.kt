package com.sumi.feature.categories

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.sumi.core.designsystem.component.LoadingState
import com.sumi.core.designsystem.theme.SumiTheme

/**
 * Previews for the category screens (AGENTS.md §5). Sample data only — previews
 * never ship. Covers light, dark, loading, removed and populated states.
 */

/** Sample category: Social, with two apps and a trailing-7-day series. */
private fun sampleCategoryState() = CategoryDetailUiState(
    isLoading = false,
    exists = true,
    categoryId = "social",
    name = "Social",
    colorToken = "matcha-4",
    range = com.sumi.domain.insights.InsightsRange.WEEK,
    rangeCaption = "Nov 8 – Nov 14, 2026",
    rangeTotalMs = 3_000_000L * 7, // 5h 50m across the week
    rangeShare = 0.41f,
    comparison = "About 55m more than the previous 7 days.",
    series = listOf(20L, 5L, 40L, 30L, 65L, 50L, 45L).map { it * 60_000L },
    dayBars = listOf(
        com.sumi.core.designsystem.component.DayBar("Su", 20L * 60_000L, false, "Sunday, 20m"),
        com.sumi.core.designsystem.component.DayBar("Mo", 5L * 60_000L, false, "Monday, 5m"),
        com.sumi.core.designsystem.component.DayBar("Tu", 40L * 60_000L, false, "Tuesday, 40m"),
        com.sumi.core.designsystem.component.DayBar("We", 30L * 60_000L, false, "Wednesday, 30m"),
        com.sumi.core.designsystem.component.DayBar("Th", 65L * 60_000L, false, "Thursday, 65m"),
        com.sumi.core.designsystem.component.DayBar("Fr", 50L * 60_000L, false, "Friday, 50m"),
        com.sumi.core.designsystem.component.DayBar("Sa", 45L * 60_000L, true, "Saturday, 45m, so far"),
    ),
    dayLabels = listOf(
        "Sunday, Nov 8",
        "Monday, Nov 9",
        "Tuesday, Nov 10",
        "Wednesday, Nov 11",
        "Thursday, Nov 12",
        "Friday, Nov 13",
        "Saturday, Nov 14",
    ),
    mostActiveDayIndex = 4,
    quietestDayIndex = 1,
    apps = listOf(
        CategoryAppRow("com.sample.messages", "Messages", 1_800_000L * 7, 0.43f),
        CategoryAppRow("com.sample.feed", "Fieldnotes", 1_200_000L * 7, 0.29f),
    ),
)

@Preview(showBackground = true, backgroundColor = 0xFFF6F2E8, name = "Category detail · Loading")
@Composable
private fun PreviewCategoryDetailLoading() {
    SumiTheme(darkTheme = false) { LoadingState() }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F2E8, name = "Category detail · Removed")
@Composable
private fun PreviewCategoryDetailRemoved() {
    SumiTheme(darkTheme = false) {
        CategoryDetailContent(
            state = CategoryDetailUiState(isLoading = false, exists = false, categoryId = "gone"),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F2E8, name = "Category detail · Populated")
@Composable
private fun PreviewCategoryDetailPopulated() {
    SumiTheme(darkTheme = false) {
        CategoryDetailContent(state = sampleCategoryState())
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF181F18, name = "Category detail · Populated (dark)")
@Composable
private fun PreviewCategoryDetailPopulatedDark() {
    SumiTheme(darkTheme = true) {
        CategoryDetailContent(state = sampleCategoryState())
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F2E8, name = "Category detail · No apps assigned")
@Composable
private fun PreviewCategoryDetailNoApps() {
    SumiTheme(darkTheme = false) {
        CategoryDetailContent(state = sampleCategoryState().copy(apps = emptyList()))
    }
}

/** Sample manager state: three seeded-ish categories plus a large uncategorized residual. */
private fun sampleManagerState() = CategoriesUiState(
    isLoading = false,
    categories = listOf(
        CategoryRow("social", "Social", "matcha-4", false, 12),
        CategoryRow("entertainment", "Entertainment", "mist", false, 8),
        CategoryRow("everything-else", "Everything else", "method", true, 3),
    ),
    uncategorizedCount = 27,
)

@Preview(showBackground = true, backgroundColor = 0xFFF6F2E8, name = "Category manager · Loading")
@Composable
private fun PreviewCategoryManagerLoading() {
    SumiTheme(darkTheme = false) { LoadingState() }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F2E8, name = "Category manager · Populated")
@Composable
private fun PreviewCategoryManagerPopulated() {
    SumiTheme(darkTheme = false) {
        CategoryManagerContent(state = sampleManagerState())
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF181F18, name = "Category manager · Populated (dark)")
@Composable
private fun PreviewCategoryManagerPopulatedDark() {
    SumiTheme(darkTheme = true) {
        CategoryManagerContent(state = sampleManagerState())
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F2E8, name = "Category manager · Empty")
@Composable
private fun PreviewCategoryManagerEmpty() {
    SumiTheme(darkTheme = false) {
        CategoryManagerContent(state = sampleManagerState().copy(categories = emptyList()))
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F2E8, name = "Category manager · Large font", fontScale = 1.5f)
@Composable
private fun PreviewCategoryManagerLargeFont() {
    SumiTheme(darkTheme = false) {
        Column { CategoryManagerContent(state = sampleManagerState()) }
    }
}
