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
    colorToken = "categoryColor1",
    todayMs = 3_000_000L, // 50m
    todayShare = 0.71f,
    week = listOf(2L, 1L, 4L, 3L, 6L, 5L, 5L).map { it * 600_000L },
    weekTotalMs = 26_000_000L,
    apps = listOf(
        CategoryAppRow("com.sample.messages", "Messages", 1_800_000L, 0.43f),
        CategoryAppRow("com.sample.feed", "Fieldnotes", 1_200_000L, 0.29f),
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

@Preview(showBackground = true, backgroundColor = 0xFF1B1D18, name = "Category detail · Populated (dark)")
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
        CategoryRow("social", "Social", "categoryColor1", false, 12),
        CategoryRow("entertainment", "Entertainment", "categoryColor2", false, 8),
        CategoryRow("everything-else", "Everything else", "categoryColor6", true, 3),
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

@Preview(showBackground = true, backgroundColor = 0xFF1B1D18, name = "Category manager · Populated (dark)")
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
