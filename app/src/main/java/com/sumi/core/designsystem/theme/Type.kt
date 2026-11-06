package com.sumi.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Typography roles per DESIGN.md §3.2.
 * - Interface: Inter (body/headline) — maps to [FontFamily.Default] until bundled Inter TTFs are dropped in (see res/font/README.md).
 * - Reflection/editorial: Literata — maps to [FontFamily.Serif] fallback.
 * - Numeric/durations: IBM Plex Mono — maps to [FontFamily.Monospace] fallback with tabular numerals.
 *
 * Once fonts are bundled, replace these families with Font(R.font.inter_*) etc. No hardcoded type sizes in features.
 */
object SumiFontFamilies {
    val Interface = FontFamily.Default
    val Reflection = FontFamily.Serif
    val Numeric = FontFamily.Monospace
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
