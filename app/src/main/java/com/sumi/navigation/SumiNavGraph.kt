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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.navigation.hierarchy
import com.sumi.core.datastore.ThemeMode
import com.sumi.feature.appdetail.AppDetailScreen
import com.sumi.feature.apps.AppsScreen
import com.sumi.feature.categories.CategoryDetailScreen
import com.sumi.feature.categories.CategoryManagerScreen
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
    /**
     * The exact NavDestination route pattern this tab maps to. Plain destinations use
     * [route]; a destination with path arguments (Apps, `apps?filter={filter}`) is never
     * equal to the plain string, and `destination.route` reports the pattern.
     */
    val selectionPattern: String = route,
)

// One coherent icon family: outlined when at rest, filled when selected. Consistent
// metaphors — a day for Today, a chart for Insights, a calm ring for Focus, a grid for Apps.
private val BottomDestinations = listOf(
    BottomDestination(Routes.TODAY, "Today", Icons.Outlined.WbSunny, Icons.Filled.WbSunny),
    BottomDestination(Routes.INSIGHTS, "Insights", Icons.Outlined.Insights, Icons.Filled.Insights),
    BottomDestination(Routes.FOCUS, "Focus", Icons.Outlined.CenterFocusWeak, Icons.Filled.CenterFocusStrong),
    BottomDestination(
        Routes.APPS,
        "Apps",
        Icons.Outlined.GridView,
        Icons.Filled.GridView,
        selectionPattern = Routes.appsWithFilter("{filter}"),
    ),
)

/** Bottom bar stays visible for every apps route, including the filtered deep-link variant. */
private val appsDeepLink = Routes.appsWithFilter("{filter}")
private val MainRoutes = BottomDestinations.map { it.route } + Routes.SETTINGS + appsDeepLink

// Detail/management routes render below the top bar with a back arrow; the bottom bar stays hidden.
private val SubRoutes = setOf(
    Routes.SETTINGS,
    Routes.appDetailPattern(),
    Routes.CATEGORY_MANAGER,
    Routes.categoryDetailPattern(),
)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SumiApp(
    isOnboardingCompleted: Boolean,
    themeMode: ThemeMode,
    onboardingViewModel: OnboardingViewModel,
    onThemeSelected: (ThemeMode) -> Unit,
    /** Monotonic counter of "open Focus" requests (see MainActivity); navigates once per increment. */
    openFocusRequest: Int = 0,
) {
    val navController = rememberNavController()
    // Caller composes this graph only after the flag has loaded, so the
    // start destination is correct on frame one — no re-routing band-aid.
    val startDestination = if (isOnboardingCompleted) Routes.TODAY else Routes.ONBOARDING_WELCOME

    // A notification tap (ongoing session or reminder) lands here to route into Focus.
    LaunchedEffect(openFocusRequest) {
        if (openFocusRequest > 0 && isOnboardingCompleted) {
            navController.navigateTab(Routes.FOCUS)
        }
    }

    val usageAccessState by onboardingViewModel.usageAccessState.collectAsState()
    val currentBackStackEntry = navController.currentBackStackEntryAsState().value
    val currentRoute = currentBackStackEntry?.destination?.route
    // Selection compares against the destination pattern along the whole hierarchy — an
    // argumented destination (Apps) never matches its plain route string.
    val isTabSelected: (BottomDestination) -> Boolean = { dest ->
        currentBackStackEntry?.destination?.hierarchy
            ?.any { it.route == dest.selectionPattern } == true
    }
    val showBottomBar = currentRoute in MainRoutes
    val isSubRoute = currentRoute in SubRoutes
    val showTopBar = showBottomBar || isSubRoute

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
                if (showTopBar) {
                    TopAppBar(
                        title = { Text("Sumi") },
                        actions = {
                            // Sub-routes get a back arrow; the tabbed destinations keep the gear.
                            if (isSubRoute) {
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
                                selected = isTabSelected(dest),
                                onClick = { navController.navigateTab(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (isTabSelected(dest)) {
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
                                selected = isTabSelected(dest),
                                onClick = { navController.navigateTab(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (isTabSelected(dest)) {
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
                    TodayScreen(
                        onOpenAppDetail = { pkg -> navController.navigate(Routes.appDetail(pkg)) },
                        onOpenCategoryDetail = { id -> navController.navigate(Routes.categoryDetail(id)) },
                        onOpenCategories = { navController.navigateTab(Routes.INSIGHTS) },
                    )
                }
                composable(Routes.INSIGHTS) {
                    InsightsScreen(
                        onOpenAppDetail = { pkg -> navController.navigate(Routes.appDetail(pkg)) },
                        onOpenCategoryDetail = { id, range -> navController.navigate(Routes.categoryDetail(id, range)) },
                        onReviewUncategorized = { navController.navigate(Routes.appsWithFilter("uncategorized")) },
                        onSeeAllApps = { navController.navigate(Routes.appsWithFilter("all")) },
                    )
                }
                composable(Routes.FOCUS) { FocusScreen() }
                composable(
                    route = Routes.appsWithFilter("{filter}"),
                    arguments = listOf(navArgument("filter") { nullable = true; defaultValue = null }),
                ) { backStackEntry ->
                    AppsScreen(
                        initialFilter = backStackEntry.arguments?.getString("filter"),
                        onOpenAppDetail = { pkg -> navController.navigate(Routes.appDetail(pkg)) },
                        onManageCategories = { navController.navigate(Routes.CATEGORY_MANAGER) },
                    )
                }
                composable(Routes.CATEGORY_MANAGER) { CategoryManagerScreen() }
                composable(
                    route = Routes.categoryDetailPattern(),
                    arguments = listOf(
                        navArgument("categoryId") { type = NavType.StringType },
                        navArgument("range") { nullable = true; defaultValue = null },
                    ),
                ) {
                    // The category id and optional range arrive via SavedStateHandle in CategoryDetailViewModel.
                    CategoryDetailScreen(
                        onOpenAppDetail = { pkg -> navController.navigate(Routes.appDetail(pkg)) },
                        onManageCategories = { navController.navigate(Routes.CATEGORY_MANAGER) },
                    )
                }
                composable(Routes.SETTINGS) {
                    val settingsViewModel: com.sumi.feature.settings.SettingsViewModel =
                        androidx.hilt.navigation.compose.hiltViewModel()
                    val diagnostics by settingsViewModel.diagnosticsLines.collectAsState()
                    val showSystemApps by settingsViewModel.showSystemApps.collectAsState(initial = false)
                    SettingsScreen(
                        themeMode = themeMode,
                        onThemeSelected = onThemeSelected,
                        diagnosticsLines = diagnostics,
                        showSystemApps = showSystemApps,
                        onShowSystemAppsChange = settingsViewModel::setShowSystemApps,
                    )
                }
                composable(
                    route = Routes.appDetailPattern(),
                    arguments = listOf(navArgument("packageName") { type = NavType.StringType }),
                ) { backStackEntry ->
                    // The package id arrives via SavedStateHandle in AppDetailViewModel.
                    AppDetailScreen()
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
