package com.sumi.feature.insights

import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sumi.core.designsystem.component.AppUsageRow
import com.sumi.core.designsystem.component.CategoryDistributionCard
import com.sumi.core.designsystem.component.DailyBars
import com.sumi.core.designsystem.component.EmptyState
import com.sumi.core.designsystem.component.LoadingState
import com.sumi.core.designsystem.component.SumiCard
import com.sumi.core.designsystem.component.SumiSectionHeader
import com.sumi.core.designsystem.component.TimelineStrip
import com.sumi.core.designsystem.component.formatDuration
import com.sumi.core.designsystem.theme.NumericBody
import com.sumi.core.designsystem.theme.NumericLarge
import com.sumi.core.designsystem.theme.ReflectionBody
import com.sumi.core.designsystem.theme.SumiSpacing
import com.sumi.core.designsystem.theme.categoryColorOrNeutral
import com.sumi.domain.insights.InsightKind
import com.sumi.domain.insights.InsightsRange
import com.sumi.domain.insights.Averages
import com.sumi.domain.categories.DefaultPackageCategories
import java.util.Locale

/**
 * Insights (V1): range-aware analytics. Today is the glance; this is the study.
 * One spine (the range switcher) drives everything below it. Every number states its
 * window; every share uses the documented denominator; interpretations stay hedged.
 */
@Composable
fun InsightsScreen(
    viewModel: InsightsViewModel = hiltViewModel(),
    onOpenAppDetail: (packageName: String) -> Unit = {},
    onOpenCategoryDetail: (categoryId: String, range: String) -> Unit = { _, _ -> },
    onReviewUncategorized: () -> Unit = {},
    onSeeAllApps: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val iconLoader = remember(viewModel) { viewModel.iconLoader::iconFor }
    InsightsContent(
        state = state,
        iconLoader = iconLoader,
        onRangeChange = viewModel::setRange,
        onSelectDay = viewModel::selectDay,
        onOpenAppDetail = onOpenAppDetail,
        onOpenCategoryDetail = onOpenCategoryDetail,
        onReviewUncategorized = onReviewUncategorized,
        onSeeAllApps = onSeeAllApps,
    )
}

/** Stateless content; previews exercise loading, empty, permission-missing and populated. */
@Composable
internal fun InsightsContent(
    state: InsightsUiState,
    iconLoader: (suspend (String) -> Drawable?)? = null,
    onRangeChange: (InsightsRange) -> Unit = {},
    onSelectDay: (Int?) -> Unit = {},
    onOpenAppDetail: (packageName: String) -> Unit = {},
    onOpenCategoryDetail: (categoryId: String, range: String) -> Unit = { _, _ -> },
    onReviewUncategorized: () -> Unit = {},
    onSeeAllApps: () -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(SumiSpacing.screenHorizontal),
    ) {
        Spacer(Modifier.height(SumiSpacing.lg))
        Text(text = "Insights", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(SumiSpacing.sm))
        RangeSwitcher(selected = state.range, onSelect = onRangeChange)
        Spacer(Modifier.height(SumiSpacing.md))
        when {
            state.isLoading -> LoadingState()
            !state.hasUsageAccess -> Column {
                Spacer(Modifier.height(SumiSpacing.xl))
                EmptyState(
                    title = "Usage Access is off",
                    message = "Sumi starts learning your day from now. Enable Usage Access to see your analytics.",
                )
                Spacer(Modifier.weight(1f))
            }
            state.coverageDays == 0 -> Column {
                Spacer(Modifier.height(SumiSpacing.xl))
                EmptyState(
                    title = "Nothing recorded in this range",
                    message = "Sumi starts learning from now. Older history appears after the first sync.",
                )
                Spacer(Modifier.weight(1f))
            }
            else -> Populated(
                state = state,
                iconLoader = iconLoader,
                onSelectDay = onSelectDay,
                onOpenAppDetail = onOpenAppDetail,
                onOpenCategoryDetail = onOpenCategoryDetail,
                onReviewUncategorized = onReviewUncategorized,
                onSeeAllApps = onSeeAllApps,
            )
        }
        Spacer(Modifier.height(SumiSpacing.lg))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangeSwitcher(
    selected: InsightsRange,
    onSelect: (InsightsRange) -> Unit,
) {
    val ranges = listOf(InsightsRange.TODAY, InsightsRange.YESTERDAY, InsightsRange.WEEK, InsightsRange.MONTH)
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        ranges.forEachIndexed { index, range ->
            SegmentedButton(
                selected = range == selected,
                onClick = { onSelect(range) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = ranges.size),
                label = { Text(range.shortLabel) },
            )
        }
    }
}

@Composable
private fun Populated(
    state: InsightsUiState,
    iconLoader: (suspend (String) -> Drawable?)?,
    onSelectDay: (Int?) -> Unit,
    onOpenAppDetail: (packageName: String) -> Unit,
    onOpenCategoryDetail: (categoryId: String, range: String) -> Unit,
    onReviewUncategorized: () -> Unit,
    onSeeAllApps: () -> Unit,
) {
    val range = state.range
    // Overview (editorial anchor): window caption, label, serif numeral, neutral comparison.
    Text(
        text = state.rangeCaption.uppercase(Locale.getDefault()),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(SumiSpacing.xs))
    Text(
        text = when (range) {
            InsightsRange.TODAY -> "Screen time today"
            InsightsRange.YESTERDAY -> "Screen time yesterday"
            else -> "Screen time in ${range.windowLabel}"
        },
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(text = formatDuration(state.totalMs), style = NumericLarge)
    Spacer(Modifier.height(SumiSpacing.xs))
    state.comparison?.let { comparison ->
        Text(
            text = comparison,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    state.averageMs?.let { average ->
        Text(
            text = "${formatDuration(average)} per day across ${range.windowDays} days.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    state.coverageNote?.let { note ->
        Text(
            text = note,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    // Daily bars; tap a bar to inspect that day. Partial days are marked both visually and in speech.
    Spacer(Modifier.height(SumiSpacing.xl))
    SumiCard {
        Text(
            text = "Daily screen time — tap a bar to inspect",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(SumiSpacing.md))
        DailyBars(
            days = state.dailyTotals,
            selectedIndex = state.selectedDayIndex,
            onSelect = onSelectDay,
            averageMs = state.averageMsForBars,
        )
        state.selectedDayIndex?.let { index ->
            state.dailyDetails.getOrNull(index)?.let { detail ->
                DayInspector(detail = detail)
            }
        }
    }

    // Category distribution: who the time went to.
    if (state.categories.isNotEmpty()) {
        Spacer(Modifier.height(SumiSpacing.xl))
        CategoryDistributionCard(
            segments = state.categories,
            totalDurationMs = state.trackedMs,
            onCategoryClick = { id -> onOpenCategoryDetail(id, range.name) },
            onUncategorizedClick = onReviewUncategorized,
            title = "Where your time goes",
        )
    }

    // Uncategorized residual: actionable, never nagging. Only above ~15% of tracked time.
    if (state.trackedMs > 0L && state.uncategorizedShare >= UNCATEGORIZED_PROMPT_SHARE) {
        Spacer(Modifier.height(SumiSpacing.md))
        UncategorizedPrompt(
            durationMs = state.uncategorizedMs,
            windowLabel = range.windowLabel,
            onReview = onReviewUncategorized,
        )
    }

    // Time in social apps: a lens on the Social category, not a parallel data system.
    state.social?.let { social ->
        Spacer(Modifier.height(SumiSpacing.md))
        SumiCard {
            Text(
                text = "Time in social apps",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(SumiSpacing.xs))
            if (social.totalMs > 0L) {
                Text(
                    text = formatDuration(social.totalMs),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(SumiSpacing.xs))
                Text(
                    text = "${Averages.percentOfShare(social.share)}% of tracked time ${range.windowLabel}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                social.comparison?.let { comparison ->
                    Text(
                        text = comparison,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(SumiSpacing.sm))
                TrendStrip(values = social.dailyMs)
                if (social.topApps.isNotEmpty()) {
                    Spacer(Modifier.height(SumiSpacing.sm))
                    Column(verticalArrangement = Arrangement.spacedBy(SumiSpacing.xs)) {
                        social.topApps.forEach { app ->
                            AppUsageRow(
                                packageName = app.packageName,
                                displayName = app.displayName,
                                durationMs = app.durationMs,
                                shareOfTotal = null,
                                iconLoader = iconLoader,
                                onClick = { onOpenAppDetail(app.packageName) },
                            )
                        }
                        if (social.moreApps > 0) {
                            Text(
                                text = "and ${social.moreApps} more ${if (social.moreApps == 1) "app" else "apps"}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = SumiSpacing.md),
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "None in ${range.windowLabel}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(
                onClick = { onOpenCategoryDetail(DefaultPackageCategories.SOCIAL, range.name) },
                contentPadding = PaddingValues(horizontal = 0.dp),
            ) {
                Text("Open social analytics")
            }
        }
    }

    // Most used in the range.
    if (state.topApps.isNotEmpty()) {
        Spacer(Modifier.height(SumiSpacing.xl))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SumiSectionHeader("MOST USED", modifier = Modifier.weight(1f))
            TextButton(onClick = onSeeAllApps) {
                Text("See all", style = MaterialTheme.typography.labelLarge)
            }
        }
        Spacer(Modifier.height(SumiSpacing.sm))
        Column(verticalArrangement = Arrangement.spacedBy(SumiSpacing.xs)) {
            state.topApps.forEach { app ->
                AppUsageRow(
                    packageName = app.packageName,
                    displayName = app.displayName,
                    durationMs = app.durationMs,
                    shareOfTotal = if (state.trackedMs > 0L) {
                        app.durationMs.toFloat() / state.trackedMs.toFloat()
                    } else {
                        null
                    },
                    iconLoader = iconLoader,
                    onClick = { onOpenAppDetail(app.packageName) },
                    sharePeriod = state.range.windowLabel.replaceFirstChar { it.lowercase() },
                )
            }
        }
    }

    // Patterns: when the usage happens. Only when the data supports it.
    Spacer(Modifier.height(SumiSpacing.xl))
    SumiSectionHeader("PATTERNS")
    Spacer(Modifier.height(SumiSpacing.sm))
    if (state.coverageDays < MIN_PATTERN_COVERAGE_DAYS || state.hourly.all { it == 0L }) {
        Text(
            text = "Patterns appear once Sumi has recorded a few days of use.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        SumiCard {
            Text(
                text = "Hour of day across ${range.windowLabel}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(SumiSpacing.md))
            TimelineStrip(hourlyTotals = state.hourly)
            Spacer(Modifier.height(SumiSpacing.sm))
            state.patterns.busiestHour?.let { hour ->
                Text(
                    text = "Most active hour: ${hour.toString().padStart(2, '0')}:00–${(hour + 1).toString().padStart(2, '0')}:00",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = "Mornings (5:00–11:00) ${formatDuration(state.patterns.morningMs)} · " +
                    "Evenings (17:00–22:00) ${formatDuration(state.patterns.eveningMs)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (range.windowDays > 1 && (state.patterns.weekdayMs > 0L || state.patterns.weekendMs > 0L)) {
                Text(
                    text = "Weekdays ${formatDuration(state.patterns.weekdayMs)} (${windowPercent(state.patterns.weekdayMs, state.totalMs)}%) · " +
                        "Weekends ${formatDuration(state.patterns.weekendMs)} (${windowPercent(state.patterns.weekendMs, state.totalMs)}%)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    // Observations: deterministic, fact-first, interpretations hedged.
    if (state.observations.isNotEmpty()) {
        Spacer(Modifier.height(SumiSpacing.xl))
        SumiSectionHeader("OBSERVATIONS")
        Spacer(Modifier.height(SumiSpacing.sm))
        Column(verticalArrangement = Arrangement.spacedBy(SumiSpacing.sm)) {
            state.observations.forEach { insight ->
                if (insight.kind == InsightKind.INTERPRETATION) {
                    Row {
                        Text(
                            text = "·",
                            style = ReflectionBody,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(SumiSpacing.xs))
                        Text(
                            text = insight.text,
                            style = ReflectionBody,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontStyle = FontStyle.Italic,
                        )
                    }
                } else {
                    Text(
                        text = insight.text,
                        style = ReflectionBody,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

/** Tap-a-bar inspector: that day's total and category split. */
@Composable
private fun DayInspector(detail: DayDetail) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = SumiSpacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = detail.title.uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = formatDuration(detail.totalMs),
                style = NumericBody,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (detail.isPartial) {
            Text(
                text = "Today isn't over yet — the day is still in progress.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (detail.segments.isEmpty()) {
            Text(
                text = "No usage recorded.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Spacer(Modifier.height(SumiSpacing.xs))
            detail.segments.forEach { segment ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(SumiSpacing.minTouchTarget - 8.dp)
                        .padding(vertical = SumiSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SumiSpacing.sm),
                ) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(categoryColorOrNeutral(segment.colorToken)),
                    )
                    Text(
                        text = segment.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                    Text(
                        text = formatDuration(segment.durationMs),
                        style = NumericBody,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "${Averages.percentOfShare(segment.share)}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(36.dp),
                    )
                }
            }
        }
    }
}

/** Compact per-day trend strip for the social card. Decorative; the value list is spoken. */
@Composable
private fun TrendStrip(
    values: List<Long>,
    modifier: Modifier = Modifier,
) {
    val max = values.maxOrNull()?.takeIf { it > 0L } ?: 1L
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .semantics {
                contentDescription = "Daily trend across the range, ${values.size} days"
            },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        values.forEach { value ->
            val heightFraction = if (value > 0L) {
                (value.toFloat() / max.toFloat()).coerceAtLeast(0.08f)
            } else {
                0.04f
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(heightFraction)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accent.copy(alpha = 0.55f)),
            )
        }
    }
}

/**
 * Uncategorized residual prompt (ARCHITECTURE.md §13). Calm, actionable, always available
 * — never a nag (DESIGN.md §7). Only shown above ~15% of tracked time in the range.
 */
@Composable
private fun UncategorizedPrompt(
    durationMs: Long,
    windowLabel: String,
    onReview: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = SumiSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${formatDuration(durationMs)} of $windowLabel isn't categorized yet",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "These apps are waiting to be assigned a category.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onReview) {
            Text("Review apps")
        }
    }
}
