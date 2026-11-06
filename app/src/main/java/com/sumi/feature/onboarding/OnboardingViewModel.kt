package com.sumi.feature.onboarding

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sumi.core.datastore.PreferencesRepository
import com.sumi.feature.usage.UsageCollector
import com.sumi.platform.usage.UsageAccessChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

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

    fun completeOnboarding() {
        viewModelScope.launch {
            preferences.setOnboardingCompleted(true)
            collector.collectOnForeground()
        }
    }
}
