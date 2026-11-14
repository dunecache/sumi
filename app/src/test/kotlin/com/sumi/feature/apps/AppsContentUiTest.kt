package com.sumi.feature.apps

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sumi.core.designsystem.theme.SumiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Rendered UI tests for the redesigned Apps destination, asserting what the user sees:
 * the live chip row, the per-row combined semantics (what TalkBack speaks), the zero-usage
 * divider, the all-categorized success line, and the permission-missing full state.
 *
 * Runs on the JVM under Robolectric — no device or emulator needed.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class AppsContentUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun row(
        pkg: String,
        name: String,
        category: String?,
        categoryName: String?,
        todayMs: Long,
        weekMs: Long,
        isNew: Boolean = false,
    ) = AppRow(
        packageName = pkg,
        displayName = name,
        categoryId = category,
        categoryName = categoryName,
        categoryColorToken = "matcha-4",
        todayMs = todayMs,
        weekMs = weekMs,
        isNew = isNew,
        isSystemApp = false,
    )

    private fun state(
        apps: List<AppRow>,
        filter: AppsFilter = AppsFilter.All,
        sort: AppsSort = AppsSort.MOST_USED_TODAY,
        uncategorizedCount: Int = apps.count { it.categoryId == null },
        hasUsageAccess: Boolean = true,
        isLoading: Boolean = false,
        categories: List<CategoryFilterChip> = sampleCategories,
    ) = AppsUiState(
        isLoading = isLoading,
        hasUsageAccess = hasUsageAccess,
        apps = apps,
        todayTotalMs = apps.sumOf { it.todayMs },
        weekTotalMs = apps.sumOf { it.weekMs },
        categories = categories,
        filter = filter,
        sort = sort,
        uncategorizedCount = uncategorizedCount,
    )

    private val sampleCategories = listOf(
        CategoryFilterChip("social", "Social", "matcha-4", sortOrder = 0),
        CategoryFilterChip("education", "Education", "wisteria", sortOrder = 4),
        CategoryFilterChip("ai", "AI", "wisteria", sortOrder = 8),
    )

    // All + 3 category chips + Uncategorized + Manage… → index 5 is the last item.
    private val LAST_CHIP_INDEX = 5

    private val sampleApps = listOf(
        row("com.instagram.android", "Instagram", "social", "Social", 60_000L, 23_400_000L),
        // Kept categorized so the "Uncategorized" chip is the only such text on screen.
        row("com.mpv", "mpvRx", "social", "Social", 0L, 0L),
    )

    @Test
    fun `rows announce name, category, durations to TalkBack`() {
        setContent(state(sampleApps))

        composeRule.onNode(
            hasContentDescription("Instagram. Social category. 1m today. 6h 30m this week.", substring = true),
        ).assertIsDisplayed()
    }

    @Test
    fun `chip row renders the live categories and the manage action`() {
        setContent(state(sampleApps))

        composeRule.onNodeWithText("All").assertIsDisplayed()
        // Category chips are asserted with names unique to the row ("Social" also
        // appears as the row's category-identity line — that one is expected twice).
        composeRule.onNodeWithText("Education").assertIsDisplayed()
        composeRule.onNodeWithText("AI").assertIsDisplayed()
        // Lazy rows compose only onscreen items; offscreen chips must be scrolled
        // to by index (the Manage… affordance sits beyond the pre-composition window).
        composeRule.onNodeWithText("Uncategorized").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(CHIPS_ROW_TEST_TAG).performScrollToIndex(LAST_CHIP_INDEX)
        composeRule.onNodeWithText("Manage…").assertIsDisplayed()
    }

    @Test
    fun `usage sorts render the zero-usage divider`() {
        setContent(state(sampleApps))

        composeRule.onNodeWithText("NOT USED YET").assertIsDisplayed()
    }

    @Test
    fun `all apps categorized shows the quiet success line, never a void`() {
        setContent(
            state(
                apps = sampleApps.filter { it.categoryId != null },
                uncategorizedCount = 0,
            ),
        )

        composeRule.onNodeWithText("Every app has a category.").assertIsDisplayed()
    }

    @Test
    fun `uncategorized filter with no apps left frames it as a win`() {
        setContent(state(apps = emptyList(), filter = AppsFilter.Uncategorized))

        composeRule.onNodeWithText("Everything is categorized").assertIsDisplayed()
    }

    @Test
    fun `permission missing state is honest and offers the settings path`() {
        setContent(state(sampleApps, hasUsageAccess = false))

        composeRule.onNodeWithText("Usage Access is off").assertIsDisplayed()
        composeRule.onNodeWithText("Open Usage Access settings").assertIsDisplayed()
    }

    @Test
    fun `selection mode announces the count and shows select-all`() {
        setContent(
            state(sampleApps),
            selection = setOf("com.instagram.android"),
            selectionMode = true,
        )

        composeRule.onNodeWithText("1 selected").assertIsDisplayed()
        composeRule.onNodeWithText("Select all").assertIsDisplayed()
    }

    private fun setContent(
        state: AppsUiState,
        selection: Set<String> = emptySet(),
        selectionMode: Boolean = false,
    ) {
        composeRule.setContent {
            SumiTheme(darkTheme = false) {
                AppsContent(
                    state = state,
                    query = "",
                    selection = selection,
                    selectionMode = selectionMode,
                    reducedMotion = true,
                    iconLoader = null,
                    onQueryChange = {},
                    onFilterChange = {},
                    onSortChange = {},
                    onToggleSelection = {},
                    onClearSelection = {},
                    onSelectAll = {},
                    onEnterSelectionMode = {},
                    onOpenDetail = {},
                    onManageCategories = {},
                    onReassign = {},
                    onOpenPermissionSettings = {},
                )
            }
        }
    }
}
