package com.sumi.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Compose-native charts (Canvas, no chart library). Every chart states its question
 * as a caption at the call site and provides a content description with the takeaway.
 */

/**
 * 24-hour usage strip (navigation/Today reference §3.6): vertical bars in the single calm
 * accent at reduced opacity, with at most one emphasized bar (the first max) at full accent —
 * emphasis by opacity only, never hue. Zero-data buckets render dashed ghost stubs so an
 * empty day reads as "empty, on purpose" (§8.4), never as an error. One subtle fade-in on
 * first load per §6; nothing animates afterwards.
 */
@Composable
fun TimelineStrip(
    hourlyTotals: List<Long>, // 24 values, 0..23
    modifier: Modifier = Modifier,
) {
    val totals = hourlyTotals.take(24)
    val max = totals.maxOrNull()?.takeIf { it > 0 } ?: 1L
    val peak = totals.withIndex().maxByOrNull { it.value }?.takeIf { it.value > 0L }
    val accent = MaterialTheme.colorScheme.primary
    val ghost = MaterialTheme.colorScheme.onSurfaceVariant
    val description = if (peak == null) {
        "No usage recorded yet today"
    } else {
        "Timeline of today's usage, busiest around ${peak.index}:00"
    }

    // Single fade on first composition per app open (150–250ms, standard easing, §6).
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(1f, tween(durationMillis = 200))
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .graphicsLayer { alpha = entrance.value }
            .semantics { contentDescription = description },
    ) {
        val barWidth = size.width / 24f
        val ghostStroke = Stroke(
            width = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
        )
        totals.forEachIndexed { index, value ->
            val left = index * barWidth + barWidth * 0.2f
            val widthPx = barWidth * 0.6f
            when {
                value <= 0L -> {
                    // Ghost bar: dashed outline stub at the baseline, ≥3:1 contrast (§7).
                    val stubHeight = 6.dp.toPx()
                    drawRoundRect(
                        color = ghost,
                        topLeft = Offset(left, size.height - stubHeight),
                        size = Size(widthPx, stubHeight),
                        cornerRadius = CornerRadius(4f, 4f),
                        style = ghostStroke,
                    )
                }
                index == peak?.index -> {
                    // The one emphasized bar: full accent (§3.6).
                    drawRoundRect(
                        color = accent,
                        topLeft = Offset(left, 0f),
                        size = Size(widthPx, size.height),
                        cornerRadius = CornerRadius(4f, 4f),
                    )
                }
                else -> {
                    val barHeight = (size.height * (value.toFloat() / max.toFloat())).coerceAtLeast(2f)
                    drawRoundRect(
                        color = accent.copy(alpha = 0.5f),
                        topLeft = Offset(left, size.height - barHeight),
                        size = Size(widthPx, barHeight),
                        cornerRadius = CornerRadius(4f, 4f),
                    )
                }
            }
        }
    }
}

@Composable
fun WeeklyBars(
    dailyTotals: List<Long>, // 7 values
    modifier: Modifier = Modifier,
    caption: String = "Daily screen time this week",
) {
    val max = dailyTotals.maxOrNull()?.takeIf { it > 0 } ?: 1L
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.surfaceVariant
    val description = "$caption: busiest day ${max / 60_000L}m"
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(96.dp)
            .semantics { contentDescription = description },
    ) {
        val barWidth = size.width / 7f
        dailyTotals.take(7).forEachIndexed { index, value ->
            val fraction = (value.toFloat() / max.toFloat()).coerceIn(0f, 1f)
            val barHeight = (size.height * fraction).coerceAtLeast(2f)
            val left = index * barWidth + barWidth * 0.25f
            val widthPx = barWidth * 0.5f
            drawRoundRect(
                color = if (index == 6) accent else muted,
                topLeft = Offset(left, size.height - barHeight),
                size = androidx.compose.ui.geometry.Size(widthPx, barHeight),
                cornerRadius = CornerRadius(6f, 6f),
            )
        }
    }
}
