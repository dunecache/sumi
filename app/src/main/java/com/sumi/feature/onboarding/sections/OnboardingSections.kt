package com.sumi.feature.onboarding.sections

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.sumi.core.designsystem.component.EmptyState
import com.sumi.core.designsystem.component.LoadingState
import com.sumi.core.designsystem.theme.SumiSpacing
import com.sumi.feature.onboarding.UsageAccessUiState

// Onboarding: Welcome → Privacy → Usage Access → Initial sync → First overview.
// No notification permission is requested here (PRINCIPLES.md, AGENTS.md §9).

@Composable
fun WelcomeScreen(onNext: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(SumiSpacing.screenHorizontal)) {
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

@Composable
fun UsageAccessScreen(
    uiState: UsageAccessUiState,
    onOpenSettings: () -> Unit,
    onContinue: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(SumiSpacing.screenHorizontal)) {
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

@Composable
fun FirstOverviewScreen(onFinish: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(SumiSpacing.screenHorizontal)) {
        Spacer(Modifier.height(SumiSpacing.section))
        Text(text = "Your day, in perspective.", style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(SumiSpacing.md))
        EmptyState(
            title = "No usage yet",
            message = "Sumi starts learning your day from now. Come back later.",
        )
        Spacer(Modifier.weight(1f))
        Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) { Text("Finish") }
        Spacer(Modifier.height(SumiSpacing.xl))
    }
}

@Composable
fun InitialSyncTrigger(onConfirm: () -> Unit) {
    LaunchedEffect(Unit) { onConfirm() }
}
