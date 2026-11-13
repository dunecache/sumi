package com.sumi.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.CenterFocusWeak
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
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

private data class BottomDestination(
    val route: String,
    val label: String,
    val iconUnselected: ImageVector,
    val iconSelected: ImageVector,
)

// One coherent icon family: outlined when at rest, filled when selected. Consistent
// metaphors — a day for Today, a chart for Insights, a calm ring for Focus, a grid for Apps.
private val BottomDestinations = listOf(
    BottomDestination(Routes.TODAY, "Today", Icons.Outlined.WbSunny, Icons.Filled.WbSunny),
    BottomDestination(Routes.INSIGHTS, "Insights", Icons.Outlined.Insights, Icons.Filled.Insights),
    BottomDestination(Routes.FOCUS, "Focus", Icons.Outlined.CenterFocusWeak, Icons.Filled.CenterFocusStrong),
    BottomDestination(Routes.APPS, "Apps", Icons.Outlined.GridView, Icons.Filled.GridView),
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

    // 200ms cross-fades between destinations; skipped entirely when the system animator
    // scale is 0 (reduced motion), per DESIGN.md §3.4.
    val context = androidx.compose.ui.platform.LocalContext.current
    val animatorScale = remember {
        android.provider.Settings.Global.getFloat(
            context.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
    }
    val navEnter: EnterTransition = if (animatorScale == 0f) {
        EnterTransition.None
    } else {
        fadeIn(animationSpec = tween(200))
    }
    val navExit: ExitTransition = if (animatorScale == 0f) {
        ExitTransition.None
    } else {
        fadeOut(animationSpec = tween(200))
    }

    // Adaptive M3 navigation: bottom bar on compact widths, rail on expanded (≥ 600dp).
    BoxWithConstraints {
        val expanded = maxWidth >= ExpandedWidthThreshold

        Scaffold(
            topBar = {
                if (showBottomBar) {
                    TopAppBar(
                        title = { Text("Sumi") },
                        actions = {
                            // §2.2: gear on main tabs; back arrow on the settings route itself.
                            if (currentRoute == Routes.SETTINGS) {
                                IconButton(onClick = { navController.popBackStack() }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                    )
                                }
                            } else {
                                IconButton(onClick = { navController.navigate(Routes.SETTINGS) }) {
                                    Icon(
                                        imageVector = Icons.Filled.Settings,
                                        contentDescription = "Settings",
                                    )
                                }
                            }
                        },
                    )
                }
            },
            bottomBar = {
                if (showBottomBar && !expanded) {
                    // M3 NavigationBar, customized to stay understated: tonal bar surface,
                    // default-size indicator, consistent icon pair per destination.
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                        BottomDestinations.forEach { dest ->
                            NavigationBarItem(
                                selected = currentRoute == dest.route,
                                onClick = { navController.navigateTab(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (currentRoute == dest.route) {
                                            dest.iconSelected
                                        } else {
                                            dest.iconUnselected
                                        },
                                        contentDescription = dest.label,
                                    )
                                },
                                label = { Text(dest.label) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Row(
                Modifier
                    .padding(padding)
                    .fillMaxSize(),
            ) {
                if (showBottomBar && expanded) {
                    NavigationRail(containerColor = Color.Transparent) {
                        BottomDestinations.forEach { dest ->
                            NavigationRailItem(
                                selected = currentRoute == dest.route,
                                onClick = { navController.navigateTab(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (currentRoute == dest.route) {
                                            dest.iconSelected
                                        } else {
                                            dest.iconUnselected
                                        },
                                        contentDescription = dest.label,
                                    )
                                },
                                label = { Text(dest.label) },
                            )
                        }
                    }
                }
                // Editorial max width keeps long text readable on tablets.
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = startDestination,
                        enterTransition = { navEnter },
                        exitTransition = { navExit },
                        popEnterTransition = { navEnter },
                        popExitTransition = { navExit },
                        modifier = Modifier.widthIn(max = ContentMaxWidth),
                    ) {
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
                    TodayScreen()
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
    }
}

/** Tab navigation options shared by the bar and the rail (M3 adaptive guidance). */
private fun androidx.navigation.NavHostController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(Routes.TODAY) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private val ExpandedWidthThreshold = 600.dp
private val ContentMaxWidth = 640.dp
