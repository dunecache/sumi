package com.sumi.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sumi.core.designsystem.theme.NumericBody
import com.sumi.core.designsystem.theme.SumiSpacing
import com.sumi.core.designsystem.theme.categoryColorOrNeutral
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * One pre-resolved segment of the distribution bar. `colorToken` is an app_categories
 * color token (palette or neutral); duration/share derive from the domain breakdown.
 * `categoryId == null` marks the uncategorized residual bucket.
 */
data class CategorySegment(
    val categoryId: String?,
    val name: String,
    val durationMs: Long,
    val share: Float,
    val colorToken: String,
)

/**
 * "Where your time goes" — total tracked usage, a proportional segmented bar, and a
 * labelled list of every bucket. Color is never the only channel: each row carries
 * a dot, the category name, the duration and the percentage. Buckets with zero usage
 * are omitted. Buckets under [MIN_VISIBLE_SHARE] still render a minimal sliver so the
 * bar never lies by omission.
 */
@Composable
fun CategoryDistributionCard(
    segments: List<CategorySegment>,
    totalDurationMs: Long,
    modifier: Modifier = Modifier,
    title: String = "Where your time goes",
    onCategoryClick: ((String) -> Unit)? = null,
    onSeeAll: (() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SumiSectionHeader(title, modifier = Modifier.weight(1f))
            if (onSeeAll != null) {
                TextButton(onClick = onSeeAll) {
                    Text("See all", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        if (segments.isEmpty()) {
            Spacer(Modifier.height(SumiSpacing.sm))
            Text(
                text = "No tracked usage yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        Text(
            text = "${formatDuration(totalDurationMs)} tracked",
            style = NumericBody,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = SumiSpacing.xs),
        )
        Spacer(Modifier.height(SumiSpacing.md))
        SegmentedBar(segments)
        Spacer(Modifier.height(SumiSpacing.md))
        Column(verticalArrangement = Arrangement.spacedBy(SumiSpacing.xs)) {
            segments.forEach { segment ->
                val id = segment.categoryId
                CategoryRow(
                    segment = segment,
                    onClick = if (onCategoryClick != null && id != null) {
                        { onCategoryClick(id) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Composable
private fun SegmentedBar(segments: List<CategorySegment>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(SEGMENT_BAR_HEIGHT)
            .semantics {
                contentDescription = "Category distribution: " + segments.joinToString(", ") { segment ->
                    "${segment.name}, ${percentLabel(segment.share)} percent"
                }
            },
        horizontalArrangement = Arrangement.spacedBy(SEGMENT_GAP),
    ) {
        segments.forEach { segment ->
            Box(
                modifier = Modifier
                    .weight(segment.share.coerceAtLeast(MIN_VISIBLE_SHARE))
                    .clip(RoundedCornerShape(SEGMENT_CORNER))
                    .background(categoryColorOrNeutral(segment.colorToken)),
            )
        }
    }
}

@Composable
private fun CategoryRow(
    segment: CategorySegment,
    onClick: (() -> Unit)?,
) {
    val spoken = "${segment.name}, ${formatDuration(segment.durationMs)}, ${percentLabel(segment.share)} percent of tracked time"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(SumiSpacing.minTouchTarget)
            .let { row ->
                if (onClick != null) {
                    row.clickable(onClickLabel = segment.name, onClick = onClick)
                } else {
                    row
                }
            }
            .semantics(mergeDescendants = true) { contentDescription = spoken }
            .padding(vertical = SumiSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SumiSpacing.sm),
    ) {
        Box(
            modifier = Modifier
                .size(CATEGORY_DOT)
                .clip(CircleShape)
                .background(categoryColorOrNeutral(segment.colorToken)),
        )
        Text(
            text = segment.name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        DurationText(durationMs = segment.durationMs)
        Text(
            text = "${percentLabel(segment.share)}%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(PERCENT_WIDTH),
            textAlign = TextAlign.End,
        )
    }
}

/** Whole-percent label with consistent rounding (display only). */
internal fun percentLabel(share: Float): Int = max(0f, share * 100f).roundToInt()

private val SEGMENT_BAR_HEIGHT = 12.dp
private val SEGMENT_GAP = 2.dp
private val SEGMENT_CORNER = 3.dp
private val CATEGORY_DOT = 10.dp
private val PERCENT_WIDTH = 36.dp
private const val MIN_VISIBLE_SHARE = 0.03f
