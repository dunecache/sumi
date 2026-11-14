package com.sumi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import com.sumi.platform.notifications.SumiNotifications
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val onboardingViewModel: OnboardingViewModel by viewModels()

    @Inject lateinit var preferences: PreferencesRepository

    @Inject lateinit var usageCollector: UsageCollector

    /**
     * Monotonic counter of "open the Focus tab" requests, bumped by notification taps
     * (ongoing session notification and reminder). Composed into [SumiApp]; the nav
     * graph navigates to Focus once per increment.
     */
    private var openFocusRequest by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-edge: Sumi draws behind the system bars; Scaffold + M3 components
        // consume insets. Light/dark icon appearance follows the composed theme.
        enableEdgeToEdge()
        consumeFocusIntent(intent)

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
                                .background(MaterialTheme.colorScheme.background),
                        )
                    }
                    else -> SumiApp(
                        isOnboardingCompleted = completed,
                        themeMode = themeMode,
                        onboardingViewModel = onboardingViewModel,
                        openFocusRequest = openFocusRequest,
                        onThemeSelected = { mode ->
                            lifecycleScope.launch { preferences.setThemeMode(mode) }
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeFocusIntent(intent)
    }

    private fun consumeFocusIntent(intent: android.content.Intent?) {
        if (intent?.getBooleanExtra(SumiNotifications.EXTRA_OPEN_FOCUS, false) == true) {
            openFocusRequest += 1
            intent.removeExtra(SumiNotifications.EXTRA_OPEN_FOCUS)
        }
    }
}
