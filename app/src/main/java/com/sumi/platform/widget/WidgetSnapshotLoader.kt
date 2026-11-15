package com.sumi.platform.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Paint
import android.graphics.Typeface
import com.sumi.core.datastore.PreferencesRepository
import com.sumi.core.datastore.ThemeMode
import com.sumi.core.designsystem.theme.SumiColorTokens
import com.sumi.core.designsystem.theme.categoryColorIndex
import com.sumi.core.database.dao.ApplicationDao
import com.sumi.data.categories.CategoryRepository
import com.sumi.domain.common.Clock
import com.sumi.domain.common.DateKey
import com.sumi.domain.common.TimeZoneProvider
import com.sumi.domain.insights.InsightsRange
import com.sumi.domain.usage.IntervalMath
import com.sumi.domain.usage.UsageRepository
import com.sumi.platform.applications.AppIconLoader
import com.sumi.platform.usage.UsageAccessChecker
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Everything the Glance renderers need, loaded off the composable via the Hilt entry
 * point (docs/WIDGET.md §12). All strings, fractions, colors, and bitmaps are computed
 * here — never inside composition, never on the main thread during the widget update.
 * Icons may be absent (uninstalled/hidden packages); the rows carry a letter-mark
 * fallback and never show a placeholder icon.
 */
internal data class WidgetSnapshot(
    val hasUsageAccess: Boolean,
    /** Today's total or null pending first sync. */
    val totalMs: Long?,
    val totalText: String,
    /** Prebuilt comparison line ("About … yesterday at this time.") or null to omit. */
    val comparison: String?,
    val goal: WidgetGoalLine?,
    /** "Updated HH:mm" only when the last committed collection pass is older than an hour; else null. */
    val staleText: String?,
    /** Rows for the expanded widget's default 4×2 layout (3 rows). */
    val rowsCompactList: List<RankedRowView>,
    /** Rows for the expanded widget at 4×3 or taller (5 rows). */
    val rowsTallList: List<RankedRowView>,
    /** Resolved color set (background/text/muted/stone + category accessor) for the mode. */
    val palette: WidgetPalette,
    val iconBitmaps: Map<String, android.graphics.Bitmap>,
    /** Serif total bitmaps: one at the base 26 sp, one at the tall 30 sp. */
    val totalBitmapBase: android.graphics.Bitmap?,
    val totalBitmapTall: android.graphics.Bitmap?,
    /** Drawn bars (list order + remainder, rounded ends, hairline gaps); null at zero usage. */
    val barBitmapCompact: android.graphics.Bitmap?,
    val barBitmapTall: android.graphics.Bitmap?,
    val view: WidgetView,
)

/** Resolved color set for the widget; the exact values of the mode's tokens (§11). */
internal data class WidgetPalette(
    val dark: Boolean,
    val backgroundArgb: Int,
    val onSurfaceArgb: Int,
    val mutedArgb: Int,
    val remainderArgb: Int,
    val gapArgb: Int,
    val outlineArgb: Int,
    /** One entry per CategoryColors index; stone-1 for the uncategorized residual (never a ranked category). */
    val barSegmentArgb: (String?) -> Int,
    /** Contrast glyph color for a monogram sitting on the given segment color. */
    val monogramOn: (argb: Int) -> Int,
)

internal object WidgetSnapshotLoader {

    suspend fun load(context: Context): WidgetSnapshot {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            SumiWidgetEntryPoint::class.java,
        )
        val preferences = entryPoint.preferencesRepository()
        val themeMode = preferences.themeMode.first()
        val config = context.resources.configuration
        val dark = when (themeMode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> (config.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        }
        val fontScale = config.fontScale
        val density = config.densityDpi / 160f
        val palette = widgetPalette(dark)

        val clock = entryPoint.clock()
        val zone = entryPoint.timeZoneProvider().currentZone()
        val now = clock.nowUtcMs()
        val today = DateKey.of(now, zone)
        val yesterday = DateKey.shift(today, -1L)
        val repository = entryPoint.usageRepository()

        val total = repository.observeDailyTotal(today).first()
        val totalMs = total?.totalDurationMs

        // Comparison: sessions clipped to the same wall-clock window (never partial-vs-whole).
        val yesterdayClipped = if (totalMs == null) {
            null
        } else {
            val sessions = repository.observeSessionsForDateKeys(listOf(yesterday)).first()
            if (sessions.isEmpty()) {
                null
            } else {
                val (from, to) = InsightsRange.TODAY.comparisonWindow(zone, now)
                IntervalMath.unionMsInWindow(sessions, from, to, now)
            }
        }

        val access = entryPoint.usageAccessChecker().hasUsageAccess()
        val goal = preferences.dailyGoal.first()
        val view = resolveWidgetView(totalMs, access, goal, yesterdayClipped)

        val staleText = repository.lastSuccessfulCollectionUtcMs()?.let { last ->
            if (staleFooterText(last, now)) {
                "Updated ${
                    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
                        .format(Instant.ofEpochMilli(last).atZone(zone))
                }"
            } else {
                null
            }
        }

        // Ranked breakdowns for both densities of the expanded list (bar must match the
        // visible rows), computed from the same denominator as Today's shares.
        val perApp = if (view is WidgetView.Total && view.totalMs > 0L) {
            repository.observeDailyAppUsage(today).first()
        } else {
            emptyList()
        }
        val applications = if (perApp.isNotEmpty()) {
            entryPoint.applicationDao().observeInstalled().first()
        } else {
            emptyList()
        }
        val categories = if (perApp.isNotEmpty()) {
            entryPoint.categoryRepository().observeCategories().first()
        } else {
            emptyList()
        }
        val resolvedTotal = (view as? WidgetView.Total)?.totalMs ?: 0L
        val breakdown3 = rankedBreakdown(resolvedTotal, perApp, applications, categories, limit = 3)
        val breakdown5 = rankedBreakdown(resolvedTotal, perApp, applications, categories, limit = 5)

        // Clip names to the row budget with the platform paint at the widget's exact scale.
        val namePaint = Paint().apply {
            textSize = NAME_TEXT_SP * fontScale * density
        }
        val nameBudget = NAME_BUDGET_DP * density
        fun ellipsizeName(rows: List<RankedRowView>) = rows.map {
            it.copy(displayName = WidgetBrushes.ellipsize(it.displayName, nameBudget, namePaint))
        }
        val rows3 = ellipsizeName(breakdown3.rows)
        val rows5 = ellipsizeName(breakdown5.rows)

        // App icons: launcher icon when resolvable, else the letter-mark fallback rendered by the row.
        val iconLoader = entryPoint.appIconLoader()
        val densityPx = (ICON_DP * density).toInt().coerceAtLeast(24)
        val iconBitmaps = (rows5.map { it.packageName } + rows3.map { it.packageName })
            .distinct()
            .mapNotNull { pkg ->
                iconLoader.iconFor(pkg)?.let { drawable ->
                    pkg to WidgetBrushes.iconBitmap(drawable, densityPx)
                }
            }
            .toMap()

        // The serif anchor: bundled Literata. Glance `Text` cannot carry it (system fonts
        // only), so it is drawn in the app process and shown as an image (§11).
        val literata = runCatching {
            Typeface.create(context.resources, com.sumi.R.font.literata_regular, Typeface.NORMAL)
        }.getOrDefault(Typeface.SERIF)
        val totalText = (view as? WidgetView.Total)?.totalText ?: ""
        val totalBitmapBase = if (view is WidgetView.Total) {
            WidgetBrushes.serifTextBitmap(
                totalText,
                palette.onSurfaceArgb,
                fontScale,
                density,
                baseSp = TOTAL_BASE_SP,
                bold = false,
                typeface = literata,
            )
        } else {
            null
        }
        val totalBitmapTall = if (view is WidgetView.Total) {
            WidgetBrushes.serifTextBitmap(
                totalText,
                palette.onSurfaceArgb,
                fontScale,
                density,
                baseSp = TOTAL_TALL_SP,
                bold = false,
                typeface = literata,
            )
        } else {
            null
        }

        // Bar bitmaps: one per expanded density (3-row / 5-row reconciliation).
        fun barBitmap(breakdown: AppBreakdown): android.graphics.Bitmap? {
            if (view !is WidgetView.Total || view.totalMs <= 0L) return null
            val segments = breakdown.segments.map { seg ->
                seg.fraction to palette.barSegmentArgb(seg.colorToken)
            }
            if (segments.isEmpty()) return null
            return WidgetBrushes.segmentedBar(
                segments = segments,
                gapPx = GAP_PX * density,
                gapArgb = palette.gapArgb,
            )
        }
        val barBitmapCompact = barBitmap(breakdown3)
        val barBitmapTall = barBitmap(breakdown5)

        return WidgetSnapshot(
            hasUsageAccess = access,
            totalMs = totalMs,
            totalText = totalText,
            comparison = (view as? WidgetView.Total)?.comparison,
            goal = (view as? WidgetView.Total)?.goal,
            staleText = staleText,
            rowsCompactList = rows3,
            rowsTallList = rows5,
            palette = palette,
            iconBitmaps = iconBitmaps,
            totalBitmapBase = totalBitmapBase,
            totalBitmapTall = totalBitmapTall,
            barBitmapCompact = barBitmapCompact,
            barBitmapTall = barBitmapTall,
            view = view,
        )
    }

    /**
     * Widget surfaces: warm ivory (elevated card + hairline outline) on light wallpapers,
     * warm charcoal (surface-2) on dark — the established tokens, never pure black/white,
     * never off-token hexes. The bar's gaps read as tile-colored separators.
     */
    private fun widgetPalette(dark: Boolean): WidgetPalette {
        val background = if (dark) SumiColorTokens.SurfaceDark else SumiColorTokens.SurfaceElevatedLight
        val onSurface = if (dark) SumiColorTokens.OnBackgroundDark else SumiColorTokens.OnBackgroundLight
        val muted = if (dark) SumiColorTokens.OnSurfaceMutedDark else SumiColorTokens.OnSurfaceMutedLight
        val stone = if (dark) SumiColorTokens.Stone1Dark else SumiColorTokens.Stone1Light
        val categories = if (dark) SumiColorTokens.CategoryColorsDark else SumiColorTokens.CategoryColorsLight
        val outline = if (dark) SumiColorTokens.SurfaceDark else SumiColorTokens.BorderSubtleLight
        return WidgetPalette(
            dark = dark,
            backgroundArgb = background.toArgbInt(),
            onSurfaceArgb = onSurface.toArgbInt(),
            mutedArgb = muted.toArgbInt(),
            remainderArgb = stone.toArgbInt(),
            gapArgb = background.toArgbInt(),
            outlineArgb = outline.toArgbInt(),
            barSegmentArgb = { token ->
                when (token) {
                    null -> stone.toArgbInt()
                    "stone-1", "categoryNeutral", "neutral" -> stone.toArgbInt()
                    else -> categories
                        .getOrElse(categoryColorIndex(token)) { categories.first() }
                        .toArgbInt()
                }
            },
            monogramOn = { argb ->
                if (relativeLuminance(argb) > 0.5) DARK_GLYPH else LIGHT_GLYPH
            },
        )
    }

    private fun androidx.compose.ui.graphics.Color.toArgbInt(): Int =
        android.graphics.Color.argb(
            (alpha * 255).toInt(),
            (red * 255).toInt(),
            (green * 255).toInt(),
            (blue * 255).toInt(),
        )

    private fun relativeLuminance(argb: Int): Float {
        val r = android.graphics.Color.red(argb) / 255f
        val g = android.graphics.Color.green(argb) / 255f
        val b = android.graphics.Color.blue(argb) / 255f
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    private const val NAME_TEXT_SP = 12f
    private const val NAME_BUDGET_DP = 110f
    private const val ICON_DP = 28f
    private const val TOTAL_BASE_SP = 26f
    private const val TOTAL_TALL_SP = 30f
    private const val GAP_PX = 1.5f
    private const val DARK_GLYPH = 0xFF1E241B.toInt()
    private const val LIGHT_GLYPH = 0xFFF7F7F2.toInt()
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface SumiWidgetEntryPoint {
    fun usageRepository(): UsageRepository
    fun preferencesRepository(): PreferencesRepository
    fun usageAccessChecker(): UsageAccessChecker
    fun clock(): Clock
    fun timeZoneProvider(): TimeZoneProvider
    fun applicationDao(): ApplicationDao
    fun categoryRepository(): CategoryRepository
    fun appIconLoader(): AppIconLoader
}
