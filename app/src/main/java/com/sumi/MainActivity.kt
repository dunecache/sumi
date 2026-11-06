package com.sumi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.sumi.core.datastore.PreferencesRepository
import com.sumi.core.datastore.ThemeMode
import com.sumi.core.designsystem.theme.SumiTheme
import com.sumi.feature.onboarding.OnboardingViewModel
import com.sumi.navigation.SumiApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val onboardingViewModel: OnboardingViewModel by viewModels()

    @Inject lateinit var preferences: PreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Re-check Usage Access on resume via the VM observing Lifecycle.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                onboardingViewModel.refreshUsageAccess()
            }
        }

        setContent {
            val onboardingCompleted by preferences.onboardingCompleted.collectAsState(initial = false)
            val themeMode by preferences.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            SumiTheme(darkTheme = darkTheme) {
                SumiApp(
                    isOnboardingCompleted = onboardingCompleted,
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
