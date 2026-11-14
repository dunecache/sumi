package com.sumi.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Kanso Matcha Material 3 themes over the canonical tokens, reconciled with the
 * 2026-11-14 "Design Reference Standard v1.1" critique (DESIGN.md §14). No alarm red for
 * ordinary usage (the `error` role exists for data-deletion confirmation only); no
 * dynamic color — Sumi keeps one recognizable identity across devices. See DESIGN.md §2–§3.
 */

private val LightScheme = lightColorScheme(
    primary = SumiColorTokens.AccentStrongLight,
    onPrimary = SumiColorTokens.OnAccentLight,
    primaryContainer = SumiColorTokens.SurfaceAccentLight,
    onPrimaryContainer = SumiColorTokens.OnBackgroundLight,
    secondary = SumiColorTokens.AccentPrimaryLight,
    onSecondary = SumiColorTokens.OnAccentLight,
    secondaryContainer = SumiColorTokens.AccentSecondaryLight,
    onSecondaryContainer = SumiColorTokens.OnBackgroundLight,
    tertiary = SumiColorTokens.ClayLight,
    onTertiary = SumiColorTokens.OnAccentLight,
    tertiaryContainer = SumiColorTokens.SurfaceSunkenLight,
    onTertiaryContainer = SumiColorTokens.OnBackgroundLight,
    background = SumiColorTokens.BackgroundLight,
    onBackground = SumiColorTokens.OnBackgroundLight,
    surface = SumiColorTokens.BackgroundLight,
    onSurface = SumiColorTokens.OnBackgroundLight,
    surfaceVariant = SumiColorTokens.SurfaceLight,
    onSurfaceVariant = SumiColorTokens.OnSurfaceMutedLight,
    // Tonal ladder, lowest → highest (sunken → panel → elevated):
    surfaceContainerLowest = SumiColorTokens.SurfaceSunkenLight,
    surfaceContainerLow = SumiColorTokens.SurfaceLight,
    surfaceContainer = SumiColorTokens.SurfaceLight,
    surfaceContainerHigh = SumiColorTokens.SurfaceElevatedLight,
    surfaceContainerHighest = SumiColorTokens.SurfaceElevatedLight,
    outline = SumiColorTokens.BorderStrongLight,
    outlineVariant = SumiColorTokens.BorderSubtleLight,
    error = SumiColorTokens.DangerLight,
    onError = SumiColorTokens.OnAccentLight,
    errorContainer = SumiColorTokens.DiffRemovedBgLight,
    onErrorContainer = SumiColorTokens.DiffRemovedFgLight,
    inverseSurface = SumiColorTokens.OnBackgroundLight,
    inverseOnSurface = SumiColorTokens.BackgroundLight,
    inversePrimary = SumiColorTokens.AccentPrimaryDark,
)

private val DarkScheme = darkColorScheme(
    primary = SumiColorTokens.AccentStrongDark,
    onPrimary = SumiColorTokens.OnAccentDark,
    primaryContainer = SumiColorTokens.SurfaceAccentDark,
    onPrimaryContainer = SumiColorTokens.OnBackgroundDark,
    secondary = SumiColorTokens.AccentSecondaryDark,
    onSecondary = SumiColorTokens.OnAccentDark,
    secondaryContainer = SumiColorTokens.AccentSecondaryDark,
    onSecondaryContainer = SumiColorTokens.BackgroundDark,
    tertiary = SumiColorTokens.ClayDark,
    onTertiary = SumiColorTokens.OnAccentDark,
    tertiaryContainer = SumiColorTokens.SurfaceElevatedDark,
    onTertiaryContainer = SumiColorTokens.OnBackgroundDark,
    background = SumiColorTokens.BackgroundDark,
    onBackground = SumiColorTokens.OnBackgroundDark,
    surface = SumiColorTokens.BackgroundDark,
    onSurface = SumiColorTokens.OnBackgroundDark,
    surfaceVariant = SumiColorTokens.SurfaceDark,
    onSurfaceVariant = SumiColorTokens.OnSurfaceMutedDark,
    surfaceContainerLowest = SumiColorTokens.SurfaceSunkenDark,
    surfaceContainerLow = SumiColorTokens.SurfaceDark,
    surfaceContainer = SumiColorTokens.SurfaceDark,
    surfaceContainerHigh = SumiColorTokens.SurfaceElevatedDark,
    surfaceContainerHighest = SumiColorTokens.SurfaceElevatedDark,
    outline = SumiColorTokens.BorderStrongDark,
    outlineVariant = SumiColorTokens.BorderSubtleDark,
    error = SumiColorTokens.DangerDark,
    onError = SumiColorTokens.OnAccentDark,
    errorContainer = SumiColorTokens.DiffRemovedBgDark,
    onErrorContainer = SumiColorTokens.DiffRemovedFgDark,
    inverseSurface = SumiColorTokens.OnBackgroundDark,
    inverseOnSurface = SumiColorTokens.BackgroundDark,
    inversePrimary = SumiColorTokens.AccentPrimaryLight,
)

/** Extra semantic colors outside the Material scheme (status, focus, categories). */
data class SumiExtendedColors(
    val onSurfaceMuted: Color,
    val accent: Color, // decorative fills only — use colorScheme.primary for text-safe accents
    val onAccent: Color,
    val accentContainer: Color,
    val selection: Color,
    val focus: Color,
    val success: Color,
    val warning: Color,
    val info: Color,
    val categories: List<Color>, // canonical charts palette; pair with labels, never color-alone
    val stone: Color, // stone-1 — uncategorized residual bucket, never a ranked category
    val matchaTrack: Color, // matcha-1 — quiet track fill for the daily intention ring
    val terracotta: Color, // warning hue — used only for the over-goal state, never a category
    val navPill: Color, // bottom-nav active indicator pill (triple-coded selected state)
    val onNavPill: Color, // icon + label on the active pill — always >= 7:1 against navPill
    val chartBar: Color, // chart bars and analytic progress fills
    val disabledFill: Color, // disabled control fill (reference standard §8.1)
    val disabledLabel: Color, // disabled control text
)

private val LightExtended = SumiExtendedColors(
    onSurfaceMuted = SumiColorTokens.OnSurfaceMutedLight,
    accent = SumiColorTokens.AccentPrimaryLight,
    onAccent = SumiColorTokens.OnAccentLight,
    accentContainer = SumiColorTokens.SurfaceAccentLight,
    selection = SumiColorTokens.SelectionLight,
    focus = SumiColorTokens.FocusLight,
    success = SumiColorTokens.SuccessLight,
    warning = SumiColorTokens.WarningLight,
    info = SumiColorTokens.InfoLight,
    categories = SumiColorTokens.CategoryColorsLight,
    stone = SumiColorTokens.Stone1Light,
    matchaTrack = SumiColorTokens.Matcha1Light,
    terracotta = SumiColorTokens.TerracottaLight,
    navPill = SumiColorTokens.SelectionLight, // light theme: matcha-1 pill (no separate measured value)
    onNavPill = SumiColorTokens.OnBackgroundLight, // 10.6:1 on #DCE2D1
    chartBar = SumiColorTokens.ChartBarLight,
    disabledFill = SumiColorTokens.DisabledFillLight,
    disabledLabel = SumiColorTokens.DisabledLabelLight,
)

private val DarkExtended = SumiExtendedColors(
    onSurfaceMuted = SumiColorTokens.OnSurfaceMutedDark,
    accent = SumiColorTokens.AccentPrimaryDark,
    onAccent = SumiColorTokens.OnAccentDark,
    accentContainer = SumiColorTokens.SurfaceAccentDark,
    selection = SumiColorTokens.SelectionDark,
    focus = SumiColorTokens.FocusDark,
    success = SumiColorTokens.SuccessDark,
    warning = SumiColorTokens.WarningDark,
    info = SumiColorTokens.InfoDark,
    categories = SumiColorTokens.CategoryColorsDark,
    stone = SumiColorTokens.Stone1Dark,
    matchaTrack = SumiColorTokens.Matcha1Dark,
    terracotta = SumiColorTokens.TerracottaDark,
    navPill = SumiColorTokens.Olive100Dark,
    onNavPill = SumiColorTokens.OnAccentDark, // 8.7:1 on #E5EEE2
    chartBar = SumiColorTokens.ChartBarDark,
    disabledFill = SumiColorTokens.DisabledFillDark,
    disabledLabel = SumiColorTokens.DisabledLabelDark,
)

val LocalSumiExtendedColors = staticCompositionLocalOf { LightExtended }

/** Extended semantic colors for the current theme (provided by [SumiTheme]). */
@Composable
fun sumiExtendedColors(): SumiExtendedColors = LocalSumiExtendedColors.current

/**
 * Resolves a category color token against the current theme.
 *
 * Recognizes the primitive aliases the canonical `charts.json` carries (`matcha-4`,
 * `mist`, `clay-2`, `wisteria`, `ochre`, `method`), the positional `categoryColor{n}`
 * tokens persisted before the primitive sync, plus `matcha-2` (sage — utilities /
 * secondary) and `stone-1` (uncategorized residual). Anything unrecognized falls back
 * to the first palette entry — never to a warning-adjacent hue.
 */
@Composable
fun categoryColor(colorToken: String): Color {
    val extended = sumiExtendedColors()
    return when (colorToken) {
        "matcha-2" -> MaterialTheme.colorScheme.secondary
        "stone-1" -> extended.stone
        else -> extended.categories.getOrElse(categoryColorIndex(colorToken)) { extended.categories.first() }
    }
}

/**
 * Resolves the uncategorized residual; grey only, by design (AGENTS.md, DESIGN.md §3.1).
 */
@Composable
fun categoryColorOrNeutral(colorToken: String): Color =
    if (colorToken == "categoryNeutral" || colorToken == "neutral" || colorToken == "stone-1") {
        sumiExtendedColors().stone
    } else {
        categoryColor(colorToken)
    }

@Composable
fun SumiTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    androidx.compose.runtime.CompositionLocalProvider(
        LocalSumiExtendedColors provides if (darkTheme) DarkExtended else LightExtended,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = SumiTypography,
            shapes = SumiShapes,
            content = content,
        )
    }
}
