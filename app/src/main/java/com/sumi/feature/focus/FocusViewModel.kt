package com.sumi.feature.focus

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sumi.core.common.AppDispatchers
import com.sumi.core.datastore.PreferencesRepository
import com.sumi.data.focus.FocusSessionRepository
import com.sumi.domain.common.Clock
import com.sumi.domain.common.DateKey
import com.sumi.domain.common.TimeZoneProvider
import com.sumi.domain.focus.FocusMath
import com.sumi.platform.focus.FocusSessionService
import com.sumi.platform.notifications.FocusReminderScheduler
import com.sumi.platform.usage.UsageAccessChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One row in the Focus history list — intentionally detail-light (§16.8). */
data class FocusHistoryRow(
    val id: Long,
    val startedAtUtcMs: Long,
    val intendedMinutes: Int,
    /** Actual elapsed time in whole minutes, clamped at ≥ 0. */
    val actualMinutes: Int,
    val exits: Int,
    /** See FocusOutcome — drives the honest interrupted/reboot label in the list. */
    val outcome: String,
)

sealed interface FocusPhase {
    data object Idle : FocusPhase
    /** A session is alive; the screen ticks with the countdown. */
    data class Active(
        val sessionId: Long,
        val intendedMinutes: Int,
        val startedAtUtcMs: Long,
        val exits: Int,
        val remainingMs: Long,
        val progressFraction: Float,
    ) : FocusPhase
}

data class FocusUiState(
    val isLoading: Boolean = true,
    val hasUsageAccess: Boolean = true,
    val intentionSaved: String = "",
    val intentionDraft: String = "",
    val intentionEditing: Boolean = false,
    val selectedMinutes: Int = 25,
    val phase: FocusPhase = FocusPhase.Idle,
    /** Neutral summary of the just-finished session; hidden after the user taps Got it. */
    val lastEndedSummary: String? = null,
    /** Trailing 7-day elapsed time in sessions (ms). */
    val weeklyFocusMs: Long = 0L,
    /** POST_NOTIFICATIONS granted (Android 13+) or OS < 13. */
    val canPostNotifications: Boolean = true,
    val reminderEnabled: Boolean = false,
    val reminderMinuteOfDay: Int = 9 * 60,
    /** Up to the latest few closed sessions for the history list, newest first. */
    val recentSessions: List<FocusHistoryRow> = emptyList(),
)

@HiltViewModel
class FocusViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val focusSessions: FocusSessionRepository,
    private val preferences: PreferencesRepository,
    private val clock: Clock,
    private val timeZone: TimeZoneProvider,
    private val checker: UsageAccessChecker,
    private val reminderScheduler: FocusReminderScheduler,
    private val dispatchers: AppDispatchers,
) : ViewModel() {

    private val _intentionDraft = MutableStateFlow("")
    private val _intentionEditing = MutableStateFlow(false)
    private val _selectedMinutes = MutableStateFlow(25)
    private val _weeklyFocusMs = MutableStateFlow(0L)
    private val _nowUtcMs = MutableStateFlow(clock.nowUtcMs())
    // Usage access changes only via a trip to Settings; it needs no 1 Hz cadence.
    private val _hasUsageAccess = MutableStateFlow(checker.hasUsageAccess())

    private val timerFlow = kotlinx.coroutines.flow.flow {
        var tick = 0
        while (kotlinx.coroutines.currentCoroutineContext().isActive) {
            _nowUtcMs.value = clock.nowUtcMs()
            if (tick++ % PERMISSION_REFRESH_EVERY_TICKS == 0) {
                _hasUsageAccess.value = checker.hasUsageAccess()
            }
            delay(FocusMath.POLL_INTERVAL_MS)
        }
    }

    private val sessionStream = focusSessions.observeOpen()
    private val historyStream = focusSessions.observeHistory()

    init {
        viewModelScope.launch { timerFlow.collect { } }
        viewModelScope.launch {
            preferences.focusLastDurationMinutes.collect { _selectedMinutes.value = it }
        }
        viewModelScope.launch {
            // Recompute the week roll-up whenever the history changes (session end) or once at start.
            historyStream.collect {
                _weeklyFocusMs.value = withContext(dispatchers.io) {
                    focusSessions.weeklyFocusMs(clock.nowUtcMs())
                }
            }
        }
    }

    private val baseA = combine(
        sessionStream,
        preferences.dailyIntention,
        _nowUtcMs,
        _selectedMinutes,
        _intentionEditing,
    ) { session, intention, now, selected, editing ->
        BaseA(session, intention, now, selected, editing)
    }

    private val baseB = combine(
        preferences.focusReminderEnabled,
        preferences.focusReminderMinuteOfDay,
        _weeklyFocusMs,
        historyStream,
    ) { reminderEnabled, reminderMinute, weeklyMs, history ->
        val latestEnded = history.firstOrNull { it.endedAtUtcMs != null }
        val summary = latestEnded?.let { ended ->
            val actualMs = ((ended.endedAtUtcMs ?: 0L) - ended.startedAtUtcMs).coerceAtLeast(0L)
            FocusMath.summaryText(ended.intendedMinutes, actualMs, ended.exitCount)
        }
        BaseB(
            reminderEnabled = reminderEnabled,
            reminderMinuteOfDay = reminderMinute,
            weeklyMs = weeklyMs,
            lastEndedSummary = summary,
            recentSessions = history.take(RECENT_HISTORY_ROWS).map { session ->
                val endedAt = session.endedAtUtcMs
                val actualMs = if (endedAt != null) (endedAt - session.startedAtUtcMs).coerceAtLeast(0L) else 0L
                FocusHistoryRow(
                    id = session.id,
                    startedAtUtcMs = session.startedAtUtcMs,
                    intendedMinutes = session.intendedMinutes,
                    actualMinutes = (actualMs / 60_000L).toInt(),
                    exits = session.exitCount,
                    outcome = session.outcome,
                )
            },
        )
    }

    val uiState: StateFlow<FocusUiState> = combine(
        baseA,
        baseB,
        _intentionDraft,
        _hasUsageAccess,
    ) { a, b, draft, hasAccess ->
        val zone = timeZone.currentZone()
        val todayKey = DateKey.of(a.nowMs, zone)
        val intentionSaved = if (a.intention.dateKey == todayKey) a.intention.text else ""
        val phase = a.session?.let {
            FocusPhase.Active(
                sessionId = it.id,
                intendedMinutes = it.intendedMinutes,
                startedAtUtcMs = it.startedAtUtcMs,
                exits = it.exitCount,
                remainingMs = FocusMath.remainingMs(it.startedAtUtcMs, it.intendedMinutes, a.nowMs),
                progressFraction = FocusMath.progressFraction(it.startedAtUtcMs, it.intendedMinutes, a.nowMs),
            )
        } ?: FocusPhase.Idle
        val canNotify = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        FocusUiState(
            isLoading = false,
            hasUsageAccess = hasAccess,
            intentionSaved = intentionSaved,
            intentionDraft = draft,
            intentionEditing = a.editing,
            selectedMinutes = a.selectedMinutes,
            phase = phase,
            lastEndedSummary = b.lastEndedSummary,
            weeklyFocusMs = b.weeklyMs,
            canPostNotifications = canNotify,
            reminderEnabled = b.reminderEnabled,
            reminderMinuteOfDay = b.reminderMinuteOfDay,
            recentSessions = b.recentSessions,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FocusUiState())

    // ---- Intention ----

    fun onStartEditingIntention() {
        val zone = timeZone.currentZone()
        val todayKey = DateKey.of(clock.nowUtcMs(), zone)
        viewModelScope.launch {
            // Prefill only if the stored intention belongs to *today* — never yesterday's words.
            val cfg = preferences.dailyIntention.first()
            _intentionDraft.value = if (cfg.dateKey == todayKey) cfg.text else ""
        }
        _intentionEditing.value = true
    }

    fun onIntentionDraftChange(value: String) {
        _intentionDraft.value = value.take(MAX_INTENTION_CHARS)
    }

    fun onSaveIntention() {
        val zone = timeZone.currentZone()
        val todayKey = DateKey.of(clock.nowUtcMs(), zone)
        viewModelScope.launch {
            preferences.setDailyIntention(todayKey, _intentionDraft.value)
            _intentionEditing.value = false
        }
    }

    fun onDismissIntentionEditor() {
        _intentionEditing.value = false
    }

    fun onClearIntention() {
        val zone = timeZone.currentZone()
        val todayKey = DateKey.of(clock.nowUtcMs(), zone)
        viewModelScope.launch {
            preferences.setDailyIntention(todayKey, "")
        }
    }

    // ---- Duration ----

    fun onDurationChange(minutes: Int) {
        _selectedMinutes.value = minutes.coerceIn(DURATION_MIN, DURATION_MAX)
    }

    // ---- Sessions ----

    fun onStartSession() {
        val minutes = _selectedMinutes.value
        viewModelScope.launch {
            preferences.rememberFocusDuration(minutes)
            focusSessions.startSession(minutes)
            withContext(Dispatchers.Main) {
                FocusSessionService.start(appContext)
            }
        }
    }

    fun onEndSessionEarly() {
        viewModelScope.launch {
            focusSessions.endEarly()
            FocusSessionService.stop(appContext)
        }
    }

    // ---- Reminder ----

    fun onReminderToggle(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setFocusReminderEnabled(enabled)
            if (enabled) {
                reminderScheduler.scheduleDaily(preferences.focusReminderMinuteOfDay.first())
            } else {
                reminderScheduler.cancel()
            }
        }
    }

    fun onReminderTimeChange(minuteOfDay: Int) {
        viewModelScope.launch {
            preferences.setFocusReminderMinuteOfDay(minuteOfDay)
            if (preferences.focusReminderEnabled.first()) {
                reminderScheduler.scheduleDaily(minuteOfDay)
            }
        }
    }

    fun onOpenNotificationSettings() {
        // Notification permission is a special case of Settings; deep-link into app info.
        val intent = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, appContext.packageName)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { appContext.startActivity(intent) }
    }

    private data class BaseA(
        val session: com.sumi.core.database.entity.FocusSession?,
        val intention: com.sumi.core.datastore.DailyIntentionConfig,
        val nowMs: Long,
        val selectedMinutes: Int,
        val editing: Boolean,
    )

    private data class BaseB(
        val reminderEnabled: Boolean,
        val reminderMinuteOfDay: Int,
        val weeklyMs: Long,
        val lastEndedSummary: String?,
        val recentSessions: List<FocusHistoryRow>,
    )

    private companion object {
        const val DURATION_MIN = 5
        const val DURATION_MAX = 120
        const val MAX_INTENTION_CHARS = 120
        /** Rows shown in the history list — enough to see a pattern, never a dashboard. */
        const val RECENT_HISTORY_ROWS = 4
        /** 1-of-N ticks between usage-access re-checks (≈10 s at the 1 Hz tick). */
        const val PERMISSION_REFRESH_EVERY_TICKS = 10
    }
}
