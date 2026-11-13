package com.sumi.feature.appdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sumi.core.designsystem.component.AppIcon
import com.sumi.core.designsystem.component.EmptyState
import com.sumi.core.designsystem.component.SumiCard
import com.sumi.core.designsystem.component.SumiSectionHeader
import com.sumi.core.designsystem.component.WeeklyBars
import com.sumi.core.designsystem.component.formatDuration
import com.sumi.core.designsystem.theme.NumericLarge
import com.sumi.core.designsystem.theme.SumiSpacing
import com.sumi.core.designsystem.theme.categoryColorOrNeutral

/**
 * App detail (M2): individual usage history on real data — identity header, today's
 * total, a 7-day chart, a 30-day average, and today's session summary. The raw package
 * name appears here as quiet metadata; it never headlines the screen.
 */
@Composable
fun AppDetailScreen(
    viewModel: AppDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val iconLoader = remember(viewModel) { viewModel.iconLoader::iconFor }
    var showCategoryPicker by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(SumiSpacing.screenHorizontal),
    ) {
        Spacer(Modifier.height(SumiSpacing.lg))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SumiSpacing.md),
        ) {
            AppIcon(
                packageName = state.packageName,
                displayName = state.displayName,
                loader = iconLoader,
            )
            Column {
                Text(text = state.displayName, style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = state.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(SumiSpacing.lg))
        SumiSectionHeader("CATEGORY")
        Spacer(Modifier.height(SumiSpacing.xs))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(CATEGORY_DOT)
                    .clip(CircleShape)
                    .background(categoryColorOrNeutral(state.categoryColorToken)),
            )
            Column(modifier = Modifier.padding(start = SumiSpacing.sm).weight(1f)) {
                Text(
                    text = state.categoryName,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                )
                Text(
                    text = if (state.categoryUserSet) "Your choice" else "Automatic",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { showCategoryPicker = true }) {
                Text("Change")
            }
        }
        Spacer(Modifier.height(SumiSpacing.xl))

        Text(
            text = "Screen time today",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = if (state.isLoading) "—" else formatDuration(state.todayMs),
            style = NumericLarge,
        )

        if (!state.isLoading && state.week.any { it > 0L }) {
            Spacer(Modifier.height(SumiSpacing.xl))
            SumiSectionHeader("LAST 7 DAYS")
            Spacer(Modifier.height(SumiSpacing.sm))
            SumiCard { WeeklyBars(dailyTotals = state.week) }
        }

        if (!state.isLoading) {
            val activeDays = state.month.count { it > 0L }
            val monthAverage = if (activeDays >= 2) {
                state.month.filter { it > 0L }.average()
            } else {
                null
            }
            monthAverage?.let { avg ->
                Spacer(Modifier.height(SumiSpacing.md))
                Text(
                    text = "About ${formatDuration(avg.toLong())} a day over the last 30 days.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (!state.isLoading) {
            state.sessionSummary?.let { summary ->
                Spacer(Modifier.height(SumiSpacing.md))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (!state.isLoading && state.week.none { it > 0L } && state.sessionSummary == null) {
            Spacer(Modifier.height(SumiSpacing.lg))
            EmptyState(
                title = "No history yet",
                message = "Usage history for this app will appear here.",
            )
        }

        Spacer(Modifier.height(SumiSpacing.lg))
    }

    if (showCategoryPicker) {
        CategoryPickerSheet(
            options = state.categories,
            selectedId = state.categoryId,
            onDismiss = { showCategoryPicker = false },
            onSelect = { id ->
                viewModel.setCategory(id)
                showCategoryPicker = false
            },
        )
    }
}

/** Category assignment sheet: every category plus the uncategorized residual. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryPickerSheet(
    options: List<com.sumi.feature.appdetail.CategoryOption>,
    selectedId: String?,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SumiSpacing.screenHorizontal),
        ) {
            Text(text = "Category", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(SumiSpacing.lg))
            PickerRow(
                name = "Uncategorized",
                colorToken = "categoryNeutral",
                selected = selectedId == null,
                onClick = { onSelect(null) },
            )
            options.forEach { option ->
                PickerRow(
                    name = option.name,
                    colorToken = option.colorToken,
                    selected = selectedId == option.id,
                    onClick = { onSelect(option.id) },
                )
            }
            Spacer(Modifier.height(SumiSpacing.xl))
        }
    }
}

@Composable
private fun PickerRow(
    name: String,
    colorToken: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Select $name", onClick = onClick)
            .padding(vertical = SumiSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(PICKER_DOT)
                .clip(CircleShape)
                .background(categoryColorOrNeutral(colorToken)),
        )
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .weight(1f)
                .padding(start = SumiSpacing.md),
            maxLines = 1,
        )
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private val CATEGORY_DOT = 28.dp
private val PICKER_DOT = 24.dp
