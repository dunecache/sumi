package com.sumi.core.designsystem.theme

import androidx.compose.ui.graphics.Color

// Provisional Kanso Matcha token placeholders (see DESIGN.md §3.1).
// Replace with canonical v1.0 token spec when wiring core/designsystem.
// Always reference tokens by name, never by hex, in feature code.

object SumiColorTokens {
    // Light
    val BackgroundLight = Color(0xFFF6F2E8) // warm ivory
    val SurfaceLight = Color(0xFFFBF8F0)
    val SurfaceVariantLight = Color(0xFFECE6D6)
    val OnBackgroundLight = Color(0xFF2A2B25)
    val OnSurfaceLight = Color(0xFF2A2B25)
    val OnSurfaceMutedLight = Color(0xFF6B6C5F)
    val AccentLight = Color(0xFF7A8F55) // muted matcha
    val OnAccentLight = Color(0xFFFFFFFF)
    val AccentContainerLight = Color(0xFFDCE3C8)
    val OutlineLight = Color(0xFFD9D2BE)
    val InfoLight = Color(0xFF7C8A8C)

    // Dark
    val BackgroundDark = Color(0xFF1B1D18) // deep moss-charcoal
    val SurfaceDark = Color(0xFF23261F)
    val SurfaceVariantDark = Color(0xFF2C3027)
    val OnBackgroundDark = Color(0xFFE9E5D8)
    val OnSurfaceDark = Color(0xFFE9E5D8)
    val OnSurfaceMutedDark = Color(0xFFA3A596)
    val AccentDark = Color(0xFFA3B97B)
    val OnAccentDark = Color(0xFF1B1D18)
    val AccentContainerDark = Color(0xFF38422B)
    val OutlineDark = Color(0xFF3D4136)
    val InfoDark = Color(0xFF9AA8AA)

    // Category colors: muted, harmonious (earth, moss, clay, stone, sky-gray, bamboo).
    // Must be distinguishable in both themes and never relied upon alone (pair with label).
    val Category1 = Color(0xFF7A8F55)
    val Category2 = Color(0xFF9C8A6E)
    val Category3 = Color(0xFFB07A5A)
    val Category4 = Color(0xFF8A8D80)
    val Category5 = Color(0xFF8FA3A8)
    val Category6 = Color(0xFF6E8B67)
}
