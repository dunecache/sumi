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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UsageAccessUiState {
    data object Checking : UsageAccessUiState
    data object Granted : UsageAccessUiState
    data object Missing : UsageAccessUiState
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val preferences: PreferencesRepository,
    private val usageAccessChecker: UsageAccessChecker,
    private val collector: UsageCollector,
    private val reminderScheduler: FocusReminderScheduler,
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

    fun runInitialSync(onDone: () -> Unit) {
        viewModelScope.launch {
            if (usageAccessChecker.hasUsageAccess()) {
                collector.importHistory()
            }
            onDone()
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
