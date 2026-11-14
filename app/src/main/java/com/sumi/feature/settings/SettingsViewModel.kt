package com.sumi.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sumi.core.datastore.PreferencesRepository
import kotlinx.coroutines.flow.Flow
import com.sumi.domain.common.Clock
import com.sumi.domain.common.DateKey
import com.sumi.domain.common.TimeZoneProvider
import com.sumi.domain.usage.UsageRepository
import com.sumi.platform.usage.UsageAccessChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Local diagnostics for Settings (M5). Answering "why does Today show nothing?" without
 * guessing: permission status, onboarding state, and today's stored total. No analytics.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: UsageRepository,
    private val checker: UsageAccessChecker,
    private val preferences: PreferencesRepository,
    private val clock: Clock,
    private val timeZone: TimeZoneProvider,
) : ViewModel() {

    private val _diagnosticsLines = MutableStateFlow<List<String>>(emptyList())
    val diagnosticsLines: StateFlow<List<String>> = _diagnosticsLines.asStateFlow()

    /** Apps library: whether system apps appear mixed into the library. Default false. */
    val showSystemApps: Flow<Boolean> = preferences.showSystemApps

    fun setShowSystemApps(value: Boolean) {
        viewModelScope.launch { preferences.setShowSystemApps(value) }
    }

    init {
        viewModelScope.launch {
            val today = DateKey.of(clock.nowUtcMs(), timeZone.currentZone())
            combine(
                preferences.onboardingCompleted,
                repository.observeDailyTotal(today),
            ) { onboardingDone, todayTotal ->
                listOf(
                    "Usage access: ${if (checker.hasUsageAccess()) "granted" else "NOT granted"}",
                    "Onboarding completed: ${if (onboardingDone) "yes" else "no"}",
                    "Today's stored total: ${(todayTotal?.totalDurationMs ?: 0L) / 60_000L} min",
                    "Stored days: ${todayTotal?.dateKey ?: "none"}",
                )
            }.collect { _diagnosticsLines.value = it }
        }
    }
}
