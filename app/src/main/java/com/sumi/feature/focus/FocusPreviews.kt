package com.sumi.feature.focus

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.sumi.core.database.entity.FocusOutcome
import com.sumi.core.designsystem.theme.SumiTheme

/**
 * Previews for the Focus screen (AGENTS.md §5). Sample data only — never ships.
 * Covers light, dark, loading, empty, permission-denied (reminder) and the active session.
 */

/** A busy-enough week: one completed, one ended early with drift, one unconfirmable. */
private fun sampleSessions() = listOf(
    FocusHistoryRow(
        id = 4,
        startedAtUtcMs = java.time.LocalDate.of(2026, 11, 14).atTime(9, 15)
            .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
        intendedMinutes = 25,
        actualMinutes = 25,
        exits = 0,
        outcome = FocusOutcome.COMPLETED,
    ),
    FocusHistoryRow(
        id = 3,
        startedAtUtcMs = java.time.LocalDate.of(2026, 11, 13).atTime(21, 40)
            .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
        intendedMinutes = 45,
        actualMinutes = 18,
        exits = 2,
        outcome = FocusOutcome.ENDED_EARLY,
    ),
    FocusHistoryRow(
        id = 2,
        startedAtUtcMs = java.time.LocalDate.of(2026, 11, 12).atTime(19, 5)
            .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
        intendedMinutes = 25,
        actualMinutes = 0,
        exits = 0,
        outcome = FocusOutcome.INTERRUPTED,
    ),
)

private fun samplePopulatedState() = FocusUiState(
    isLoading = false,
    hasUsageAccess = true,
    intentionSaved = "Read one chapter, no rush.",
    intentionDraft = "",
    intentionEditing = false,
    selectedMinutes = 25,
    phase = FocusPhase.Idle,
    lastEndedSummary = "25 of 25 minutes. No gentle exits.",
    weeklyFocusMs = (2L * 60L + 45L) * 60_000L,
    canPostNotifications = true,
    reminderEnabled = true,
    reminderMinuteOfDay = 540,
    recentSessions = sampleSessions(),
)

private fun sampleEmptyState() = FocusUiState(
    isLoading = false,
    hasUsageAccess = true,
)

private fun sampleActiveState() = samplePopulatedState().copy(
    phase = FocusPhase.Active(
        sessionId = 4,
        intendedMinutes = 25,
        startedAtUtcMs = 1_766_300_000_000L,
        exits = 1,
        remainingMs = 12L * 60L * 1_000L + 34L * 1_000L,
        progressFraction = 0.5f,
    ),
)

private val noOp: () -> Unit = {}
private val noOpBool: (Boolean) -> Unit = {}
private val noOpString: (String) -> Unit = {}
private val noOpInt: (Int) -> Unit = {}

@Preview(showBackground = true, backgroundColor = 0xFFF6F2E8, name = "Focus · Loading")
@Composable
fun PreviewFocusLoading() {
    SumiTheme(darkTheme = false) {
        com.sumi.core.designsystem.component.LoadingState(description = "Loading your focus")
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F2E8, name = "Focus · Populated")
@Composable
fun PreviewFocusPopulated() {
    SumiTheme(darkTheme = false) {
        FocusContent(
            state = samplePopulatedState(),
            onStartEditingIntention = noOp,
            onIntentionDraftChange = noOpString,
            onSaveIntention = noOp,
            onDismissIntentionEditor = noOp,
            onClearIntention = noOp,
            onDurationChange = noOpInt,
            onStartSession = noOp,
            onReminderToggle = noOpBool,
            onAdjustReminderTime = noOp,
            onOpenNotificationSettings = noOp,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF181F18, name = "Focus · Populated (dark)")
@Composable
fun PreviewFocusPopulatedDark() {
    SumiTheme(darkTheme = true) {
        FocusContent(
            state = samplePopulatedState(),
            onStartEditingIntention = noOp,
            onIntentionDraftChange = noOpString,
            onSaveIntention = noOp,
            onDismissIntentionEditor = noOp,
            onClearIntention = noOp,
            onDurationChange = noOpInt,
            onStartSession = noOp,
            onReminderToggle = noOpBool,
            onAdjustReminderTime = noOp,
            onOpenNotificationSettings = noOp,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F2E8, name = "Focus · Empty (light)")
@Composable
fun PreviewFocusEmpty() {
    SumiTheme(darkTheme = false) {
        FocusContent(
            state = sampleEmptyState(),
            onStartEditingIntention = noOp,
            onIntentionDraftChange = noOpString,
            onSaveIntention = noOp,
            onDismissIntentionEditor = noOp,
            onClearIntention = noOp,
            onDurationChange = noOpInt,
            onStartSession = noOp,
            onReminderToggle = noOpBool,
            onAdjustReminderTime = noOp,
            onOpenNotificationSettings = noOp,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F2E8, name = "Focus · Intention editing")
@Composable
fun PreviewFocusEditing() {
    SumiTheme(darkTheme = false) {
        FocusContent(
            state = samplePopulatedState().copy(
                intentionEditing = true,
                intentionDraft = "Finish the chapter tonight",
            ),
            onStartEditingIntention = noOp,
            onIntentionDraftChange = noOpString,
            onSaveIntention = noOp,
            onDismissIntentionEditor = noOp,
            onClearIntention = noOp,
            onDurationChange = noOpInt,
            onStartSession = noOp,
            onReminderToggle = noOpBool,
            onAdjustReminderTime = noOp,
            onOpenNotificationSettings = noOp,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F2E8, name = "Focus · Reminder denied")
@Composable
fun PreviewFocusReminderDenied() {
    SumiTheme(darkTheme = false) {
        FocusContent(
            state = samplePopulatedState().copy(
                reminderEnabled = true,
                canPostNotifications = false,
            ),
            onStartEditingIntention = noOp,
            onIntentionDraftChange = noOpString,
            onSaveIntention = noOp,
            onDismissIntentionEditor = noOp,
            onClearIntention = noOp,
            onDurationChange = noOpInt,
            onStartSession = noOp,
            onReminderToggle = noOpBool,
            onAdjustReminderTime = noOp,
            onOpenNotificationSettings = noOp,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F2E8, name = "Focus · Active session")
@Composable
fun PreviewFocusActive() {
    SumiTheme(darkTheme = false) {
        ActiveSessionView(
            phase = sampleActiveState().phase as FocusPhase.Active,
            intention = "Read one chapter, no rush.",
            canSeeDrift = true,
            onEndEarly = noOp,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF181F18, name = "Focus · Active session (dark)")
@Composable
fun PreviewFocusActiveDark() {
    SumiTheme(darkTheme = true) {
        ActiveSessionView(
            phase = sampleActiveState().phase as FocusPhase.Active,
            intention = "Read one chapter, no rush.",
            canSeeDrift = true,
            onEndEarly = noOp,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F2E8, name = "Focus · Drift paused")
@Composable
fun PreviewFocusDriftPaused() {
    SumiTheme(darkTheme = false) {
        ActiveSessionView(
            phase = sampleActiveState().phase as FocusPhase.Active,
            intention = "Read one chapter, no rush.",
            canSeeDrift = false,
            onEndEarly = noOp,
        )
    }
}
