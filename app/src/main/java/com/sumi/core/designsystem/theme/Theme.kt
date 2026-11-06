package com.sumi.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Kanso Matcha themes. No alarm red for ordinary usage; no default Material purple, no dynamic color.
// See DESIGN.md §3.1.

private val LightScheme = lightColorScheme(
    background = SumiColorTokens.BackgroundLight,
    surface = SumiColorTokens.SurfaceLight,
    surfaceVariant = SumiColorTokens.SurfaceVariantLight,
    onBackground = SumiColorTokens.OnBackgroundLight,
    onSurface = SumiColorTokens.OnSurfaceLight,
    onSurfaceVariant = SumiColorTokens.OnSurfaceMutedLight,
    primary = SumiColorTokens.AccentLight,
    onPrimary = SumiColorTokens.OnAccentLight,
    primaryContainer = SumiColorTokens.AccentContainerLight,
    onPrimaryContainer = SumiColorTokens.OnBackgroundLight,
    outline = SumiColorTokens.OutlineLight,
)

private val DarkScheme = darkColorScheme(
    background = SumiColorTokens.BackgroundDark,
    surface = SumiColorTokens.SurfaceDark,
    surfaceVariant = SumiColorTokens.SurfaceVariantDark,
    onBackground = SumiColorTokens.OnBackgroundDark,
    onSurface = SumiColorTokens.OnSurfaceDark,
    onSurfaceVariant = SumiColorTokens.OnSurfaceMutedDark,
    primary = SumiColorTokens.AccentDark,
    onPrimary = SumiColorTokens.OnAccentDark,
    primaryContainer = SumiColorTokens.AccentContainerDark,
    onPrimaryContainer = SumiColorTokens.OnBackgroundDark,
    outline = SumiColorTokens.OutlineDark,
)

/** Extra semantic colors exposed outside the Material scheme. */
data class SumiExtendedColors(
    val onSurfaceMuted: Color,
    val accent: Color,
    val onAccent: Color,
    val accentContainer: Color,
    val info: Color,
)

@Composable
fun sumiExtendedColors(darkTheme: Boolean = isSystemInDarkTheme()): SumiExtendedColors =
    if (darkTheme) {
        SumiExtendedColors(
            onSurfaceMuted = SumiColorTokens.OnSurfaceMutedDark,
            accent = SumiColorTokens.AccentDark,
            onAccent = SumiColorTokens.OnAccentDark,
            accentContainer = SumiColorTokens.AccentContainerDark,
            info = SumiColorTokens.InfoDark,
        )
    } else {
        SumiExtendedColors(
            onSurfaceMuted = SumiColorTokens.OnSurfaceMutedLight,
            accent = SumiColorTokens.AccentLight,
            onAccent = SumiColorTokens.OnAccentLight,
            accentContainer = SumiColorTokens.AccentContainerLight,
            info = SumiColorTokens.InfoLight,
        )
    }

@Composable
fun SumiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = SumiTypography,
        shapes = SumiShapes,
        content = content,
    )
}
