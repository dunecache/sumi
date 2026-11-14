package com.sumi.feature.focus

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import com.sumi.core.database.entity.FocusOutcome
import com.sumi.core.designsystem.component.LoadingState
import com.sumi.core.designsystem.component.SumiCard
import com.sumi.core.designsystem.component.SumiSectionHeader
import com.sumi.core.designsystem.component.formatDuration
import com.sumi.core.designsystem.theme.NumericLarge
import com.sumi.core.designsystem.theme.SumiSpacing
import com.sumi.core.designsystem.theme.sumiExtendedColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Focus — a quiet room to enter. Optional, calm, and friendly to step away from.
 *
 * Sections (§16): intention for today (stored per local day), a session launcher that
 * remembers your last duration, a full-screen active state, an honest history, and one
 * optional once-a-day reminder that is off by default. Sessions never touch the daily
 * goal — that instrument lives on Today.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen(
    viewModel: FocusViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val phase = state.phase

    if (state.isLoading) {
        LoadingState(description = "Loading your focus")
        return
    }

    // §16.4 — dedicated calm state while a session is alive; deliberately takes the screen.
    // Back keeps you here inside the app; system back simply leaves the summary visible.
    if (phase is FocusPhase.Active) {
        ActiveSessionView(
            phase = phase,
            intention = state.intentionSaved,
            canSeeDrift = state.hasUsageAccess,
            onEndEarly = viewModel::onEndSessionEarly,
        )
        return
    }

    var showTimePicker by remember { mutableStateOf(false) }

    // AGENTS.md §9 / §16.9: show the runtime prompt only when the user opts in — never at launch.
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.onReminderToggle(true)
        // Denied: reminder stays off; the card explains and links to system settings.
    }

    val onReminderToggle: (Boolean) -> Unit = { enabling ->
        if (!enabling) {
            viewModel.onReminderToggle(false)
        } else if (
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            !state.canPostNotifications
        ) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.onReminderToggle(true)
        }
    }

    if (showTimePicker) {
        val timeState = rememberTimePickerState(
            initialHour = state.reminderMinuteOfDay / 60,
            initialMinute = state.reminderMinuteOfDay % 60,
            is24Hour = true,
        )
        TimePickerCompose(
            state = timeState,
            onDismiss = { showTimePicker = false },
            onConfirm = { minutes ->
                viewModel.onReminderTimeChange(minutes)
                showTimePicker = false
            },
        )
    }

    FocusContent(
        state = state,
        onStartEditingIntention = viewModel::onStartEditingIntention,
        onIntentionDraftChange = viewModel::onIntentionDraftChange,
        onSaveIntention = viewModel::onSaveIntention,
        onDismissIntentionEditor = viewModel::onDismissIntentionEditor,
        onClearIntention = viewModel::onClearIntention,
        onDurationChange = viewModel::onDurationChange,
        onStartSession = viewModel::onStartSession,
        onReminderToggle = onReminderToggle,
        onAdjustReminderTime = { showTimePicker = true },
        onOpenNotificationSettings = viewModel::onOpenNotificationSettings,
    )
}

/** Stateless populated Focus screen — extracted so previews and tests can drive it (§16). */
@Composable
internal fun FocusContent(
    state: FocusUiState,
    onStartEditingIntention: () -> Unit,
    onIntentionDraftChange: (String) -> Unit,
    onSaveIntention: () -> Unit,
    onDismissIntentionEditor: () -> Unit,
    onClearIntention: () -> Unit,
    onDurationChange: (Int) -> Unit,
    onStartSession: () -> Unit,
    onReminderToggle: (Boolean) -> Unit,
    onAdjustReminderTime: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
) {
    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(horizontal = SumiSpacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(SumiSpacing.lg),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            top = SumiSpacing.lg,
            bottom = SumiSpacing.xl,
        ),
    ) {
        item {
            // Eyebrow + a calm sentence; the app bar above carries the Sumi identity.
            Text(
                text = "FOCUS",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "A quiet room to enter.",
                style = MaterialTheme.typography.displayLarge,
            )
        }

        // §16.2 — intention for today. Per local calendar day; quiet, editable, optional.
        item {
            IntentionCard(
                intention = state.intentionSaved,
                draft = state.intentionDraft,
                editing = state.intentionEditing,
                onEdit = onStartEditingIntention,
                onDraftChange = onIntentionDraftChange,
                onSave = onSaveIntention,
                onDismiss = onDismissIntentionEditor,
                onClear = onClearIntention,
            )
        }

        // §16.3 — launcher. Chips plus a 5–120 minute slider; remembers your last pick.
        item {
            LauncherCard(
                selectedMinutes = state.selectedMinutes,
                intention = state.intentionSaved,
                onDurationChange = onDurationChange,
                onStart = onStartSession,
            )
        }

        // §16.7 — honest outcome of the latest session, when there is one to tell.
        state.lastEndedSummary?.let { summary ->
            item {
                LastSessionReveal(summary = summary)
            }
        }

        // §16.8 — trailing 7 days in focus, same boundaries/formatter as Insights.
        item {
            WeeklyFocusCard(weeklyFocusMs = state.weeklyFocusMs)
        }

        // §16.8 — recent sessions, honest labels, no verdicts.
        item {
            RecentSessionsCard(sessions = state.recentSessions)
        }

        // §16.9 — one once-a-day reminder, off by default, invites — never chases.
        item {
            ReminderCard(
                enabled = state.reminderEnabled,
                minuteOfDay = state.reminderMinuteOfDay,
                notificationsAllowed = state.canPostNotifications,
                onToggle = onReminderToggle,
                onAdjustTime = onAdjustReminderTime,
                onOpenNotificationSettings = onOpenNotificationSettings,
            )
        }

        item {
            Text(
                text = "The daily goal lives on Today — it counts time, not intention.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = SumiSpacing.sm),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Intention card (§16.2)
// ---------------------------------------------------------------------------

@Composable
private fun IntentionCard(
    intention: String,
    draft: String,
    editing: Boolean,
    onEdit: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
) {
    SumiCard {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SumiSectionHeader("Today's intention", modifier = Modifier.weight(1f))
            if (!editing) {
                TextButton(
                    onClick = onEdit,
                    modifier = Modifier.height(SumiSpacing.minTouchTarget),
                ) {
                    Text(if (intention.isBlank()) "Set intention" else "Edit")
                }
            }
        }
        Spacer(Modifier.height(SumiSpacing.xs))
        if (editing) {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                label = { Text("Intention") },
                placeholder = { Text("e.g. finish the chapter, no rush") },
                minLines = 1,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(SumiSpacing.xs))
            Text(
                text = "Only you see this. Saved per day; tomorrow starts blank.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(SumiSpacing.sm))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SumiSpacing.sm, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (intention.isNotBlank()) {
                    TextButton(onClick = onClear) { Text("Clear") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(onClick = onSave) { Text("Save intention") }
            }
        } else if (intention.isNotBlank()) {
            Spacer(Modifier.height(SumiSpacing.xs))
            Text(
                text = intention,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        } else {
            Spacer(Modifier.height(SumiSpacing.xs))
            Text(
                text = "Would you like to set an intention for today?",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Session launcher (§16.3)
// ---------------------------------------------------------------------------

@Composable
private fun LauncherCard(
    selectedMinutes: Int,
    intention: String,
    onDurationChange: (Int) -> Unit,
    onStart: () -> Unit,
) {
    SumiCard {
        SumiSectionHeader("Session")
        Spacer(Modifier.height(SumiSpacing.xs))
        Text(
            text = "Focus, for a while. No streak, no score.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(SumiSpacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(SumiSpacing.sm)) {
            PRESET_MINUTES.forEach { minutes ->
                FilterChip(
                    selected = selectedMinutes == minutes,
                    onClick = { onDurationChange(minutes) },
                    label = { Text("${minutes}m") },
                )
            }
        }
        Spacer(Modifier.height(SumiSpacing.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Custom",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            // The value readout repeats the preset chip when one is selected; show it
            // only for a genuinely custom value. TalkBack still hears the slider's
            // contentDescription either way.
            if (selectedMinutes !in PRESET_MINUTES) {
                Text(
                    text = "$selectedMinutes min",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Slider(
            value = selectedMinutes.toFloat(),
            onValueChange = { onDurationChange(it.toInt()) },
            valueRange = DURATION_MIN.toFloat()..DURATION_MAX.toFloat(),
            steps = (DURATION_MAX - DURATION_MIN) / DURATION_STEP - 1,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "Session length, $selectedMinutes minutes"
                },
        )
        if (intention.isNotBlank()) {
            Spacer(Modifier.height(SumiSpacing.xs))
            Text(
                text = intention,
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(SumiSpacing.md))
        Button(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .height(SumiSpacing.minTouchTarget),
        ) {
            Text("Begin a session")
        }
    }
}

// ---------------------------------------------------------------------------
// Active session (§16.4) — dedicated calm full screen
// ---------------------------------------------------------------------------

@Composable
internal fun ActiveSessionView(
    phase: FocusPhase.Active,
    intention: String,
    canSeeDrift: Boolean,
    onEndEarly: () -> Unit,
) {
    val remaining = phase.remainingMs
    val totalSeconds = (remaining / 1_000L).coerceAtLeast(0L)
    val minutes = (totalSeconds / 60).toInt()
    val seconds = (totalSeconds % 60).toInt()
    val timeText = "%02d:%02d".format(Locale.US, minutes, seconds)
    val extended = sumiExtendedColors()

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = SumiSpacing.screenHorizontal),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SumiSpacing.lg, Alignment.CenterVertically),
    ) {
        if (intention.isNotBlank()) {
            Text(
                text = intention,
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            text = timeText,
            style = NumericLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics {
                // On-demand announcement only — TalkBack reads this when focused; no live region.
                contentDescription = if (minutes > 0) "Time left: $minutes minutes" else "Time left: under a minute"
            },
        )
        LinearProgressIndicator(
            progress = { phase.progressFraction.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            trackColor = extended.matchaTrack,
            color = MaterialTheme.colorScheme.primary,
        )
        if (canSeeDrift) {
            if (phase.exits > 0) {
                Text(
                    text = "${phase.exits} gentle ${if (phase.exits == 1) "exit" else "exits"} so far",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = "Staying with it",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Text(
                text = "Usage access is off — drift awareness is paused; the session still works.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        OutlinedButton(
            onClick = onEndEarly,
            modifier = Modifier.height(SumiSpacing.minTouchTarget),
        ) {
            Text("End early")
        }
    }
}

// ---------------------------------------------------------------------------
// Post-session reveal (§16.7)
// ---------------------------------------------------------------------------

@Composable
private fun LastSessionReveal(summary: String) {
    SumiCard {
        SumiSectionHeader("Last session")
        Spacer(Modifier.height(SumiSpacing.xs))
        Text(
            text = summary,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ---------------------------------------------------------------------------
// History (§16.8)
// ---------------------------------------------------------------------------

@Composable
private fun WeeklyFocusCard(weeklyFocusMs: Long) {
    SumiCard {
        SumiSectionHeader("This week")
        Spacer(Modifier.height(SumiSpacing.xs))
        if (weeklyFocusMs > 0L) {
            Text(
                text = "${formatDuration(weeklyFocusMs)} in focus over the last 7 days",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        } else {
            Text(
                text = "No sessions yet — that's fine.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(SumiSpacing.xs))
        Text(
            text = "Trailing 7-day window, same boundaries as Insights.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RecentSessionsCard(sessions: List<FocusHistoryRow>) {
    SumiCard {
        SumiSectionHeader("Recent sessions")
        Spacer(Modifier.height(SumiSpacing.xs))
        if (sessions.isEmpty()) {
            Text(
                text = "No sessions yet — that's fine.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val zone = ZoneId.systemDefault()
            val formatter = DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.getDefault())
            sessions.forEachIndexed { index, row ->
                if (index > 0) {
                    Spacer(Modifier.height(SumiSpacing.sm))
                }
                val started = Instant.ofEpochMilli(row.startedAtUtcMs).atZone(zone)
                val exitsLabel = if (row.exits == 0) {
                    "no gentle exits"
                } else {
                    "${row.exits} gentle ${if (row.exits == 1) "exit" else "exits"}"
                }
                val interrupted = row.outcome == FocusOutcome.INTERRUPTED || row.outcome == FocusOutcome.REBOOT
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = started.format(formatter),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (interrupted) {
                            Text(
                                text = if (row.outcome == FocusOutcome.REBOOT) {
                                    "Ended when the phone restarted"
                                } else {
                                    "Couldn't confirm how it ended"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        text = if (row.outcome == FocusOutcome.INTERRUPTED) {
                            "${row.intendedMinutes}m aim · duration unknown · $exitsLabel"
                        } else {
                            "${row.intendedMinutes}m aim · ${row.actualMinutes}m · $exitsLabel"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Reminder (§16.9) — optional, once-a-day, never chases
// ---------------------------------------------------------------------------

@Composable
private fun ReminderCard(
    enabled: Boolean,
    minuteOfDay: Int,
    notificationsAllowed: Boolean,
    onToggle: (Boolean) -> Unit,
    onAdjustTime: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
) {
    SumiCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                SumiSectionHeader("Reminder")
                Spacer(Modifier.height(SumiSpacing.xs))
                Text(
                    text = "Once a day, at a time that suits you. Time for a session?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                // Never enabled while permission is missing; the tap goes to the system
                // prompt (see onReminderToggle) — the switch reflects real state.
                checked = enabled && notificationsAllowed,
                onCheckedChange = onToggle,
                enabled = true,
            )
        }
        if (!notificationsAllowed) {
            Spacer(Modifier.height(SumiSpacing.sm))
            Text(
                text = "Notifications are off for Sumi — the reminder stays silent.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = onOpenNotificationSettings,
                modifier = Modifier.height(SumiSpacing.minTouchTarget),
            ) {
                Text("Open notification settings")
            }
        } else if (enabled) {
            Spacer(Modifier.height(SumiSpacing.sm))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Daily at ${formatMinuteOfDay(minuteOfDay)}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = onAdjustTime,
                    modifier = Modifier.height(SumiSpacing.minTouchTarget),
                ) {
                    Text("Adjust")
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerCompose(
    state: androidx.compose.material3.TimePickerState,
    onDismiss: () -> Unit,
    onConfirm: (minuteOfDay: Int) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("Set time") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        text = { TimePicker(state = state) },
    )
}

private fun formatMinuteOfDay(minuteOfDay: Int): String =
    "%02d:%02d".format(Locale.getDefault(), minuteOfDay / 60, minuteOfDay % 60)

private val PRESET_MINUTES = listOf(15, 25, 45)
private const val DURATION_MIN = 5
private const val DURATION_MAX = 120
private const val DURATION_STEP = 5
