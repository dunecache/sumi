package com.sumi.feature.today

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sumi.core.designsystem.component.CategorySegment
import com.sumi.core.designsystem.theme.SumiTheme
import com.sumi.domain.goal.DailyGoal
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Rendered UI tests for Today (AGENTS.md §6 requires a Today screen test).
 *
 * These run on the JVM under Robolectric (`./gradlew :app:testDebugUnitTest`), so they
 * execute in CI without a device. They assert what the user actually sees: the day
 * overline, the hero line, the headline number, the daily intention card, the category
 * distribution and the most-used list — in the order the design specifies.
 *
 * Usage rows merge their descendants into one semantic node (name, duration, share),
 * so they are asserted through their spoken content description — the same string
 * TalkBack reads out.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class TodayScreenUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `populated day shows the overline, hero line and headline total`() {
        setContent(populatedState())

        composeRule.onNodeWithText("TODAY ·", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Your day, in perspective.").assertIsDisplayed()
        composeRule.onNodeWithText("Screen time today").assertIsDisplayed()
        composeRule.onNodeWithText("1h 30m").assertIsDisplayed()
    }

    @Test
    fun `comparison with the previous day is shown in neutral wording`() {
        setContent(populatedState())

        composeRule.onNodeWithText("About 1h 30m less than yesterday.").assertIsDisplayed()
    }

    @Test
    fun `daily intention card shows the goal, the pace and the remaining time`() {
        setContent(populatedState())

        composeRule.onNodeWithText("DAILY INTENTION").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("1h 30m of 3h used").assertIsDisplayed()
        composeRule.onNodeWithText("1h 30m remaining").assertIsDisplayed()
    }

    @Test
    fun `category distribution lists every bucket with its duration and share`() {
        setContent(populatedState())

        composeRule.onNodeWithText("WHERE YOUR TIME GOES").performScrollTo().assertIsDisplayed()
        composeRule.onNode(
            hasContentDescription("Social, 50m, 71 percent of tracked time"),
        ).performScrollTo().assertIsDisplayed()
        composeRule.onNode(
            hasContentDescription("Uncategorized, 20m, 29 percent of tracked time"),
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `most used list announces the app, its duration and its share`() {
        setContent(populatedState())

        composeRule.onNodeWithText("MOST USED").performScrollTo().assertIsDisplayed()
        composeRule.onNode(
            hasContentDescription("Messages, 40m, 57% of today"),
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `permission missing state explains how to enable usage access`() {
        setContent(
            populatedState().copy(
                isLoading = false,
                hasUsageAccess = false,
            ),
        )

        composeRule.onNodeWithText("Usage Access is off").assertIsDisplayed()
        composeRule.onNode(
            hasText("Sumi starts learning your day from now", substring = true),
        ).assertIsDisplayed()
    }

    @Test
    fun `empty day shows the no-usage message instead of a zero total`() {
        setContent(
            populatedState().copy(
                isLoading = false,
                hasUsageAccess = true,
                totalDurationMs = 0L,
                mostUsed = emptyList(),
                previousDayDurationMs = null,
                comparison = null,
                intention = DailyIntentionState.Disabled,
                categories = emptyList(),
                trackedDurationMs = 0L,
            ),
        )

        composeRule.onNodeWithText("No usage yet").assertIsDisplayed()
    }

    private fun setContent(state: TodayUiState) {
        composeRule.setContent {
            SumiTheme(darkTheme = false) {
                TodayContent(state = state)
            }
        }
    }

    private fun populatedState() = TodayUiState(
        isLoading = false,
        hasUsageAccess = true,
        totalDurationMs = 5_400_000L, // 1h 30m
        previousDayDurationMs = 10_800_000L, // 3h yesterday
        comparison = yesterdayComparison(5_400_000L, 10_800_000L),
        mostUsed = listOf(
            MostUsedApp("com.example.messages", "Messages", 2_400_000L),
            MostUsedApp("com.example.browser", "Browser", 1_200_000L),
        ),
        observation = "Your phone was busiest in the evening.",
        intention = DailyIntentionState.Progress(
            DailyGoal.evaluate(5_400_000L, 3L * 60L * 60L * 1000L),
        ),
        intentionEnabled = true,
        intentionGoalMinutes = 180,
        categories = listOf(
            CategorySegment("social", "Social", 3_000_000L, 0.71f, "matcha-4"),
            CategorySegment(null, "Uncategorized", 1_200_000L, 0.29f, "stone-1"),
        ),
        trackedDurationMs = 4_200_000L,
    )
}
