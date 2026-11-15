package com.sumi.platform.widget

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.glance.GlanceComposable
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sumi.MainActivity

/** Resting radius for the widget surface — closest fixed approximation to the system
 *  app-widget background radius; the platform additionally masks S+ widgets to the launcher. */
internal const val WIDGET_CORNER_RADIUS = 24

/** Reserved extra consumed by MainActivity → SumiApp → the app-detail route. */
internal const val EXTRA_OPEN_APP_DETAIL = "com.sumi.widget.extra.OPEN_APP_DETAIL"

private fun cp(argb: Int): ColorProvider =
    ColorProvider(androidx.compose.ui.graphics.Color(argb))

/**
 * Tile container: tap anywhere opens Today (§7); permission and pending states route to
 * the app as well — the fix always lives inside it. Light appearance gets a hairline
 * outline so the tile survives pale wallpapers (§11); dark needs none.
 */
@Composable
@GlanceComposable
internal fun SumiWidgetSurface(
    palette: WidgetPalette,
    description: String,
    content: @Composable @GlanceComposable () -> Unit,
) {
    val surface = cp(palette.backgroundArgb)
    val contentModifier = GlanceModifier
        .fillMaxSize()
        .semantics { contentDescription = description }
        .clickable(actionStartActivity<MainActivity>())

    if (palette.dark) {
        Box(
            modifier = contentModifier.background(surface).cornerRadius(WIDGET_CORNER_RADIUS.dp),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    } else {
        Box(
            modifier = contentModifier
                .background(cp(palette.outlineArgb))
                .cornerRadius(WIDGET_CORNER_RADIUS.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(1.dp)
                    .background(surface)
                    .cornerRadius((WIDGET_CORNER_RADIUS - 1).dp),
                contentAlignment = Alignment.Center,
            ) {
                content()
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Shared state layouts
// ---------------------------------------------------------------------------

@Composable
@GlanceComposable
private fun PendingContent(snap: WidgetSnapshot) {
    val onSurface = cp(snap.palette.onSurfaceArgb)
    val muted = cp(snap.palette.mutedArgb)
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            PENDING_TITLE,
            style = TextStyle(color = onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium),
        )
        Spacer(GlanceModifier.height(4.dp))
        Text(
            PENDING_SUBTITLE,
            style = TextStyle(color = muted, fontSize = 11.sp, textAlign = TextAlign.Center),
        )
    }
}

@Composable
@GlanceComposable
private fun PermissionContent(snap: WidgetSnapshot) {
    val onSurface = cp(snap.palette.onSurfaceArgb)
    val muted = cp(snap.palette.mutedArgb)
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            PERMISSION_TITLE,
            style = TextStyle(color = onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium),
        )
        Spacer(GlanceModifier.height(4.dp))
        Text(
            PERMISSION_SUBTITLE,
            style = TextStyle(color = muted, fontSize = 11.sp, textAlign = TextAlign.Center),
        )
    }
}

// ---------------------------------------------------------------------------
// Version A — Compact ("Sumi — Screen time"). §5: bar, serif total, comparison.
// ---------------------------------------------------------------------------

@Composable
@GlanceComposable
internal fun CompactWidgetContent(snap: WidgetSnapshot) {
    val size = LocalSize.current
    val narrow = size.width < 210.dp
    val onSurface = cp(snap.palette.onSurfaceArgb)
    val muted = cp(snap.palette.mutedArgb)

    SumiWidgetSurface(snap.palette, accessibilitySummary(snap.view)) {
        when (snap.view) {
            is WidgetView.Total -> {
                val view = snap.view as WidgetView.Total
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .padding(horizontal = if (narrow) 10.dp else 14.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.Start,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    snap.barBitmapCompact?.let { bar ->
                        Image(
                            provider = ImageProvider(bar),
                            contentDescription = null, // decorative; the list and summary carry values
                            modifier = GlanceModifier.fillMaxWidth().height(6.dp),
                            contentScale = androidx.glance.layout.ContentScale.FillBounds,
                        )
                        Spacer(GlanceModifier.height(5.dp))
                    }
                    snap.totalBitmapBase?.let { total ->
                        Image(
                            provider = ImageProvider(total),
                            contentDescription = view.totalText,
                            modifier = GlanceModifier,
                            contentScale = androidx.glance.layout.ContentScale.Fit,
                        )
                    } ?: Text(
                        view.totalText,
                        style = TextStyle(color = onSurface, fontSize = 26.sp, fontWeight = FontWeight.Bold),
                    )
                    view.comparison?.let { comparison ->
                        Spacer(GlanceModifier.height(2.dp))
                        Text(
                            comparison,
                            style = TextStyle(
                                color = muted,
                                fontSize = if (narrow) 9.5.sp else 10.5.sp,
                            ),
                        )
                    }
                }
            }
            WidgetView.Pending -> PendingContent(snap)
            WidgetView.PermissionMissing -> PermissionContent(snap)
        }
    }
}

// ---------------------------------------------------------------------------
// Version B — Expanded ("Sumi — Screen time (expanded)"). §6
// Density ladder: tall (≥190 dp, 5 rows @ 40 dp) / comfortable (≥140 dp,
// 3 rows @ 30 dp) / tight (3 rows @ 24 dp, staleness footer omitted, total 26 sp).
// ---------------------------------------------------------------------------

@Composable
@GlanceComposable
internal fun ExpandedWidgetContent(snap: WidgetSnapshot) {
    val size = LocalSize.current
    val tall = size.height >= TALL_HEIGHT_DP
    val comfortable = size.height >= COMFORTABLE_HEIGHT_DP

    SumiWidgetSurface(
        snap.palette,
        accessibilitySummary(
            snap.view,
            if (tall) snap.rowsTallList else snap.rowsCompactList,
        ),
    ) {
        when (snap.view) {
            is WidgetView.Total -> {
                val view = snap.view as WidgetView.Total
                val rows = if (tall) snap.rowsTallList else snap.rowsCompactList
                val bar = if (tall) snap.barBitmapTall else snap.barBitmapCompact
                val visibleRows = rows.take(if (tall) MAX_ROWS_TALL else MAX_ROWS_BASE)
                val rowHeight = if (tall) 40 else if (comfortable) 30 else 24
                val verticalPadding = if (tall) 14.dp else if (comfortable) 10.dp else 8.dp
                val showFooter = comfortable || tall
                val muted = cp(snap.palette.mutedArgb)

                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = verticalPadding),
                    horizontalAlignment = Alignment.Start,
                    verticalAlignment = if (tall) Alignment.Top else Alignment.CenterVertically,
                ) {
                    Text(
                        EYEBROW_TITLE,
                        style = TextStyle(color = muted, fontSize = 10.sp, fontWeight = FontWeight.Medium),
                    )
                    (if (tall) snap.totalBitmapTall else snap.totalBitmapBase)?.let { total ->
                        Image(
                            provider = ImageProvider(total),
                            contentDescription = view.totalText,
                            modifier = GlanceModifier.padding(top = 2.dp),
                            contentScale = androidx.glance.layout.ContentScale.Fit,
                        )
                    } ?: Text(
                        view.totalText,
                        style = TextStyle(
                            color = cp(snap.palette.onSurfaceArgb),
                            fontSize = if (tall) 30.sp else 26.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    view.comparison?.let { comparison ->
                        Text(
                            comparison,
                            style = TextStyle(color = muted, fontSize = 10.5.sp),
                            modifier = GlanceModifier.padding(top = 1.dp),
                        )
                    }
                    bar?.let {
                        Image(
                            provider = ImageProvider(it),
                            contentDescription = null,
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .padding(top = 6.dp)
                                .height(8.dp),
                            contentScale = androidx.glance.layout.ContentScale.FillBounds,
                        )
                    }
                    if (visibleRows.isNotEmpty()) {
                        Column(modifier = GlanceModifier.fillMaxSize().padding(top = if (tall) 10.dp else 6.dp)) {
                            visibleRows.forEach { row ->
                                AppRow(snap, row, rowHeight)
                            }
                            Spacer(GlanceModifier.defaultWeight())
                        }
                    }
                    view.goal?.let { goal ->
                        Text(
                            goal.line,
                            style = TextStyle(color = muted, fontSize = 10.5.sp),
                            modifier = GlanceModifier.padding(top = 6.dp),
                        )
                    }
                    if (showFooter) {
                        snap.staleText?.let { stale ->
                            Text(
                                stale,
                                style = TextStyle(
                                    color = muted,
                                    fontSize = 9.sp,
                                    textAlign = TextAlign.End,
                                ),
                                modifier = GlanceModifier.fillMaxWidth().padding(top = 2.dp),
                            )
                        }
                    }
                }
            }
            WidgetView.Pending -> PendingContent(snap)
            WidgetView.PermissionMissing -> PermissionContent(snap)
        }
    }
}

@Composable
@GlanceComposable
private fun AppRow(
    snap: WidgetSnapshot,
    row: RankedRowView,
    rowHeight: Int,
) {
    val segmentArgb = snap.palette.barSegmentArgb(row.categoryColorToken)
    val seg = cp(segmentArgb)
    val nameColor = cp(snap.palette.onSurfaceArgb)
    val muted = cp(snap.palette.mutedArgb)
    val rowIcon = ROW_ICON_DP.dp

    val context = LocalContext.current
    val rowIntent = remember(row.packageName) {
        Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_OPEN_APP_DETAIL, row.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
    }

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(rowHeight.dp)
            .clickable(androidx.glance.appwidget.action.actionStartActivity(rowIntent, actionParametersOf()))
            .semantics { contentDescription = "Open ${row.displayName} — ${row.durationText} today" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = snap.iconBitmaps[row.packageName]
        if (icon != null) {
            Image(
                provider = ImageProvider(icon),
                contentDescription = null, // the row's semantics carry the name
                modifier = GlanceModifier.width(rowIcon).height(rowIcon),
                contentScale = androidx.glance.layout.ContentScale.Fit,
            )
        } else {
            Box(
                modifier = GlanceModifier
                    .width(rowIcon)
                    .height(rowIcon)
                    .background(seg)
                    .cornerRadius(rowIcon),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    row.letterMark,
                    style = TextStyle(
                        color = cp(snap.palette.monogramOn(segmentArgb)),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }
        Spacer(GlanceModifier.width(10.dp))
        Text(
            row.displayName,
            style = TextStyle(color = nameColor, fontSize = 12.sp, fontWeight = FontWeight.Medium),
        )
        Spacer(GlanceModifier.defaultWeight())
        Column(horizontalAlignment = Alignment.End) {
            Box(
                modifier = GlanceModifier
                    .width(14.dp)
                    .height(3.dp)
                    .background(seg)
                    .cornerRadius(2.dp),
            ) {}
            Spacer(GlanceModifier.height(3.dp))
            Text(
                row.durationText,
                style = TextStyle(color = muted, fontSize = 11.sp),
            )
        }
    }
}

private const val ROW_ICON_DP = 28
private const val MAX_ROWS_BASE = 3
private const val MAX_ROWS_TALL = 5
private val TALL_HEIGHT_DP = 190.dp
private val COMFORTABLE_HEIGHT_DP = 140.dp
