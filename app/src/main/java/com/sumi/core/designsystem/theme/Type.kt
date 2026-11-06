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
        fontSize = 32.sp,
        lineHeight = 40.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = SumiFontFamilies.Interface,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
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
        fontSize = 16.sp,
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
    ),
    labelMedium = TextStyle(
        fontFamily = SumiFontFamilies.Interface,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)

/** Numeric style for durations and big numbers (IBM Plex Mono role). */
val NumericLarge = TextStyle(
    fontFamily = SumiFontFamilies.Numeric,
    fontWeight = FontWeight.Medium,
    fontSize = 36.sp,
    lineHeight = 44.sp,
)

val NumericBody = TextStyle(
    fontFamily = SumiFontFamilies.Numeric,
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    lineHeight = 20.sp,
)
