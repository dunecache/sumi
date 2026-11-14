package com.sumi.feature.onboarding.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.sumi.core.designsystem.component.FactRow
import com.sumi.core.designsystem.component.FactStack
import com.sumi.core.designsystem.component.ProgressModule
import com.sumi.core.designsystem.component.StatusIndicator
import com.sumi.core.designsystem.component.StatusTone
import com.sumi.core.designsystem.component.SumiPrimaryButton
import com.sumi.core.designsystem.component.SumiTextLink
import com.sumi.core.designsystem.component.WhatSumiDoesNotDoSheet
import com.sumi.core.designsystem.theme.SumiSpacing
import com.sumi.feature.onboarding.InitialSyncUiState
import com.sumi.feature.onboarding.UsageAccessUiState

// Onboarding: Welcome → Privacy → Focus (optional) → Usage Access → Initial sync →
// Ready. Setup completes when the initial sync finishes (or the user chooses to
// continue with nothing stored); "Ready." only opens the app.
// The only permission ask in onboarding is the Focus reminder opt-in, and it fires only
// when the user flips the switch — never on its own (PRINCIPLES.md, AGENTS.md §9).
//
// Layout grammar (reference standard §10.1): metadata pinned under the status bar,
// the headline block in the optical upper-middle (center-anchored band, absorbing the
// void), the action zone pinned above the gesture inset. No continuous void exceeds
// SumiSpacing.maxVoid.

@Composable
private fun StepLabel(step: Int, of: Int) {
    Text(
        text = "Setup · $step of $of",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Shared shell: status-bar inset, screen margin, metadata row on top, CTA at bottom. */
@Composable
private fun StepScaffold(
    step: Int? = null,
    of: Int = TOTAL_STEPS,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
    action: @Composable () -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = SumiSpacing.screenHorizontal),
    ) {
        Spacer(Modifier.height(SumiSpacing.lg))
        if (step != null) StepLabel(step, of)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            content()
        }
        action()
        Spacer(Modifier.height(SumiSpacing.xl))
    }
}

private const val TOTAL_STEPS = 5

@Composable
fun WelcomeScreen(onNext: () -> Unit) {
    var showTrustSheet by remember { mutableStateOf(false) }
    StepScaffold(
        step = 1,
        content = {
            Text(
                text = "Understand your attention. Find your balance.",
                style = MaterialTheme.typography.displayLarge,
            )
            Spacer(Modifier.height(SumiSpacing.md))
            Text(
                text = "Sumi makes phone usage visible and offers optional, gentle tools to change habits.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        action = {
            SumiPrimaryButton(label = "Continue", onClick = onNext)
            Spacer(Modifier.height(SumiSpacing.sm))
            SumiTextLink(
                text = "What Sumi does not do",
                onClick = { showTrustSheet = true },
                centered = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
    if (showTrustSheet) {
        WhatSumiDoesNotDoSheet(onDismiss = { showTrustSheet = false })
    }
}

@Composable
fun PrivacyScreen(onNext: () -> Unit) {
    StepScaffold(
        step = 2,
        content = {
            Text(
                text = "Private by default",
                style = MaterialTheme.typography.displayLarge,
            )
            Spacer(Modifier.height(SumiSpacing.md))
            Text(
                text = "Everything stays on this device.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(SumiSpacing.xl))
            FactStack(
                facts = listOf(
                    FactRow("No account.", "Nothing to sign up for, nothing to log into."),
                    FactRow("No analytics.", "No telemetry, trackers, or crash reports."),
                    FactRow("No network access.", "The app never talks to the internet."),
                ),
            )
        },
        action = { SumiPrimaryButton(label = "Continue", onClick = onNext) },
    )
}

/**
 * Optional step: one quiet once-a-day nudge. The toggle IS the ask moment — flipping it
 * on launches the runtime permission prompt on API 33+; denying leaves the switch off
 * with a quiet explanation and a pointer to system settings. Everything about this step
 * is skippable by simply pressing Continue.
 */
@Composable
fun FocusReminderScreen(
    reminderEnabled: Boolean,
    canPostNotifications: Boolean,
    onToggleReminder: (Boolean) -> Unit,
    onContinue: () -> Unit,
) {
    // Denial is kept visible but quiet; never re-prompts, never persists guilt.
    var showDenied by remember { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) onToggleReminder(true) else showDenied = true
    }
    val handleToggle: (Boolean) -> Unit = { enable ->
        when {
            !enable -> {
                showDenied = false
                onToggleReminder(false)
            }
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                !canPostNotifications -> {
                notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
            else -> onToggleReminder(true)
        }
    }
    StepScaffold(
        step = 3,
        content = {
            Text(
                text = "Focus, gently",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(SumiSpacing.md))
            Text(
                text = "Sumi can nudge you once a day with a quiet question about taking a focused " +
                    "break. That is the only notification it will ever send you, and only if you " +
                    "ask for it. Skip it, or turn it on any time — in the Focus tab.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(SumiSpacing.lg))
            // Switch row per reference standard §9.7: switch aligned to the title line,
            // full row tappable.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = SumiSpacing.minTouchTarget)
                    .clickable(onClick = { handleToggle(!reminderEnabled) })
                    .semantics(mergeDescendants = true) {
                        role = Role.Switch
                        stateDescription = if (reminderEnabled) "on" else "off"
                    }
                    .padding(vertical = SumiSpacing.xs),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f).padding(end = SumiSpacing.md)) {
                    Text(
                        text = "Once-a-day Focus reminder",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(SumiSpacing.xs))
                    Text(
                        text = if (reminderEnabled) {
                            "On · 09:00 — change it any time in the Focus tab."
                        } else {
                            "Off. You can turn it on any time in the Focus tab."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (showDenied) {
                        Spacer(Modifier.height(SumiSpacing.sm))
                        Text(
                            text = "Notifications are off for Sumi, so the reminder stays off. " +
                                "You can allow them any time in system settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Switch(
                    checked = reminderEnabled,
                    onCheckedChange = handleToggle,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        },
        action = { SumiPrimaryButton(label = "Continue", onClick = onContinue) },
    )
}

@Composable
fun UsageAccessScreen(
    uiState: UsageAccessUiState,
    onOpenSettings: () -> Unit,
    onContinue: () -> Unit,
) {
    StepScaffold(
        step = 4,
        content = {
            Text(
                text = "Usage Access",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(SumiSpacing.md))
            Text(
                text = "Android requires this special permission so Sumi can read your usage " +
                    "statistics. It is granted in system Settings. Sumi cannot do anything without it.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(SumiSpacing.xl))
            // Status is icon + text, neutral when off (never red) — reference standard §9.8.
            when (uiState) {
                UsageAccessUiState.Granted -> StatusIndicator(
                    text = "Usage access is on.",
                    tone = StatusTone.Affirmative,
                )
                UsageAccessUiState.Missing -> {
                    StatusIndicator(
                        text = "Usage access is off.",
                        tone = StatusTone.Neutral,
                    )
                    Spacer(Modifier.height(SumiSpacing.sm))
                    SumiTextLink(text = "Open system Settings", onClick = onOpenSettings)
                }
                UsageAccessUiState.Checking -> ProgressModule(
                    progress = null,
                    liveLine = "Checking…",
                )
            }
        },
        action = {
            // Honest gate: Continue stays inert until the permission is actually granted.
            SumiPrimaryButton(
                label = "Continue",
                onClick = onContinue,
                enabled = uiState == UsageAccessUiState.Granted,
            )
        },
    )
}

/**
 * Final import step (reference standard §10.3). The import starts when the screen appears
 * and the user waits honestly: progress module + disabled CTA. Ready → affirmative and
 * Continue enabled; failure → attention status + Retry; nothing to import → skip the
 * wait with a quiet line.
 */
@Composable
fun InitialSyncScreen(
    permissionState: UsageAccessUiState,
    importState: InitialSyncUiState,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
    onStartImport: () -> Unit,
    onContinue: () -> Unit,
) {
    LaunchedEffect(permissionState, importState) {
        if (permissionState == UsageAccessUiState.Granted &&
            importState is InitialSyncUiState.NotStarted
        ) {
            onStartImport()
        }
    }
    StepScaffold(
        step = 5,
        content = {
            Text(
                text = "Preparing your history",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(SumiSpacing.md))
            Text(
                text = "Sumi is importing your past usage from the system. " +
                    "This happens once, on your device.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(SumiSpacing.xl))
            when {
                permissionState == UsageAccessUiState.Checking -> ProgressModule(
                    progress = null,
                    liveLine = "Checking…",
                )
                permissionState == UsageAccessUiState.Missing -> {
                    StatusIndicator(
                        text = "Usage access is off.",
                        tone = StatusTone.Neutral,
                    )
                    Spacer(Modifier.height(SumiSpacing.sm))
                    SumiTextLink(text = "Open system Settings", onClick = onOpenSettings)
                }
                importState is InitialSyncUiState.Importing ||
                    importState is InitialSyncUiState.NotStarted -> ProgressModule(
                        progress = null,
                        liveLine = "Importing…",
                    )
                importState is InitialSyncUiState.Ready -> StatusIndicator(
                    text = "History is on this device.",
                    tone = StatusTone.Affirmative,
                )
                importState is InitialSyncUiState.ReadyEmpty -> StatusIndicator(
                    text = "Nothing to import yet — Sumi starts counting today.",
                    tone = StatusTone.Affirmative,
                )
                importState is InitialSyncUiState.Failed -> {
                    StatusIndicator(
                        text = "Import stopped before finishing.",
                        tone = StatusTone.Attention,
                    )
                    Spacer(Modifier.height(SumiSpacing.sm))
                    SumiTextLink(text = "Retry", onClick = onRetry)
                }
            }
        },
        action = {
            val ready = importState is InitialSyncUiState.Ready ||
                importState is InitialSyncUiState.ReadyEmpty
            SumiPrimaryButton(
                label = "Continue",
                onClick = onContinue,
                enabled = ready,
            )
        },
    )
}

/**
 * Completion moment (reference standard §10.1). Serif speaks here: the one quiet flourish
 * of the app. Setup is complete by the time this screen shows — the button only opens
 * the app.
 */
@Composable
fun ReadyScreen(onOpenApp: () -> Unit) {
    StepScaffold(
        content = {
            Text(
                text = "Ready.",
                style = MaterialTheme.typography.displayLarge,
            )
            Spacer(Modifier.height(SumiSpacing.md))
            Text(
                text = "Your history is on this device. From here, Sumi stays quiet and counts.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        action = { SumiPrimaryButton(label = "Enter Sumi", onClick = onOpenApp) },
    )
}

