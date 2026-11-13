package com.sumi.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * Canonical Kanso Matcha v1.2 color tokens (semantic layer, both modes), sourced from
 * the design-system repository (`tokens/primitives/<mode>.json`, `tokens/semantic/<mode>.json`,
 * `tokens/charts.json`). Values are WCAG-validated by that repository's contrast checker
 * (v1.1/v1.2 amendments applied); do not tweak hexes here — amend the canonical spec
 * first, then sync.
 *
 * Always reference tokens by name, never by hex, in feature code. The new usage-UI
 * tokens (`Stone1*`, `Matcha1*`, `Terracotta*`) are named after their primitives; the
 * category palette keeps positional names for persisted tokens — `categoryColor{n}` —
 * while the primitive name lives in a comment beside each entry.
 */
object SumiColorTokens {
    // ---- Morning Matcha (light): warm paper surfaces, calm editorial light ----
    // Named primitives (tokens/primitives/morning-matcha.json): `background` = paper-1,
    // `surface` = paper-2, `surface-elevated` = paper-0, `surface-sunken` = paper-3.
    val BackgroundLight = Color(0xFFF3F1E9) // base background (paper)
    val SurfaceLight = Color(0xFFE8E5DA) // workspace / panel surface
    val SurfaceElevatedLight = Color(0xFFFAF9F4) // elevated surfaces (cards, modals)
    val SurfaceSunkenLight = Color(0xFFE2DED2) // sunken surfaces (code backgrounds)
    val SurfaceAccentLight = Color(0xFFDCE2D1) // soft highlight / selected surfaces (matcha-1)
    val OnBackgroundLight = Color(0xFF34382F) // primary text (stone-3)
    val OnSurfaceMutedLight = Color(0xFF616359) // secondary / muted text (stone-2)
    val BorderSubtleLight = Color(0xFFD5D0C2) // hairlines / separators (decorative, paper-3)
    val BorderStrongLight = Color(0xFF827E74) // essential boundaries (inputs, active edges)
    val AccentPrimaryLight = Color(0xFF89977A) // matcha accent — decorative fills only (matcha-3)
    val AccentStrongLight = Color(0xFF5C664A) // accessible accent (text, checkboxes, active tab)
    val AccentSecondaryLight = Color(0xFFAAB49A) // sage, secondary accent (matcha-2)
    val SelectionLight = Color(0xFFDCE2D1)
    val FocusLight = Color(0xFF626D4F) // matcha-4
    val SuccessLight = Color(0xFF5C664A)
    val WarningLight = Color(0xFF795E2C)
    val DangerLight = Color(0xFF944E47)
    val InfoLight = Color(0xFF48676B)
    val ClayLight = Color(0xFFB68E78) // tertiary accent (warm clay, clay-1)

    // Named primitives used by the usage UI (synced above):
    val Stone1Light = Color(0xFF77796D) // neutral grey — uncategorized residual bucket
    val Matcha1Light = Color(0xFFDCE2D1) // quiet track fill for the daily intention ring
    val TerracottaLight = Color(0xFF9A514A) // warning hue — ONLY for the over-goal state

    // Diff domain (validated pairs from tokens/diff.json; reused for error containers).
    val DiffRemovedFgLight = Color(0xFF914B45)
    val DiffRemovedBgLight = Color(0xFFEAD8D2)

    // ---- Evening Zen (dark): deep warm charcoal, quiet botanical accents ----
    // Named primitives (tokens/primitives/evening-zen.json): `background` = ink-0,
    // `surface` = ink-1, `surface-elevated` = ink-2, `surface-sunken` = ink-3.
    val BackgroundDark = Color(0xFF22251F)
    val SurfaceDark = Color(0xFF2D3129)
    val SurfaceElevatedDark = Color(0xFF363A31)
    val SurfaceSunkenDark = Color(0xFF1C1F1A)
    val SurfaceAccentDark = Color(0xFF41483A) // matcha-1
    val OnBackgroundDark = Color(0xFFE8E5D9) // stone-3
    val OnSurfaceMutedDark = Color(0xFFB5B5A9) // stone-2
    val BorderSubtleDark = Color(0xFF41463B) // ink-3
    val BorderStrongDark = Color(0xFF83877F)
    val AccentPrimaryDark = Color(0xFFA3AE91) // matcha-3
    val AccentStrongDark = Color(0xFFC1CCAC) // matcha-4
    val AccentSecondaryDark = Color(0xFF858F76) // matcha-2
    val SelectionDark = Color(0xFF41483A)
    val FocusDark = Color(0xFFA3AE91)
    val SuccessDark = Color(0xFFC1CCAC)
    val WarningDark = Color(0xFFD2B477)
    val DangerDark = Color(0xFFE1A59C) // bright-terracotta text convention
    val InfoDark = Color(0xFF9CB8BA)
    val ClayDark = Color(0xFFB58E79) // clay-1

    val DiffRemovedFgDark = Color(0xFFE1A59C)
    val DiffRemovedBgDark = Color(0xFF49332F)

    // Named primitives used by the usage UI (synced above):
    val Stone1Dark = Color(0xFFAAA99B) // neutral grey — uncategorized residual bucket
    val Matcha1Dark = Color(0xFF41483A) // quiet track fill for the daily intention ring
    val TerracottaDark = Color(0xFFD28B80) // warning hue — ONLY for the over-goal state

    // Category/chart palettes (canonical charts.json, 7 muted hues per mode).
    // Never relied on alone — always paired with a label (DESIGN.md §3.1).
    // Positions stay stable for persisted `categoryColor{n}` tokens; the *names* in the
    // comments are the primitives each position maps to (Step 2 aliases them in code):
    // [0] matcha-4 · [1] mist · [2] clay-2 · [3] wisteria · [4] ochre · [5] method · [6] constant.
    // `constant` evening == `terracotta` evening (#D28B80): it is warning-adjacent, so it
    // is NOT assigned to any category (see DESIGN.md §3.1).
    val CategoryColorsLight = listOf(
        Color(0xFF626D4F), // matcha-4
        Color(0xFF4F7075), // mist
        Color(0xFF8A6654), // clay-2
        Color(0xFF79658B), // wisteria
        Color(0xFF80642F), // ochre
        Color(0xFF55756A), // method
        Color(0xFF925F50), // constant (reserved, unassigned)
    )
    val CategoryColorsDark = listOf(
        Color(0xFFA3AE91), // matcha-4
        Color(0xFF9CB8BA), // mist
        Color(0xFFD0A78F), // clay-2
        Color(0xFFB5A4C8), // wisteria
        Color(0xFFD2B477), // ochre
        Color(0xFF9BBBAA), // method
        Color(0xFFD28B80), // constant (reserved, unassigned)
    )
}

/** Index into the canonical category palette for a `categoryColor{n}` token name. */
fun categoryColorIndex(colorToken: String): Int = when (colorToken) {
    "categoryColor1" -> 0
    "categoryColor2" -> 1
    "categoryColor3" -> 2
    "categoryColor4" -> 3
    "categoryColor5" -> 4
    "categoryColor6" -> 5
    else -> 6
}
