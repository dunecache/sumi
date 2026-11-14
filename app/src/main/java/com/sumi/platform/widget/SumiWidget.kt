package com.sumi.platform.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceComposable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.sumi.MainActivity
import com.sumi.core.datastore.DailyGoalConfig
import com.sumi.core.datastore.PreferencesRepository
import com.sumi.core.datastore.ThemeMode
import com.sumi.core.designsystem.component.formatDuration
import com.sumi.core.designsystem.theme.SumiColorTokens
import com.sumi.domain.common.Clock
import com.sumi.domain.common.DateKey
import com.sumi.domain.common.TimeZoneProvider
import com.sumi.domain.usage.UsageRepository
import com.sumi.platform.usage.UsageAccessChecker
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope

/** Resting radius for the widget surface (Sumi r-md = 16 dp). */
private const val WIDGET_CORNER_RADIUS = 16

/**
 * The Sumi home-screen widget (docs/WIDGET.md): today's screen time, a thin goal
 * progress rule when a daily goal exists, and a calm supporting line. Never a mini
 * Today screen; tapping anywhere opens Sumi, which lands on Today.
 *
 * Theme policy (widget reference §4.3): the widget renders from the app's theme
 * preference — [ThemeMode.SYSTEM] resolves the current system night setting at render
 * time — on the same refresh cadence as the app (open/foreground, usage collection,
 * theme change, plus the 15-minute collection safety net). Forced light/dark always
 * renders the forced variant. Widget pixels are hosted by the launcher; rendering was
 * not verified on a device in this change.
 */
class SumiWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(WIDTH_COMPACT.dp, HEIGHT_BASELINE.dp),
            DpSize(WIDTH_MEDIUM.dp, HEIGHT_BASELINE.dp),
            DpSize(WIDTH_LARGE.dp, HEIGHT_LARGE.dp),
        ),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetSnapshotLoader.load(context)
        provideContent { WidgetContent(snapshot) }
    }

    private companion object {
        const val WIDTH_COMPACT = 140
        const val WIDTH_MEDIUM = 250
        const val WIDTH_LARGE = 330
        const val HEIGHT_BASELINE = 110
        const val HEIGHT_LARGE = 148
    }
}

/** Receiver registered in the manifest; the system instantiates it via reflection. */
class SumiWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SumiWidget()
}

/** Everything the renderer needs, loaded off the composable via the Hilt entry point. */
internal data class WidgetSnapshot(
    val hasUsageAccess: Boolean,
    /** null when no aggregate row exists yet (pending first sync). */
    val totalMs: Long?,
    val goal: DailyGoalConfig,
    val darkTheme: Boolean,
)

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface SumiWidgetEntryPoint {
    fun usageRepository(): UsageRepository
    fun preferencesRepository(): PreferencesRepository
    fun usageAccessChecker(): UsageAccessChecker
    fun clock(): Clock
    fun timeZoneProvider(): TimeZoneProvider
}

internal object WidgetSnapshotLoader {
    suspend fun load(context: Context): WidgetSnapshot {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            SumiWidgetEntryPoint::class.java,
        )
        val preferences = entryPoint.preferencesRepository()
        val themeMode = preferences.themeMode.first()
        val systemDark = (
            context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            ) == Configuration.UI_MODE_NIGHT_YES
        val dark = when (themeMode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> systemDark
        }
        val today = DateKey.of(
            entryPoint.clock().nowUtcMs(),
            entryPoint.timeZoneProvider().currentZone(),
        )
        val total = entryPoint.usageRepository().observeDailyTotal(today).first()
        return WidgetSnapshot(
            hasUsageAccess = entryPoint.usageAccessChecker().hasUsageAccess(),
            totalMs = total?.totalDurationMs,
            goal = preferences.dailyGoal.first(),
            darkTheme = dark,
        )
    }
}

/** Refresh hook for paths that may change what the widget shows. */
internal object WidgetUpdater {
    /** Fire-and-forget (app open, resume, 60 s tick, theme change). */
    fun refreshAsync(context: Context) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
            .launch { SumiWidget().updateAll(context.applicationContext) }
    }

    /** Suspending (collection worker, where we are already off the main thread). */
    suspend fun refreshNow(context: Context) {
        SumiWidget().updateAll(context.applicationContext)
    }
}

private data class Palette(
    val background: androidx.compose.ui.graphics.Color,
    val onSurface: androidx.compose.ui.graphics.Color,
    val muted: androidx.compose.ui.graphics.Color,
    val progress: androidx.compose.ui.graphics.Color,
    val track: androidx.compose.ui.graphics.Color,
)

private fun palette(dark: Boolean): Palette = if (dark) {
    Palette(
        background = SumiColorTokens.BackgroundDark,
        onSurface = SumiColorTokens.OnBackgroundDark,
        muted = SumiColorTokens.OnSurfaceMutedDark,
        progress = SumiColorTokens.AccentStrongDark,
        track = SumiColorTokens.Matcha1Dark,
    )
} else {
    Palette(
        background = SumiColorTokens.BackgroundLight,
        onSurface = SumiColorTokens.OnBackgroundLight,
        muted = SumiColorTokens.OnSurfaceMutedLight,
        progress = SumiColorTokens.AccentStrongLight,
        track = SumiColorTokens.Matcha1Light,
    )
}

private fun ColorProvider(color: androidx.compose.ui.graphics.Color): ColorProvider =
    ColorProvider(color.value)

@GlanceComposable
private fun WidgetContent(snapshot: WidgetSnapshot) {
    val palette = palette(snapshot.darkTheme)
    val view = resolveWidgetView(snapshot.totalMs, snapshot.hasUsageAccess, snapshot.goal)
    val size = LocalSize.current
    val breakpoint = pickBreakpoint(size.width.value.toInt(), size.height.value.toInt())

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(palette.background))
            .cornerRadius(WIDGET_CORNER_RADIUS.dp)
            .clickable(actionStartActivity<MainActivity>())
            .semantics { contentDescription = accessibilitySummary(view) },
        contentAlignment = Alignment.CenterStart,
    ) {
        when (view) {
            is WidgetView.Total -> TotalLayout(view, palette, breakpoint)
            WidgetView.Pending -> PendingLayout(palette, breakpoint)
            WidgetView.PermissionMissing -> PermissionLayout(palette, breakpoint)
        }
    }
}

@GlanceComposable
private fun WordMark(muted: ColorProvider) {
    Text(
        WORD_MARK,
        style = TextStyle(color = muted, fontSize = 11.sp, fontWeight = FontWeight.Medium),
    )
}

@GlanceComposable
private fun TotalLayout(
    view: WidgetView.Total,
    palette: Palette,
    breakpoint: WidgetBreakpoint,
) {
    val onSurface = ColorProvider(palette.onSurface)
    val muted = ColorProvider(palette.muted)
    val progress = ColorProvider(palette.progress)
    val track = ColorProvider(palette.track)
    val zero = view.totalMs == 0L
    val supporting = when {
        zero -> ZERO_USAGE_LINE
        view.goal != null -> view.goal.statusLine
        else -> NO_GOAL_LINE
    }
    val percentPart = view.goal?.let {
        when {
            zero -> null
            breakpoint == WidgetBreakpoint.LARGE -> it.percentText
            else -> it.percentShort
        }
    }
    val supportingLine = listOfNotNull(percentPart, supporting).joinToString(" · ")
    val totalSp = when (breakpoint) {
        WidgetBreakpoint.COMPACT -> 28.sp
        WidgetBreakpoint.MEDIUM -> 32.sp
        WidgetBreakpoint.LARGE -> 36.sp
    }

    Column(
        modifier = GlanceModifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WordMark(muted)
        Text(
            formatDuration(view.totalMs),
            style = TextStyle(color = onSurface, fontSize = totalSp, fontWeight = FontWeight.Bold),
        )
        if (breakpoint != WidgetBreakpoint.COMPACT && view.goal != null) {
            Spacer(GlanceModifier.height(6.dp))
            LinearProgressIndicator(
                progress = view.goal.fraction,
                modifier = GlanceModifier.fillMaxWidth().height(3.dp),
                color = progress,
                backgroundColor = track,
            )
            Spacer(GlanceModifier.height(4.dp))
        }
        if (breakpoint == WidgetBreakpoint.LARGE && view.goal != null && !zero) {
            // Large breakpoints get the full reference copy in two calm lines.
            Text(view.goal.percentText, style = TextStyle(color = muted, fontSize = 12.sp))
            Text(supporting, style = TextStyle(color = muted, fontSize = 12.sp))
        } else {
            Text(supportingLine, style = TextStyle(color = muted, fontSize = 12.sp))
        }
    }
}

@GlanceComposable
private fun PendingLayout(palette: Palette, breakpoint: WidgetBreakpoint) {
    val onSurface = ColorProvider(palette.onSurface)
    val muted = ColorProvider(palette.muted)
    Column(
        modifier = GlanceModifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WordMark(muted)
        Text(
            PENDING_PLACEHOLDER,
            style = TextStyle(
                color = onSurface,
                fontSize = if (breakpoint == WidgetBreakpoint.LARGE) 36.sp else 32.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Text(PENDING_LINE, style = TextStyle(color = muted, fontSize = 12.sp))
    }
}

@GlanceComposable
private fun PermissionLayout(palette: Palette, breakpoint: WidgetBreakpoint) {
    val onSurface = ColorProvider(palette.onSurface)
    val muted = ColorProvider(palette.muted)
    Column(
        modifier = GlanceModifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WordMark(muted)
        Text(
            PERMISSION_TITLE,
            style = TextStyle(color = onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium),
        )
        Spacer(GlanceModifier.height(4.dp))
        Text(PERMISSION_ACTION, style = TextStyle(color = muted, fontSize = 11.sp))
    }
}
