package com.sumi.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sumi.core.designsystem.theme.SumiTheme
import com.sumi.feature.onboarding.sections.FirstOverviewScreen
import com.sumi.feature.onboarding.sections.InitialSyncScreen
import com.sumi.feature.onboarding.sections.PrivacyScreen
import com.sumi.feature.onboarding.sections.UsageAccessScreen
import com.sumi.feature.onboarding.sections.WelcomeScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Rendered UI tests for onboarding (AGENTS.md §6 requires onboarding UI tests).
 *
 * These run on the JVM under Robolectric (`./gradlew :app:testDebugUnitTest`), so they
 * execute in CI without a device. Onboarding is the one place the copy must be honest:
 * what Sumi is, what it is not, and that nothing leaves the device.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class OnboardingUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `welcome step states what sumi does`() {
        setContent { WelcomeScreen(onNext = {}) }

        composeRule.onNodeWithText("Setup · 1 of 4").assertIsDisplayed()
        composeRule.onNodeWithText("Understand your attention. Find your balance.").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Sumi makes phone usage visible and offers optional, gentle tools to change habits.",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Continue").assertIsDisplayed()
    }

    @Test
    fun `privacy step promises on-device processing`() {
        setContent { PrivacyScreen(onNext = {}) }

        composeRule.onNodeWithText("Setup · 2 of 4").assertIsDisplayed()
        composeRule.onNodeWithText("Private by default").assertIsDisplayed()
        composeRule.onNode(
            hasText("Sumi processes and stores your usage on this device only", substring = true),
        ).assertIsDisplayed()
    }

    @Test
    fun `usage access step without permission offers system settings`() {
        setContent {
            UsageAccessScreen(
                uiState = UsageAccessUiState.Missing,
                onOpenSettings = {},
                onContinue = {},
            )
        }

        composeRule.onNodeWithText("Setup · 3 of 4").assertIsDisplayed()
        composeRule.onNodeWithText("Usage Access").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Sumi cannot do anything without it.",
            substring = true,
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Open system settings").assertIsDisplayed()
    }

    @Test
    fun `usage access step with permission confirms quietly`() {
        setContent {
            UsageAccessScreen(
                uiState = UsageAccessUiState.Granted,
                onOpenSettings = {},
                onContinue = {},
            )
        }

        composeRule.onNodeWithText("Usage Access is enabled.").assertIsDisplayed()
    }

    @Test
    fun `initial sync step explains the one-time import`() {
        setContent {
            InitialSyncScreen(uiState = UsageAccessUiState.Granted, onFinish = {})
        }

        composeRule.onNodeWithText("Setup · 4 of 4").assertIsDisplayed()
        composeRule.onNodeWithText("Preparing your history").assertIsDisplayed()
        composeRule.onNodeWithText(
            "This happens once, on your device.",
            substring = true,
        ).assertIsDisplayed()
    }

    @Test
    fun `initial sync step without permission is honest instead of hidden`() {
        setContent {
            InitialSyncScreen(uiState = UsageAccessUiState.Missing, onFinish = {})
        }

        composeRule.onNodeWithText("Usage Access is off").assertIsDisplayed()
    }

    @Test
    fun `first overview marks setup complete and only opens the app`() {
        setContent { FirstOverviewScreen(onOpenApp = {}) }

        composeRule.onNodeWithText("Setup complete").assertIsDisplayed()
        composeRule.onNodeWithText("This is Today.").assertIsDisplayed()
        composeRule.onNodeWithText("Open Sumi").assertIsDisplayed()
    }

    private fun setContent(content: @Composable () -> Unit) {
        composeRule.setContent {
            SumiTheme(darkTheme = false) { content() }
        }
    }
}
