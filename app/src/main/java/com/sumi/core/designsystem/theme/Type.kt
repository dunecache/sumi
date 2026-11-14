package com.sumi.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.sumi.R

/**
 * Typography roles per DESIGN.md §3.2, bundled locally (no downloadable fonts, no network).
 * Inter (interface), Literata (reflection), IBM Plex Mono (numeric). Variable-font TTFs from
 * google/fonts (OFL); licensed headers attributed in res/font/README.md.
 */
object SumiFontFamilies {
    val Interface = FontFamily(Font(R.font.inter_regular, FontWeight.Normal))
    val Reflection = FontFamily(Font(R.font.literata_regular, FontWeight.Normal))
    val Numeric = FontFamily(
        Font(R.font.plex_mono_regular, FontWeight.Normal),
        Font(R.font.plex_mono_medium, FontWeight.Medium),
    )
}

val SumiTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = SumiFontFamilies.Reflection,
        fontWeight = FontWeight.Normal,
        fontSize = 34.sp,
        lineHeight = 42.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = SumiFontFamilies.Reflection,
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = SumiFontFamilies.Interface,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = SumiFontFamilies.Interface,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 26.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = SumiFontFamilies.Interface,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = SumiFontFamilies.Interface,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = SumiFontFamilies.Interface,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = SumiFontFamilies.Interface,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = SumiFontFamilies.Interface,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.4.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = SumiFontFamilies.Interface,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = SumiFontFamilies.Interface,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = SumiFontFamilies.Interface,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
)

/**
 * Serif wordmark for root destinations (reference standard §9.3): the product voice
 * spoken once per root destination, 22 sp. Never used on subscreens.
 */
val SumiWordmark = TextStyle(
    fontFamily = SumiFontFamilies.Reflection,
    fontWeight = FontWeight.Normal,
    fontSize = 22.sp,
    lineHeight = 28.sp,
)

/**
 * Reflection role (Literata): observations and gentle suggestions at body size
 * (DESIGN.md §3.2). Italic only for hedged interpretations (§7).
 */
val ReflectionBody = TextStyle(
    fontFamily = SumiFontFamilies.Reflection,
    fontWeight = FontWeight.Normal,
    fontSize = 16.sp,
    lineHeight = 24.sp,
)

/**
 * Numeric styles for durations and big numbers (IBM Plex Mono role). Tabular figures keep
 * multi-row durations column-aligned and stable while values refresh.
 */
val NumericLarge = TextStyle(
    fontFamily = SumiFontFamilies.Numeric,
    fontWeight = FontWeight.Medium,
    fontSize = 30.sp,
    lineHeight = 38.sp,
    fontFeatureSettings = "tnum",
)

val NumericBody = TextStyle(
    fontFamily = SumiFontFamilies.Numeric,
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    fontFeatureSettings = "tnum",
)
