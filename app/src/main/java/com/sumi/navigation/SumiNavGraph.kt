package com.sumi.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
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
import com.sumi.core.designsystem.theme.SumiWordmark
import com.sumi.core.designsystem.theme.sumiExtendedColors
import com.sumi.core.datastore.ThemeMode
import com.sumi.feature.appdetail.AppDetailScreen
import com.sumi.feature.apps.AppsScreen
import com.sumi.feature.categories.CategoryDetailScreen
import com.sumi.feature.categories.CategoryManagerScreen
import com.sumi.feature.focus.FocusScreen
import com.sumi.feature.insights.InsightsScreen
import com.sumi.feature.onboarding.OnboardingViewModel
import com.sumi.feature.onboarding.sections.ReadyScreen
import com.sumi.feature.onboarding.sections.FocusReminderScreen
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
// metaphors — a day for Today, a chart for Insights, a concentric target for Focus (F-13;
// the viewfinder glyph read as "scan/camera"), a grid for Apps.
private val BottomDestinations = listOf(
    BottomDestination(Routes.TODAY, "Today", Icons.Outlined.WbSunny, Icons.Filled.WbSunny),
    BottomDestination(Routes.INSIGHTS, "Insights", Icons.Outlined.Insights, Icons.Filled.Insights),
    BottomDestination(Routes.FOCUS, "Focus", Icons.Outlined.TrackChanges, Icons.Filled.TrackChanges),
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
    // Selection compares the destination and its ancestors — an argumented destination
    // (Apps, `apps?filter={filter}`) never matches its plain route string, and walking
    // parents keeps this correct if it ever becomes a nested graph.
    // The tab the user came from — tracked so stacked destinations (Settings) can keep
    // the origin tab marked (reference standard §9.4). Defaults to Today.
    val originTabRoute = remember { androidx.compose.runtime.mutableStateOf(Routes.TODAY) }
    LaunchedEffect(currentRoute) {
        val route = currentRoute
        if (route != null && route in MainRoutes) originTabRoute.value = route
    }
    val isOriginStacked = currentRoute == Routes.SETTINGS
    val isTabSelected: (BottomDestination) -> Boolean = { dest ->
        generateSequence(currentBackStackEntry?.destination) { it.parent }
            .any { it.route == dest.selectionPattern } ||
            (isOriginStacked && dest.route == originTabRoute.value)
    }
    // Settings stacks from a tab; the bottom bar stays visible with the origin tab marked.
    val showBottomBar = currentRoute in MainRoutes || currentRoute == Routes.SETTINGS
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
                    // Reference standard §9.3: root destinations carry the serif wordmark on
                    // the left plus at most one trailing action; subscreens carry a left back
                    // arrow and no wordmark. Navigation never sits on the right.
                    TopAppBar(
                        title = {
                            if (!isSubRoute) {
                                Text(
                                    text = "Sumi",
                                    style = com.sumi.core.designsystem.theme.SumiWordmark,
                                )
                            }
                        },
                        navigationIcon = {
                            // Sub-routes: back arrow on the left, always (F-04).
                            if (isSubRoute) {
                                IconButton(onClick = { navController.popBackStack() }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                    )
                                }
                            }
                        },
                        actions = {
                            // Root destinations: gear on the right (48 dp target); never a back arrow.
                            if (!isSubRoute) {
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
                    // M3 NavigationBar customized per the reference standard §9.4: surface-bar
                    // strip + hairline edge, pill + filled icon + medium label = triple-coded
                    // selected state (F-08/F-20).
                    val extended = sumiExtendedColors()
                    val navPill = extended.navPill
                    val onNavPill = extended.onNavPill
                    Column {
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                        NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 0.dp,
                    ) {
                        BottomDestinations.forEach { dest ->
                            val selected = isTabSelected(dest)
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navController.navigateTab(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) {
                                            dest.iconSelected
                                        } else {
                                            dest.iconUnselected
                                        },
                                        contentDescription = dest.label,
                                    )
                                },
                                label = {
                                    Text(
                                        dest.label,
                                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = navPill,
                                    selectedIconColor = onNavPill,
                                    selectedTextColor = onNavPill,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }
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
                    val extended = sumiExtendedColors()
                    val navPill = extended.navPill
                    val onNavPill = extended.onNavPill
                    NavigationRail(containerColor = Color.Transparent) {
                        BottomDestinations.forEach { dest ->
                            val selected = isTabSelected(dest)
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navController.navigateTab(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) {
                                            dest.iconSelected
                                        } else {
                                            dest.iconUnselected
                                        },
                                        contentDescription = dest.label,
                                    )
                                },
                                label = {
                                    Text(
                                        dest.label,
                                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                                    )
                                },
                                colors = NavigationRailItemDefaults.colors(
                                    indicatorColor = navPill,
                                    selectedIconColor = onNavPill,
                                    selectedTextColor = onNavPill,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
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
                    PrivacyScreen(onNext = { navController.navigate(Routes.ONBOARDING_FOCUS) })
                }
                composable(Routes.ONBOARDING_FOCUS) {
                    val reminderEnabled by onboardingViewModel.reminderEnabled.collectAsState()
                    FocusReminderScreen(
                        reminderEnabled = reminderEnabled,
                        canPostNotifications = onboardingViewModel.canPostNotifications,
                        onToggleReminder = onboardingViewModel::setReminder,
                        onContinue = { navController.navigate(Routes.ONBOARDING_USAGE_ACCESS) },
                    )
                }
                composable(Routes.ONBOARDING_USAGE_ACCESS) {
                    UsageAccessScreen(
                        uiState = usageAccessState,
                        onOpenSettings = { onboardingViewModel.openUsageAccessSettings() },
                        onContinue = { navController.navigate(Routes.ONBOARDING_INITIAL_SYNC) },
                    )
                }
                composable(Routes.ONBOARDING_INITIAL_SYNC) {
                    val importState by onboardingViewModel.initialSyncState.collectAsState()
                    InitialSyncScreen(
                        permissionState = usageAccessState,
                        importState = importState,
                        onOpenSettings = { onboardingViewModel.openUsageAccessSettings() },
                        onRetry = onboardingViewModel::retryInitialSync,
                        onStartImport = onboardingViewModel::startInitialSync,
                        // Sync completion itself finishes onboarding: the flag is written
                        // here, so an app kill before the "Ready." screen cannot replay setup.
                        onContinue = {
                            onboardingViewModel.completeOnboarding()
                            navController.navigate(Routes.ONBOARDING_FIRST_OVERVIEW)
                        },
                    )
                }
                composable(Routes.ONBOARDING_FIRST_OVERVIEW) {
                    ReadyScreen(
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
                    val diagnostics by settingsViewModel.diagnostics.collectAsState()
                    val showSystemApps by settingsViewModel.showSystemApps.collectAsState(initial = false)
                    val exportState by settingsViewModel.exportState.collectAsState()
                    SettingsScreen(
                        themeMode = themeMode,
                        onThemeSelected = onThemeSelected,
                        diagnostics = diagnostics,
                        showSystemApps = showSystemApps,
                        onShowSystemAppsChange = settingsViewModel::setShowSystemApps,
                        onDeleteAllData = settingsViewModel::deleteAllData,
                        exportState = exportState,
                        onExportConfirmed = settingsViewModel::exportData,
                        onExportDismissed = settingsViewModel::resetExportState,
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
