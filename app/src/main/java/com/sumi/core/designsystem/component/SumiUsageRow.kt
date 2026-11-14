package com.sumi.core.designsystem.component

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sumi.core.designsystem.theme.SumiSpacing
import com.sumi.core.designsystem.theme.categoryColorOrNeutral
import com.sumi.domain.insights.Averages

/** Shared app-identity and usage-row components. No hardcoded colors, spacing, or type sizes. */

/**
 * Launcher icon for a package, resolved via [loader] (the platform icon loader, passed as
 * a lambda so the design system stays platform-free). Renders a quiet monogram fallback
 * when no icon resolves — uninstalled or hidden packages never break the layout.
 *
 * [fallbackColorToken], when set, colors the monogram with that category color token
 * (a letter mark on the category color) instead of the neutral container — the Apps
 * library uses it so an iconless app still carries its category identity. Today renders
 * the default neutral monogram.
 */
@Composable
fun AppIcon(
    packageName: String,
    displayName: String,
    loader: (suspend (String) -> Drawable?)?,
    modifier: Modifier = Modifier,
    size: Dp = DEFAULT_ICON_SIZE,
    fallbackColorToken: String? = null,
) {
    var bitmap by remember(packageName) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(packageName, loader) {
        if (loader != null) {
            loader(packageName)?.let { drawable ->
                bitmap = drawable.toImageBitmap()
            }
        }
    }
    val resolved = bitmap
    if (resolved != null) {
        Image(
            bitmap = resolved,
            contentDescription = null, // decorative; the row announces the app name
            contentScale = ContentScale.Fit,
            modifier = modifier.size(size),
        )
    } else {
        MonogramIcon(
            displayName = displayName,
            fallbackColorToken = fallbackColorToken,
            modifier = modifier.size(size),
        )
    }
}

@Composable
private fun MonogramIcon(
    displayName: String,
    fallbackColorToken: String?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(
                if (fallbackColorToken != null) {
                    categoryColorOrNeutral(fallbackColorToken)
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = displayName.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "·",
            style = MaterialTheme.typography.titleMedium,
            color = if (fallbackColorToken != null) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            },
        )
    }
}

private fun Drawable.toImageBitmap(): ImageBitmap {
    val width = intrinsicWidth.coerceAtLeast(1)
    val height = intrinsicHeight.coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, width, height)
    draw(canvas)
    return bitmap.asImageBitmap()
}

/**
 * One most-used / apps-list row: launcher icon, display name, a thin proportional usage
 * bar, duration, and optional share of the day. Whole row is one touch/semantic target
 * announcing the takeaway ("Instagram, 1h 12m, 34 percent of today").
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AppUsageRow(
    packageName: String,
    displayName: String,
    durationMs: Long,
    shareOfTotal: Float?,
    iconLoader: (suspend (String) -> Drawable?)?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    /** Spoken window for the share ("today", "the last 7 days"); screen text is window-free. */
    sharePeriod: String = "today",
) {
    val percent = shareOfTotal?.takeIf { it >= MIN_MEANINGFUL_SHARE }
        ?.let { "${Averages.percentOfShare(it)}%" }
    val spoken = buildString {
        append(displayName)
        append(", ")
        append(formatDuration(durationMs))
        if (percent != null) append(", $percent of $sharePeriod")
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = SumiSpacing.minTouchTarget)
            .let { base ->
                when {
                    onClick != null && onLongClick != null ->
                        base.combinedClickable(
                            onClickLabel = displayName,
                            onLongClickLabel = "Select $displayName",
                            onClick = onClick,
                            onLongClick = onLongClick,
                        )
                    onLongClick != null ->
                        base.combinedClickable(
                            onClickLabel = displayName,
                            onLongClickLabel = "Select $displayName",
                            onClick = {},
                            onLongClick = onLongClick,
                        )
                    onClick != null -> base.clickable(onClickLabel = displayName, onClick = onClick)
                    else -> base
                }
            }
            .semantics(mergeDescendants = true) { contentDescription = spoken }
            .padding(vertical = SumiSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SumiSpacing.md),
    ) {
        AppIcon(
            packageName = packageName,
            displayName = displayName,
            loader = iconLoader,
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            if (shareOfTotal != null) {
                ProportionalUsageBar(
                    share = shareOfTotal,
                    modifier = Modifier.padding(top = SumiSpacing.xs),
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            if (trailing != null) {
                trailing()
            } else {
                DurationText(durationMs = durationMs)
                if (percent != null) {
                    Text(
                        text = percent,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Thin proportional usage bar ("what the share looks like at a glance"). Track uses the
 * tonal ladder, fill the accessible accent — same recipe for Today's most-used rows and
 * the Apps library rows. Zero-width shares render as an empty track, never a hairline fill.
 */
@Composable
fun ProportionalUsageBar(share: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(USAGE_BAR_HEIGHT)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Box(
            Modifier
                .fillMaxWidth(share.coerceIn(0f, 1f))
                .height(USAGE_BAR_HEIGHT)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

private const val MIN_MEANINGFUL_SHARE = 0.01f
private val DEFAULT_ICON_SIZE = 40.dp
private val USAGE_BAR_HEIGHT = 4.dp
