package com.sumi.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sumi.core.datastore.ThemeMode
import com.sumi.feature.appdetail.AppDetailScreen
import com.sumi.feature.apps.AppsScreen
import com.sumi.feature.focus.FocusScreen
import com.sumi.feature.insights.InsightsScreen
import com.sumi.feature.onboarding.OnboardingViewModel
import com.sumi.feature.onboarding.sections.FirstOverviewScreen
import com.sumi.feature.onboarding.sections.InitialSyncScreen
import com.sumi.feature.onboarding.sections.PrivacyScreen
import com.sumi.feature.onboarding.sections.UsageAccessScreen
import com.sumi.feature.onboarding.sections.WelcomeScreen
import com.sumi.feature.settings.SettingsScreen
import com.sumi.feature.today.TodayScreen

private data class BottomDestination(val route: String, val label: String, val icon: ImageVector)

private val BottomDestinations = listOf(
    BottomDestination(Routes.TODAY, "Today", Icons.Filled.Home),
    BottomDestination(Routes.INSIGHTS, "Insights", Icons.Filled.Info),
    BottomDestination(Routes.FOCUS, "Focus", Icons.Filled.Star),
    BottomDestination(Routes.APPS, "Apps", Icons.Filled.Apps),
)

private val MainRoutes = BottomDestinations.map { it.route } + Routes.SETTINGS

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SumiApp(
    isOnboardingCompleted: Boolean,
    themeMode: ThemeMode,
    onboardingViewModel: OnboardingViewModel,
    onThemeSelected: (ThemeMode) -> Unit,
) {
    val navController = rememberNavController()
    // Caller composes this graph only after the flag has loaded, so the
    // start destination is correct on frame one — no re-routing band-aid.
    val startDestination = if (isOnboardingCompleted) Routes.TODAY else Routes.ONBOARDING_WELCOME

    val usageAccessState by onboardingViewModel.usageAccessState.collectAsState()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val showBottomBar = currentRoute in MainRoutes

    Scaffold(
        topBar = {
            if (showBottomBar) {
                TopAppBar(
                    title = { Text("Sumi") },
                    actions = {
                        IconButton(onClick = { navController.navigate(Routes.SETTINGS) }) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = "Settings",
                            )
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    BottomDestinations.forEach { dest ->
                        NavigationBarItem(
                            selected = currentRoute == dest.route,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(Routes.TODAY) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(imageVector = dest.icon, contentDescription = dest.label) },
                            label = { Text(dest.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            NavHost(navController = navController, startDestination = startDestination) {
                composable(Routes.ONBOARDING_WELCOME) {
                    WelcomeScreen(onNext = { navController.navigate(Routes.ONBOARDING_PRIVACY) })
                }
                composable(Routes.ONBOARDING_PRIVACY) {
                    PrivacyScreen(onNext = { navController.navigate(Routes.ONBOARDING_USAGE_ACCESS) })
                }
                composable(Routes.ONBOARDING_USAGE_ACCESS) {
                    UsageAccessScreen(
                        uiState = usageAccessState,
                        onOpenSettings = { onboardingViewModel.openUsageAccessSettings() },
                        onContinue = { navController.navigate(Routes.ONBOARDING_INITIAL_SYNC) },
                    )
                }
                composable(Routes.ONBOARDING_INITIAL_SYNC) {
                    InitialSyncScreen(
                        uiState = usageAccessState,
                        // Sync completion itself finishes onboarding: the flag is written
                        // here, so an app kill before the preview screen cannot replay setup.
                        onFinish = {
                            onboardingViewModel.runInitialSync {
                                onboardingViewModel.completeOnboarding()
                                navController.navigate(Routes.ONBOARDING_FIRST_OVERVIEW)
                            }
                        },
                    )
                }
                composable(Routes.ONBOARDING_FIRST_OVERVIEW) {
                    FirstOverviewScreen(
                        onOpenApp = {
                            navController.navigate(Routes.TODAY) {
                                popUpTo(Routes.ONBOARDING_WELCOME) { inclusive = true }
                            }
                        },
                    )
                }
                composable(Routes.TODAY) {
                    TodayScreen(onOpenSettings = { navController.navigate(Routes.SETTINGS) })
                }
                composable(Routes.INSIGHTS) { InsightsScreen() }
                composable(Routes.FOCUS) { FocusScreen() }
                composable(Routes.APPS) {
                    AppsScreen(
                        onOpenAppDetail = { pkg -> navController.navigate(Routes.appDetail(pkg)) },
                    )
                }
                composable(Routes.SETTINGS) {
                    val settingsViewModel: com.sumi.feature.settings.SettingsViewModel =
                        androidx.hilt.navigation.compose.hiltViewModel()
                    val diagnostics by settingsViewModel.diagnosticsLines.collectAsState()
                    SettingsScreen(
                        themeMode = themeMode,
                        onThemeSelected = onThemeSelected,
                        diagnosticsLines = diagnostics,
                    )
                }
                composable(
                    route = Routes.appDetailPattern(),
                    arguments = listOf(navArgument("packageName") { type = NavType.StringType }),
                ) { backStackEntry ->
                    val pkg = backStackEntry.arguments?.getString("packageName") ?: ""
                    AppDetailScreen(packageName = pkg)
                }
            }
        }
    }
}
