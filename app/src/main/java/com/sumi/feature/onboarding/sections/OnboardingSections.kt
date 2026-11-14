package com.sumi.feature.onboarding.sections

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.sumi.core.designsystem.component.EmptyState
import com.sumi.core.designsystem.component.LoadingState
import com.sumi.core.designsystem.theme.SumiSpacing
import com.sumi.feature.onboarding.UsageAccessUiState

// Onboarding: Welcome → Privacy → Focus (optional) → Usage Access → Initial sync →
// First overview (preview).
// Setup completes when the initial sync finishes; the preview only opens the app.
// The only permission ask in onboarding is the Focus reminder opt-in, and it fires only
// when the user flips the switch — never on its own (PRINCIPLES.md, AGENTS.md §9).

@Composable
private fun StepLabel(step: Int) {
    Text(
        text = "Setup · $step of 5",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun WelcomeScreen(onNext: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(SumiSpacing.screenHorizontal)) {
        StepLabel(1)
        Spacer(Modifier.height(SumiSpacing.section))
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
        Spacer(Modifier.weight(1f))
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
        TextButton(onClick = {}, modifier = Modifier.fillMaxWidth()) {
            Text("What Sumi does not do")
        }
        Spacer(Modifier.height(SumiSpacing.xl))
    }
}

@Composable
fun PrivacyScreen(onNext: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(SumiSpacing.screenHorizontal)) {
        StepLabel(2)
        Spacer(Modifier.height(SumiSpacing.section))
        Text(text = "Private by default", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(SumiSpacing.md))
        Text(
            text = "Sumi processes and stores your usage on this device only. " +
                "There is no account, no analytics, and no network access.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.weight(1f))
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
        Spacer(Modifier.height(SumiSpacing.xl))
    }
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
    Column(Modifier.fillMaxSize().padding(SumiSpacing.screenHorizontal)) {
        StepLabel(3)
        Spacer(Modifier.height(SumiSpacing.section))
        Text(text = "Focus, gently", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(SumiSpacing.md))
        Text(
            text = "Sumi can nudge you once a day with a quiet question about taking a focused " +
                "break. That is the only notification it will ever send you, and only if you " +
                "ask for it. Skip it, or turn it on any time — in the Focus tab.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(SumiSpacing.lg))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = SumiSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SumiSpacing.md),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Once-a-day Focus reminder",
                    style = MaterialTheme.typography.bodyMedium,
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
            }
            Switch(checked = reminderEnabled, onCheckedChange = handleToggle)
        }
        if (showDenied) {
            Spacer(Modifier.height(SumiSpacing.xs))
            Text(
                text = "Notifications are off for Sumi, so the reminder stays off. " +
                    "You can allow them any time in system settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(1f))
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
        Spacer(Modifier.height(SumiSpacing.xl))
    }
}

@Composable
fun UsageAccessScreen(
    uiState: UsageAccessUiState,
    onOpenSettings: () -> Unit,
    onContinue: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(SumiSpacing.screenHorizontal)) {
        StepLabel(4)
        Spacer(Modifier.height(SumiSpacing.section))
        Text(text = "Usage Access", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(SumiSpacing.md))
        Text(
            text = "Android requires this special permission so Sumi can read your usage " +
                "statistics. It is granted in system Settings. Sumi cannot do anything without it.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(SumiSpacing.lg))
        when (uiState) {
            UsageAccessUiState.Granted -> Text(
                text = "Usage Access is enabled.",
                style = MaterialTheme.typography.bodyMedium,
            )
            UsageAccessUiState.Missing -> Button(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Open system settings") }
            UsageAccessUiState.Checking -> LoadingState()
        }
        Spacer(Modifier.weight(1f))
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
        Spacer(Modifier.height(SumiSpacing.xl))
    }
}

@Composable
fun InitialSyncScreen(
    uiState: UsageAccessUiState,
    onFinish: () -> Unit,
) {
    when (uiState) {
        UsageAccessUiState.Granted -> Column(
            Modifier.fillMaxSize().padding(SumiSpacing.screenHorizontal),
        ) {
            StepLabel(5)
            Spacer(Modifier.height(SumiSpacing.section))
            Text(text = "Preparing your history", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(SumiSpacing.md))
            Text(
                text = "Sumi is importing your past usage from the system. " +
                    "This happens once, on your device.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.weight(1f))
            Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
            Spacer(Modifier.height(SumiSpacing.xl))
        }
        UsageAccessUiState.Missing -> EmptyState(
            title = "Usage Access is off",
            message = "Sumi starts learning your day from now. Enable Usage Access to see your history.",
        )
        UsageAccessUiState.Checking -> LoadingState()
    }
}

/** Final onboarding screen: a preview of Today, clearly framed as "setup complete".
 *  Onboarding is already persisted by the time this screen shows — the button only
 *  opens the app. */
@Composable
fun FirstOverviewScreen(onOpenApp: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(SumiSpacing.screenHorizontal)) {
        Text(
            text = "Setup complete",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(SumiSpacing.section))
        Text(text = "This is Today.", style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(SumiSpacing.md))
        Text(
            text = "Once you have used your phone a little, your day — screen time, " +
                "most used apps, and rhythm — appears on this screen.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Button(onClick = onOpenApp, modifier = Modifier.fillMaxWidth()) { Text("Open Sumi") }
        Spacer(Modifier.height(SumiSpacing.xl))
    }
}

@Composable
fun InitialSyncTrigger(onConfirm: () -> Unit) {
    LaunchedEffect(Unit) { onConfirm() }
}
