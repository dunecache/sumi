package com.sumi.feature.onboarding

import android.content.Context
import android.os.Build
import android.os.Build.VERSION_CODES
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sumi.core.datastore.PreferencesRepository
import com.sumi.feature.usage.UsageCollector
import com.sumi.platform.notifications.FocusReminderScheduler
import com.sumi.platform.usage.UsageAccessChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.sumi.domain.common.DateKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UsageAccessUiState {
    data object Checking : UsageAccessUiState
    data object Granted : UsageAccessUiState
    data object Missing : UsageAccessUiState
}

/**
 * One-time import state for the final onboarding step (reference standard §10.3).
 * The import starts when the screen appears; it never runs speculatively before then.
 */
sealed interface InitialSyncUiState {
    /** Screen just appeared; waiting for the permission check / import kickoff. */
    data object NotStarted : InitialSyncUiState

    /** Import running — determinate progress is not available from the system. */
    data object Importing : InitialSyncUiState

    /** Import finished and days are stored. */
    data object Ready : InitialSyncUiState

    /** Import finished and nothing was found — the app starts counting today. */
    data object ReadyEmpty : InitialSyncUiState

    /** Import threw; the user retries explicitly. */
    data object Failed : InitialSyncUiState
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val preferences: PreferencesRepository,
    private val usageAccessChecker: UsageAccessChecker,
    private val collector: UsageCollector,
    private val reminderScheduler: FocusReminderScheduler,
    private val usageRepository: com.sumi.domain.usage.UsageRepository,
    private val clock: com.sumi.domain.common.Clock,
    private val timeZone: com.sumi.domain.common.TimeZoneProvider,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    /** Once-a-day Focus reminder opt-in (AGENTS.md §9): the toggle is the ask, nothing
     * overrides it. Default false; only user-initiated flips enable it. */
    val reminderEnabled: StateFlow<Boolean> = preferences.focusReminderEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** POST_NOTIFICATIONS granted (API 33+) or not required (< API 33). Live read. */
    val canPostNotifications: Boolean
        get() =
            Build.VERSION.SDK_INT < VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    appContext,
                    android.Manifest.permission.POST_NOTIFICATIONS,
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    private val _usageAccessState = MutableStateFlow<UsageAccessUiState>(UsageAccessUiState.Checking)
    val usageAccessState: StateFlow<UsageAccessUiState> = _usageAccessState.asStateFlow()

    private companion object {
        const val HISTORY_DAYS = 90
    }

    init {
        refreshUsageAccess()
    }

    /** Re-checked on resume (MainActivity) and after returning from system Settings. */
    fun refreshUsageAccess() {
        viewModelScope.launch {
            _usageAccessState.value = if (usageAccessChecker.hasUsageAccess()) {
                UsageAccessUiState.Granted
            } else {
                UsageAccessUiState.Missing
            }
        }
    }

    fun openUsageAccessSettings() {
        appContext.startActivity(usageAccessChecker.usageAccessSettingsIntent())
    }

    fun settingsIntent() = usageAccessChecker.usageAccessSettingsIntent()

    private val _initialSyncState = MutableStateFlow<InitialSyncUiState>(InitialSyncUiState.NotStarted)

    /** State of the one-time 90-day import for the final setup step. */
    val initialSyncState: StateFlow<InitialSyncUiState> = _initialSyncState.asStateFlow()

    /** Idempotent kickoff: safe to call on every composition of the sync step. */
    fun startInitialSync() {
        if (_initialSyncState.value != InitialSyncUiState.NotStarted) return
        runInitialSync()
    }

    /** Explicit retry after a failure. */
    fun retryInitialSync() {
        if (_initialSyncState.value == InitialSyncUiState.Importing) return
        runInitialSync()
    }

    private fun runInitialSync() {
        viewModelScope.launch {
            if (!usageAccessChecker.hasUsageAccess()) {
                _initialSyncState.value = InitialSyncUiState.NotStarted
                return@launch
            }
            _initialSyncState.value = InitialSyncUiState.Importing
            try {
                collector.importHistory()
                val zone = timeZone.currentZone()
                val today = DateKey.of(clock.nowUtcMs(), zone)
                val keys = (0 until HISTORY_DAYS).map { DateKey.shift(today, -it.toLong()) }
                val stored = runCatching {
                    usageRepository.observeDailyTotalsRange(keys).first()
                }.getOrDefault(emptyList())
                _initialSyncState.value = if (stored.isEmpty()) {
                    InitialSyncUiState.ReadyEmpty
                } else {
                    InitialSyncUiState.Ready
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Throwable) {
                _initialSyncState.value = InitialSyncUiState.Failed
            }
        }
    }

    /** Reminder opt-in from onboarding; identical semantics to the Focus tab toggle. */
    fun setReminder(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setFocusReminderEnabled(enabled)
            if (enabled) {
                reminderScheduler.scheduleDaily(preferences.focusReminderMinuteOfDay.first())
            } else {
                reminderScheduler.cancel()
            }
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            preferences.setOnboardingCompleted(true)
            collector.collectOnForeground()
        }
    }
}
