package com.sumi.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sumi.core.designsystem.theme.NumericBody
import com.sumi.core.designsystem.theme.SumiSpacing
import com.sumi.core.designsystem.theme.categoryColorOrNeutral
import com.sumi.core.designsystem.theme.sumiExtendedColors
import kotlinx.coroutines.launch

/**
 * Shared components from the 2026-11-14 Design Reference Standard v1.1 (see DESIGN.md §14).
 * No hardcoded colors, spacing, or type sizes — every value flows from tokens.
 */

// ---------------------------------------------------------------------------
// §9.1 Primary button — 48 dp, pill, disabled is unmistakably inert
// ---------------------------------------------------------------------------

/**
 * One primary action per screen (reference standard §9.1). Full-width minus the 24 dp
 * screen margin, 48 dp tall, pill-shaped, `olive-700`/`olive-200` fill with its on-accent
 * label. Disabled looks unmistakably inert (disabled-fill / disabled-label) and announces
 * as disabled to TalkBack.
 */
@Composable
fun SumiPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = SumiSpacing.minTouchTarget),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = sumiExtendedColors().disabledFill,
            disabledContentColor = sumiExtendedColors().disabledLabel,
        ),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge.copy(
            fontSize = MaterialTheme.typography.titleMedium.fontSize,
            lineHeight = MaterialTheme.typography.titleMedium.lineHeight,
        ))
    }
}

// ---------------------------------------------------------------------------
// §9.2 Quiet text link
// ---------------------------------------------------------------------------

/**
 * Quiet secondary action (reference standard §9.2). 48 dp minimum hit area via padding;
 * descriptive label only ("What Sumi does not do — opens details", never bare "here").
 * [destructive] renders the `red-600` variant for data deletion — never for emphasis.
 */
@Composable
fun SumiTextLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    contentDescription: String? = null,
    centered: Boolean = false,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = SumiSpacing.minTouchTarget)
            .padding(horizontal = SumiSpacing.sm),
        colors = ButtonDefaults.textButtonColors(
            contentColor = if (destructive) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            },
        ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            textAlign = if (centered) TextAlign.Center else null,
            modifier = if (contentDescription != null) {
                Modifier.semantics { this.contentDescription = contentDescription }
            } else {
                Modifier
            },
        )
    }
}

// ---------------------------------------------------------------------------
// §9.8 Status indicator — icon + one line of text; never color alone (F-03)
// ---------------------------------------------------------------------------

enum class StatusTone {
    /** Olive check-circle — something is on/healthy. */
    Affirmative,

    /** Outline circle, muted — something is off; a neutral state, never an error. */
    Neutral,

    /** Warning icon, reserved for destructive-adjacent states only. */
    Attention,
}

/**
 * Visible system state for permissions and diagnostics (reference standard §9.8).
 * 20 dp icon + one `body-sm` line; the icon carries the tone together with the text.
 * Permission-off is [StatusTone.Neutral], never red. Announces as a full sentence.
 */
@Composable
fun StatusIndicator(
    text: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
) {
    val (icon, iconColor) = when (tone) {
        StatusTone.Affirmative -> Icons.Outlined.CheckCircle to
            MaterialTheme.colorScheme.primary
        StatusTone.Neutral -> Icons.Outlined.Circle to
            MaterialTheme.colorScheme.onSurfaceVariant
        StatusTone.Attention -> Icons.Outlined.Warning to
            MaterialTheme.colorScheme.error
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = SumiSpacing.minTouchTarget)
            .semantics(mergeDescendants = true) { this.contentDescription = text },
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Icon(
            imageVector = icon,
            contentDescription = null, // decorative; the full sentence is the announcement
            tint = iconColor,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(SumiSpacing.md))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ---------------------------------------------------------------------------
// §9.9 Progress module — 2 dp hairline track, olive fill, live metadata line
// ---------------------------------------------------------------------------

/**
 * Quiet progress feedback for asynchronous work (reference standard §9.9). [progress] is
 * null for an indeterminate loop (system cannot report); pass 0f..1f for determinate.
 * The live [liveLine] updates at most a few times per second and is announced politely.
 */
@Composable
fun ProgressModule(
    progress: Float?,
    liveLine: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite },
    ) {
        if (progress == null) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(2.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outlineVariant,
            )
        } else {
            val clamped = progress.coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { clamped },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outlineVariant,
            )
        }
        Spacer(Modifier.height(SumiSpacing.sm))
        Text(
            text = liveLine,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------------------
// §9.13 Fact stack — scannable claims separated by hairlines (setup 2)
// ---------------------------------------------------------------------------

data class FactRow(val fact: String, val qualifier: String)

/**
 * Three or more short claims as primary fact + one-line qualifier, separated by 1 dp
 * hairlines, 16 dp vertical padding per row (reference standard §9.13). No cards —
 * typography and rules carry the grouping.
 */
@Composable
fun FactStack(
    facts: List<FactRow>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        facts.forEachIndexed { index, fact ->
            if (index > 0) {
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
            Column(modifier = Modifier.padding(vertical = SumiSpacing.lg)) {
                Text(
                    text = fact.fact,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(SumiSpacing.xs))
                Text(
                    text = fact.qualifier,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// §9.12 Metadata / diagnostics row — label left, value right (tabular figures)
// ---------------------------------------------------------------------------

/**
 * Aligned label:value row (reference standard §9.12). Label left in `ink-secondary`,
 * value right-aligned with tabular figures in `ink`. When [tone] is set, the row uses
 * the StatusIndicator grammar (icon + full sentence announcement) instead of a bare value.
 */
@Composable
fun DiagnosticRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    tone: StatusTone? = null,
) {
    if (tone != null) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusIndicator(text = "$label — $value", tone = tone)
        }
        return
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .semantics(mergeDescendants = true) { this.contentDescription = "$label — $value" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = NumericBody.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
        )
    }
}

// ---------------------------------------------------------------------------
// §9.17 Category dot — decorative identity layer, never alone
// ---------------------------------------------------------------------------

/**
 * 10 dp category identity dot (reference standard §9.17). Decorative — excluded from
 * the semantics tree; the row's text label carries the meaning. Category colors appear
 * only as dots, bar segments and chart series, never as text or chrome.
 */
@Composable
fun CategoryDot(
    colorToken: String,
    modifier: Modifier = Modifier,
    size: Dp = DEFAULT_DOT_SIZE,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(categoryColorOrNeutral(colorToken))
            .semantics { },
    )
}

private val DEFAULT_DOT_SIZE = 10.dp

// ---------------------------------------------------------------------------
// §9.15 Chip — exclusive single-select pill (F-18)
// ---------------------------------------------------------------------------

/**
 * Single-select pill from a small set (reference standard §9.15): time ranges, focus
 * durations. Unselected: 1 dp hairline outline, `ink-secondary` label. Selected: accent
 * fill (`olive-200`/`olive-700`) with on-accent label, weight 500, announces its state.
 * Selection updates the owner immediately — there is no apply step.
 */
@Composable
fun SumiChip(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    showCheck: Boolean = false,
) {
    val outline = MaterialTheme.colorScheme.outlineVariant
    val selectedFill = MaterialTheme.colorScheme.primary
    val selectedLabel = MaterialTheme.colorScheme.onPrimary
    val unselectedLabel = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier
            .height(CHIP_HEIGHT)
            .clip(CircleShape)
            .background(if (selected) selectedFill else androidx.compose.ui.graphics.Color.Transparent)
            .border(
                width = 1.dp,
                color = if (selected) selectedFill else outline,
                shape = CircleShape,
            )
            .clickable(
                role = Role.RadioButton,
                onClickLabel = label,
                onClick = onSelect,
            )
            .semantics(mergeDescendants = true) {
                stateDescription = if (selected) "selected" else "not selected"
            }
            .padding(horizontal = SumiSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(SumiSpacing.xs),
    ) {
        if (selected && showCheck) {
            androidx.compose.material3.Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = selectedLabel,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) selectedLabel else unselectedLabel,
        )
    }
}

private val CHIP_HEIGHT = 40.dp

// ---------------------------------------------------------------------------
// §9.16 Duration slider — dotted track, accent handle, 5-minute steps (F-19)
// ---------------------------------------------------------------------------

/**
 * Custom session-length input (reference standard §9.16). Discrete [stepMinutes] steps;
 * the caller keeps chip and slider state in sync. Visual: dotted hairline track
 * ([chartBar] dots on a hairline baseline) with a thin accent handle; 48 dp vertical
 * touch band around the track. TalkBack: slider role, value announced in minutes.
 */
@Composable
fun SumiDurationSlider(
    valueMinutes: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: IntRange = DEFAULT_SLIDER_MIN..DEFAULT_SLIDER_MAX,
    stepMinutes: Int = DEFAULT_SLIDER_STEP,
) {
    val scheme = MaterialTheme.colorScheme
    val extended = sumiExtendedColors()
    Box(modifier = modifier.fillMaxWidth()) {
        // Dotted track: chart-bar dots on a hairline baseline (reference standard §9.16).
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(SLIDER_DOTS_HEIGHT)
                .align(Alignment.Center),
        ) {
            val diameter = DOT_DIAMETER.toPx()
            val gap = if (valueRange.last > valueRange.first) {
                diameter * 2.4f
            } else {
                diameter
            }
            var x = diameter / 2f
            while (x < size.width) {
                drawCircle(
                    color = extended.chartBar,
                    radius = diameter / 2f,
                    center = androidx.compose.ui.geometry.Offset(x, size.height / 2f),
                )
                x += gap
            }
        }
        androidx.compose.material3.Slider(
            value = valueMinutes.toFloat(),
            onValueChange = { raw ->
                val snapped = ((raw / stepMinutes).toInt() * stepMinutes)
                    .coerceIn(valueRange.first, valueRange.last)
                if (snapped != valueMinutes) onValueChange(snapped)
            },
            valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
            steps = ((valueRange.last - valueRange.first) / stepMinutes - 1).coerceAtLeast(0),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = SumiSpacing.minTouchTarget)
                .semantics {
                    contentDescription = "Session length, $valueMinutes minutes"
                },
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = scheme.primary,
                activeTrackColor = androidx.compose.ui.graphics.Color.Transparent,
                inactiveTrackColor = androidx.compose.ui.graphics.Color.Transparent,
                activeTickColor = androidx.compose.ui.graphics.Color.Transparent,
                inactiveTickColor = androidx.compose.ui.graphics.Color.Transparent,
            ),
        )
    }
}

private val SLIDER_DOTS_HEIGHT = 4.dp
private const val DOT_DIAMETER_DP = 2.2f
private val DOT_DIAMETER = DOT_DIAMETER_DP.dp
private const val DEFAULT_SLIDER_MIN = 5
private const val DEFAULT_SLIDER_MAX = 120
private const val DEFAULT_SLIDER_STEP = 5

// ---------------------------------------------------------------------------
// "What Sumi does not do" — bottom sheet (§10.1, F-11)
// ---------------------------------------------------------------------------

/**
 * The trust sheet: what Sumi does not do — shown from the welcome step and the Settings
 * privacy line. Four short claims, then the closing line. Quiet, factual, no badges.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun WhatSumiDoesNotDoSheet(
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = SHEET_RADIUS, topEnd = SHEET_RADIUS),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SumiSpacing.screenHorizontal)
                .padding(bottom = SumiSpacing.xxl),
        ) {
            Text(
                text = "What Sumi does not do",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(SumiSpacing.lg))
            SUMMI_NOT_DO_CLAIMS.forEach { claim ->
                FactRowLine(claim)
                if (claim != SUMMI_NOT_DO_CLAIMS.last()) {
                    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            Spacer(Modifier.height(SumiSpacing.lg))
            Text(
                text = "Everything Sumi knows stays on this device.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun FactRowLine(claim: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = SumiSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Icon(
            imageVector = Icons.Outlined.Close,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(SumiSpacing.md))
        Text(
            text = claim,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private val SUMMI_NOT_DO_CLAIMS = listOf(
    "It does not upload anything.",
    "It does not profile you.",
    "It does not block or punish.",
    "It does not need an account.",
)

private val SHEET_RADIUS = 28.dp
