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

/** Icon/text tone for a diagnostics status row. Neutral — permission off is not an error. */
enum class DiagnosticTone {
    Affirmative,
    Neutral,
}

/** One diagnostics row (reference standard §9.12): label left, value right-aligned, tabular figures. */
data class DiagnosticItem(
    val label: String,
    val value: String,
    val tone: DiagnosticTone? = null,
)

/** Export state machine for the DATA section's sheet flow (§10.4). */
sealed interface ExportState {
    data object Idle : ExportState
    data object Working : ExportState
    data class Saved(val fileName: String) : ExportState
    data object Failed : ExportState
}

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
    private val dataManager: DataManager,
) : ViewModel() {

    private val _diagnostics = MutableStateFlow<List<DiagnosticItem>>(emptyList())
    val diagnostics: StateFlow<List<DiagnosticItem>> = _diagnostics.asStateFlow()

    /** Apps library: whether system apps appear mixed into the library. Default false. */
    val showSystemApps: Flow<Boolean> = preferences.showSystemApps

    fun setShowSystemApps(value: Boolean) {
        viewModelScope.launch { preferences.setShowSystemApps(value) }
    }

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    /** Writes the CSV export; the DATA section reflects the outcome inline (§10.4). */
    fun exportData() {
        if (_exportState.value == ExportState.Working) return
        viewModelScope.launch {
            _exportState.value = ExportState.Working
            try {
                val fileName = dataManager.exportCsv()
                _exportState.value = ExportState.Saved(fileName)
            } catch (_: Throwable) {
                _exportState.value = ExportState.Failed
            }
        }
    }

    /** Back to Idle once the user dismisses the outcome (sheet closed or link pressed). */
    fun resetExportState() {
        if (_exportState.value != ExportState.Working) {
            _exportState.value = ExportState.Idle
        }
    }

    /** Deletes all usage history and preferences; the app returns to onboarding via DataStore. */
    fun deleteAllData() {
        viewModelScope.launch { dataManager.deleteAllData() }
    }

    init {
        viewModelScope.launch {
            val today = DateKey.of(clock.nowUtcMs(), timeZone.currentZone())
            val friendlyToday = friendlyDate(today)
            combine(
                preferences.onboardingCompleted,
                repository.observeDailyTotal(today),
            ) { onboardingDone, todayTotal ->
                val storedMinutes = (todayTotal?.totalDurationMs ?: 0L) / 60_000L
                listOf(
                    DiagnosticItem(
                        label = "Usage access",
                        value = if (checker.hasUsageAccess()) "Granted" else "Off",
                        tone = if (checker.hasUsageAccess()) DiagnosticTone.Affirmative else DiagnosticTone.Neutral,
                    ),
                    DiagnosticItem(
                        label = "Onboarding",
                        value = if (onboardingDone) "Completed" else "Not completed",
                        tone = if (onboardingDone) DiagnosticTone.Affirmative else DiagnosticTone.Neutral,
                    ),
                    DiagnosticItem(
                        label = "Stored today",
                        value = "$storedMinutes min",
                    ),
                    DiagnosticItem(
                        label = "Viewing day",
                        value = friendlyToday,
                    ),
                )
            }.collect { _diagnostics.value = it }
        }
    }
}

/** DateKey `YYYY-MM-DD` → `14 Nov 2026` for the diagnostics value column. */
private fun friendlyDate(dateKey: String): String = runCatching {
    val date = java.time.LocalDate.parse(dateKey)
    date.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.US))
}.getOrDefault(dateKey)
