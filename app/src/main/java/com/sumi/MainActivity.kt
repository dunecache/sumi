package com.sumi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.sumi.core.datastore.PreferencesRepository
import com.sumi.core.datastore.ThemeMode
import com.sumi.core.designsystem.theme.SumiTheme
import com.sumi.feature.onboarding.OnboardingViewModel
import com.sumi.feature.usage.UsageCollector
import com.sumi.navigation.SumiApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val onboardingViewModel: OnboardingViewModel by viewModels()

    @Inject lateinit var preferences: PreferencesRepository

    @Inject lateinit var usageCollector: UsageCollector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Collect on open; re-check Usage Access + collect on resume.
        // repeatOnLifecycle also fires when this activity starts; no second standalone collect.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                onboardingViewModel.refreshUsageAccess()
                usageCollector.collectOnForeground()
            }
        }

        setContent {
            // null until DataStore emits: startDestination must be correct on frame one.
            val onboardingCompleted by preferences.onboardingCompleted.collectAsState(initial = null)
            val themeMode by preferences.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            SumiTheme(darkTheme = darkTheme) {
                when (val completed = onboardingCompleted) {
                    null -> {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(
                                    if (darkTheme) {
                                        com.sumi.core.designsystem.theme.SumiColorTokens.BackgroundDark
                                    } else {
                                        com.sumi.core.designsystem.theme.SumiColorTokens.BackgroundLight
                                    }
                                ),
                        )
                    }
                    else -> SumiApp(
                        isOnboardingCompleted = completed,
                        themeMode = themeMode,
                        onboardingViewModel = onboardingViewModel,
                        onThemeSelected = { mode ->
                            lifecycleScope.launch { preferences.setThemeMode(mode) }
                        },
                    )
                }
            }
        }
    }
}
