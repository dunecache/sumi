package com.sumi.feature.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.ExperimentalFoundationApi
import com.sumi.core.designsystem.component.AppIcon
import com.sumi.core.designsystem.component.DurationText
import com.sumi.core.designsystem.component.ProportionalUsageBar
import com.sumi.core.designsystem.component.formatDuration
import com.sumi.core.designsystem.theme.SumiSpacing
import com.sumi.core.designsystem.theme.categoryColorOrNeutral
import com.sumi.domain.insights.Averages
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.ui.text.style.TextOverflow

/**
 * Rows for the Apps library. One semantic target per row; the category identity is a
 * dot plus the category name (never color alone); the usage window follows the sort.
 */

/**
 * A library row: icon (category-tinted monogram fallback), label, category identity
 * line (dot + name, or the quiet "Uncategorized" residual), a thin proportional bar for
 * the matching window, today's total, the trailing week's total, an optional share of
 * the window (usage sorts only), and a quiet "New" marker with an inline categorize
 * affordance for fresh unassigned apps.
 *
 * Merge-descendants semantics announce everything TalkBack needs in one pass:
 * "Instagram. Social category. 1 minute today. 6 hours 30 minutes this week."
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppLibraryRow(
    app: AppRow,
    /** Share of the matching window's total, or null outside usage sorts / when undefined. */
    shareOfTotal: Float?,
    iconLoader: (suspend (String) -> android.graphics.drawable.Drawable?)?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoveToCategory: () -> Unit,
    onOpenDetails: () -> Unit,
    onCategorizeNew: () -> Unit,
    windowIsWeek: Boolean,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by mutableStateOf(false)
    val todayText = formatDuration(app.todayMs)
    val weekText = formatDuration(app.weekMs)
    val categoryText = app.categoryName ?: "Uncategorized"
    val percent = shareOfTotal?.takeIf { it >= MIN_MEANINGFUL_SHARE }?.let { Averages.percentOfShare(it) }
    val spoken = buildString {
        append(app.displayName)
        append(". ")
        append(categoryText)
        append(" category. ")
        append(todayText)
        append(" today. ")
        append(weekText)
        append(" this week.")
        if (percent != null) {
            append(" ")
            append(percent)
            append(if (windowIsWeek) " percent of the week." else " percent of today.")
        }
        if (app.isNew) append(" New.")
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = SumiSpacing.minTouchTarget)
            .combinedClickable(
                onClickLabel = app.displayName,
                onLongClickLabel = "Select ${app.displayName}",
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .semantics(mergeDescendants = true) { contentDescription = spoken }
            .padding(vertical = SumiSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SumiSpacing.md),
    ) {
        AppIcon(
            packageName = app.packageName,
            displayName = app.displayName,
            loader = iconLoader,
            fallbackColorToken = app.categoryColorToken ?: UNCATEGORIZED_TOKEN,
        )
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = app.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (app.isNew) {
                    Spacer(Modifier.width(SumiSpacing.xs))
                    if (app.categoryId == null) {
                        // Quiet pill; the spec's inline categorize affordance for fresh apps.
                        NewCategorizePill(
                            onClick = onCategorizeNew,
                            contentDescription = "Categorize ${app.displayName}",
                        )
                    } else {
                        NewPill()
                    }
                }
            }
            // Category identity: dot + name. Never color alone.
            Row(
                modifier = Modifier.padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(DOT)
                        .clip(CircleShape)
                        .background(categoryColorOrNeutral(app.categoryColorToken ?: UNCATEGORIZED_TOKEN)),
                )
                Text(
                    text = categoryText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(start = SumiSpacing.xs),
                )
            }
            if (shareOfTotal != null) {
                ProportionalUsageBar(
                    share = shareOfTotal,
                    modifier = Modifier.padding(top = SumiSpacing.xs),
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            DurationText(durationMs = app.todayMs)
            Text(
                text = "wk $weekText",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (percent != null) {
                Text(
                    text = "$percent%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box {
            IconButton(
                onClick = { menuExpanded = true },
                // ≥48dp target guaranteed by IconButton; announced with the app's name.
            ) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More options for ${app.displayName}")
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("App details") },
                    onClick = { menuExpanded = false; onOpenDetails() },
                )
                DropdownMenuItem(
                    text = { Text("Move to category…") },
                    onClick = { menuExpanded = false; onMoveToCategory() },
                )
            }
        }
    }
}

/** Quiet "New" pill for apps already carrying an assignment. */
@Composable
private fun NewPill() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = SumiSpacing.xs, vertical = 2.dp),
    ) {
        Text(
            text = "New",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Quiet outlined pill that both marks the app as new and opens the assignment sheet. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NewCategorizePill(
    onClick: () -> Unit,
    contentDescription: String,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
            .combinedClickable(
                onClickLabel = contentDescription,
                onClick = onClick,
            )
            .padding(horizontal = SumiSpacing.xs, vertical = 2.dp)
            .semantics { this.contentDescription = contentDescription },
    ) {
        Text(
            text = "New · Categorize",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * Selection-mode row: leading check affordance, icon, name, category name quiet, and
 * today's duration. Whole row is one 48dp touch target toggling the package.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SelectableAppRow(
    app: AppRow,
    selected: Boolean,
    iconLoader: (suspend (String) -> android.graphics.drawable.Drawable?)?,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spoken = buildString {
        append(app.displayName)
        if (app.categoryName != null) {
            append(". ")
            append(app.categoryName)
            append(" category.")
        }
        append(" ")
        append(if (selected) "Selected." else "Not selected.")
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = SumiSpacing.minTouchTarget)
            .combinedClickable(
                onClickLabel = "${if (selected) "Deselect" else "Select"} ${app.displayName}",
                onClick = onToggle,
                onLongClick = onToggle,
            )
            .semantics(mergeDescendants = true) { contentDescription = spoken }
            .padding(vertical = SumiSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SumiSpacing.md),
    ) {
        Box(
            Modifier
                .size(CIRCLE)
                .clip(CircleShape)
                .let { circle ->
                    if (selected) {
                        circle.background(MaterialTheme.colorScheme.primary)
                    } else {
                        circle.border(2.dp, MaterialTheme.colorScheme.onSurfaceVariant, CircleShape)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        AppIcon(
            packageName = app.packageName,
            displayName = app.displayName,
            loader = iconLoader,
            fallbackColorToken = app.categoryColorToken ?: UNCATEGORIZED_TOKEN,
        )
        Text(
            text = app.displayName,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        if (app.todayMs > 0L) {
            Text(
                text = formatDuration(app.todayMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Quiet divider introducing the zero-usage group in usage sorts. */
@Composable
fun NotUsedYetDivider(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = SumiSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SumiSpacing.md),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(HAIRLINE)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
        Text(
            text = "NOT USED YET",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(HAIRLINE)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
    }
}

private const val MIN_MEANINGFUL_SHARE = 0.01f
private const val UNCATEGORIZED_TOKEN = "stone-1"
private val DOT = 10.dp
private val CIRCLE = 24.dp
private val HAIRLINE = 1.dp
