package com.sumi.core.designsystem.component

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sumi.core.designsystem.theme.NumericBody
import com.sumi.core.designsystem.theme.SumiSpacing
import com.sumi.domain.insights.Averages
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.floor

/**
 * Compose-native charts (Canvas, no chart library). Every chart states its question
 * as a caption at the call site and provides a content description with the takeaway.
 */

/**
 * 24-hour usage strip: vertical bars in the single calm accent at reduced opacity, with at
 * most one emphasized bar (the first max) at full accent — emphasis by opacity only, never
 * hue. Zero-data buckets render dashed ghost stubs so an empty day reads as "empty, on
 * purpose", never as an error. Minimal hour markers at 0/6/12/18/24; tap to explore a
 * single hour, with a quiet numeric readout. One subtle fade-in on first load; nothing
 * animates afterwards, and reduced-motion (animator scale 0) skips the fade entirely.
 */
@Composable
fun TimelineStrip(
    hourlyTotals: List<Long>, // 24 values, 0..23
    modifier: Modifier = Modifier,
) {
    val totals = hourlyTotals.take(HOURS_PER_DAY)
    val max = totals.maxOrNull()?.takeIf { it > 0 } ?: 1L
    val peak = totals.withIndex().maxByOrNull { it.value }?.takeIf { it.value > 0L }
    var selectedHour by remember { mutableStateOf<Int?>(null) }

    val accent = MaterialTheme.colorScheme.primary
    val ghost = MaterialTheme.colorScheme.onSurfaceVariant
    val idleLine = if (peak == null) {
        "No usage recorded yet today"
    } else {
        "Busiest around ${peak.index}:00 · ${formatDuration(peak.value)}"
    }
    val readout = selectedHour?.let { hour ->
        "${hour.toString().padStart(2, '0')}:00 — ${formatDuration(totals.getOrElse(hour) { 0L })}"
    } ?: idleLine

    Column(modifier = modifier.fillMaxWidth()) {
        // Reduced motion: animator scale 0 means no entrance fade.
        val context = LocalContext.current
        val animatorScale = remember {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        }
        val entrance = remember { Animatable(if (animatorScale == 0f) 1f else 0f) }
        LaunchedEffect(Unit) {
            if (animatorScale != 0f) entrance.animateTo(1f, tween(durationMillis = 200))
        }

        val chartDescription = if (selectedHour != null) "$readout (selected)" else readout
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(TIMELINE_HEIGHT)
                .graphicsLayer { alpha = entrance.value }
                .pointerInput(totals) {
                    detectTapGestures { offset ->
                        val hour = floor(offset.x / (size.width / HOURS_PER_DAY.toFloat()))
                            .toInt()
                            .coerceIn(0, HOURS_PER_DAY - 1)
                        selectedHour = if (selectedHour == hour) null else hour
                    }
                }
                .semantics { contentDescription = chartDescription },
        ) {
            val slot = size.width / HOURS_PER_DAY.toFloat()
            val barWidth = slot * 0.6f
            val ghostStroke = Stroke(
                width = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
            )
            totals.forEachIndexed { index, value ->
                val left = index * slot + slot * 0.2f
                when {
                    value <= 0L -> {
                        // Ghost stub: dashed outline at the baseline, ≥3:1 contrast.
                        val stubHeight = 6.dp.toPx()
                        drawRoundRect(
                            color = ghost,
                            topLeft = Offset(left, size.height - stubHeight),
                            size = Size(barWidth, stubHeight),
                            cornerRadius = CornerRadius(4f, 4f),
                            style = ghostStroke,
                        )
                    }
                    index == peak?.index -> {
                        // The one emphasized bar: full accent.
                        drawRoundRect(
                            color = accent,
                            topLeft = Offset(left, 0f),
                            size = Size(barWidth, size.height),
                            cornerRadius = CornerRadius(4f, 4f),
                        )
                    }
                    else -> {
                        val barHeight = (size.height * (value.toFloat() / max.toFloat()))
                            .coerceAtLeast(2f)
                        drawRoundRect(
                            color = accent.copy(alpha = 0.5f),
                            topLeft = Offset(left, size.height - barHeight),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(4f, 4f),
                        )
                    }
                }
            }
            // Selection marker: a thin full-height hairline under the chosen hour.
            selectedHour?.let { hour ->
                val left = hour * slot + slot * 0.2f
                drawLine(
                    color = accent,
                    start = Offset(left, 0f),
                    end = Offset(left, size.height),
                    strokeWidth = 2.dp.toPx(),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = SumiSpacing.xs),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            HOUR_MARKERS.forEach { marker ->
                Text(
                    text = "$marker:00",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = readout,
            style = NumericBody,
            color = if (selectedHour != null) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(top = SumiSpacing.sm),
        )
    }
}

/**
 * Weekly bars: seven days, today in full accent, the rest quiet container tone, a thin
 * average rule, and day-of-week labels. Comparison stays neutral — no good/bad colors.
 */
@Composable
fun WeeklyBars(
    dailyTotals: List<Long>, // 7 values, oldest → today
    modifier: Modifier = Modifier,
    caption: String = "Daily screen time this week",
) {
    val totals = dailyTotals.take(7)
    val max = totals.maxOrNull()?.takeIf { it > 0 } ?: 1L
    // One honest rule: the average is the total divided by the window, quiet days included.
    val average = if (totals.isNotEmpty()) Averages.dailyAverage(totals) else null
    val accent = MaterialTheme.colorScheme.primary
    val barColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val averageColor = MaterialTheme.colorScheme.outline

    val busiestIndex = totals.withIndex().maxByOrNull { it.value }?.takeIf { it.value > 0 }?.index
    val busiestLabel = busiestIndex?.let { dayLabel(it) }
    val description = if (busiestLabel == null) {
        "$caption: no usage recorded yet"
    } else if (average != null && average > 0L) {
        "$caption: busiest on $busiestLabel, about ${formatDuration(average)} a day on average"
    } else {
        "$caption: busiest on $busiestLabel"
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(WEEKLY_HEIGHT)
                .semantics { contentDescription = description },
        ) {
            val slot = size.width / 7f
            val barWidth = slot * 0.5f
            totals.forEachIndexed { index, value ->
                val fraction = (value.toFloat() / max.toFloat()).coerceIn(0f, 1f)
                val barHeight = (size.height * fraction).coerceAtLeast(2f)
                val left = index * slot + slot * 0.25f
                drawRoundRect(
                    color = if (index == 6) accent else barColor,
                    topLeft = Offset(left, size.height - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(6f, 6f),
                )
            }
            average?.let { avg ->
                val y = size.height * (1f - (avg.toFloat() / max.toFloat()))
                    .coerceIn(0f, 1f)
                drawLine(
                    color = averageColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 2.dp.toPx(),
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = SumiSpacing.xs),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            (0..6).forEach { index ->
                Text(
                    text = dayLabel(index),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (index == 6) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

/** Short localized weekday label; index 6 is today. */
private fun dayLabel(index: Int): String {
    // DayOfWeek supports plus/minus (not minusDays).
    val day = java.time.LocalDate.now().dayOfWeek.minus((6 - index).toLong())
    return day.getDisplayName(TextStyle.SHORT, Locale.getDefault())
}

private const val HOURS_PER_DAY = 24
private val HOUR_MARKERS = listOf(0, 6, 12, 18, 24)
private val TIMELINE_HEIGHT = 64.dp
private val WEEKLY_HEIGHT = 96.dp
