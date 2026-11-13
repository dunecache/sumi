package com.sumi.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * Canonical Kanso Matcha v1.3.0 color tokens (semantic layer, both modes), sourced from
 * the design-system repository (`tokens/primitives/<mode>.json`, `tokens/semantic/<mode>.json`,
 * `tokens/charts.json`). v1.3.0 ("Evening Zen revised dark palette") replaced every
 * Evening Zen primitive/semantic value; Morning Matcha is untouched. Values are
 * WCAG-validated by that repository's contrast checker (v1.1/v1.3 amendments applied);
 * do not tweak hexes here — amend the canonical spec first, then sync.
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

    // ---- Evening Zen (dark): deep forest, luminous sage, layered surfaces (v1.3.0) ----
    // Named primitives (tokens/primitives/evening-zen.json): `background` = ink-0,
    // `surface` = ink-1, `surface-elevated` = ink-2, `surface-sunken` = derived (#10160F).
    val BackgroundDark = Color(0xFF171F18)
    val SurfaceDark = Color(0xFF263326)
    val SurfaceElevatedDark = Color(0xFF364735)
    val SurfaceSunkenDark = Color(0xFF10160F)
    val SurfaceAccentDark = Color(0xFF3B4C35) // matcha-1
    val OnBackgroundDark = Color(0xFFE9EEE2) // stone-3
    val OnSurfaceMutedDark = Color(0xFFB2BBAB) // text-muted (derived from stone-1; fails <4.5:1 raw)
    val BorderSubtleDark = Color(0xFF3D4A3B) // border-subtle (derived; not ink-3)
    val BorderStrongDark = Color(0xFF829875) // matcha-2
    val AccentPrimaryDark = Color(0xFFB0C39C) // matcha-3
    val AccentStrongDark = Color(0xFFD2E4BB) // matcha-4
    val AccentSecondaryDark = Color(0xFF829875) // matcha-2
    val SelectionDark = Color(0xFF3B4C35) // matcha-1
    val FocusDark = Color(0xFFB0C39C) // matcha-3
    val SuccessDark = Color(0xFFD2E4BB) // matcha-4
    val WarningDark = Color(0xFFD9BD7E) // ochre
    val DangerDark = Color(0xFFE1ACA4) // bright-terracotta text convention
    val InfoDark = Color(0xFFA5C5C3) // mist
    val ClayDark = Color(0xFFC19A82) // clay-1

    val DiffRemovedFgDark = Color(0xFFE1ACA4)
    val DiffRemovedBgDark = Color(0xFF49332F)

    // Named primitives used by the usage UI (synced above):
    val Stone1Dark = Color(0xFFA6B09D) // neutral grey — uncategorized residual bucket
    val Matcha1Dark = Color(0xFF3B4C35) // quiet track fill for the daily intention ring
    val TerracottaDark = Color(0xFFD68D82) // warning hue — ONLY for the over-goal state

    // Category/chart palettes (canonical charts.json, 7 muted hues per mode).
    // Never relied on alone — always paired with a label (DESIGN.md §3.1).
    // Positions stay stable for persisted `categoryColor{n}` tokens; the *names* in the
    // comments are the primitives each position maps to (Step 2 aliases them in code).
    // Morning: [0] matcha-4 · [1] mist · [2] clay-2 · [3] wisteria · [4] ochre · [5] method · [6] constant.
    // Evening (v1.3.0): [0] matcha-3 · [1] mist · [2] clay-2 · [3] wisteria · [4] ochre · [5] matcha-2 · [6] terracotta.
    // Persisted tokens resolve by position; morning and evening slot values are synced.
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
        Color(0xFFB0C39C), // matcha-3
        Color(0xFFA5C5C3), // mist
        Color(0xFFD8B49B), // clay-2
        Color(0xFFC1B1D2), // wisteria
        Color(0xFFD9BD7E), // ochre
        Color(0xFF829875), // matcha-2
        Color(0xFFD68D82), // terracotta (reserved, warning hue)
    )
}

/**
 * Index into the canonical category palette for a token name.
 *
 * Accepts both the persisted positional names `categoryColor{n}` (unchanged for rows
 * written before the primitive sync) and the primitive names the canonical charts
 * carry (`matcha-4`, `mist`, …). Tokens that are not palette entries (neutral grey,
 * sage accent) resolve to 0; callers must check for them before using this — see
 * [com.sumi.core.designsystem.theme.categoryColorOrNeutral].
 */
fun categoryColorIndex(colorToken: String): Int = when (colorToken) {
    "categoryColor1", "matcha-4" -> 0
    "categoryColor2", "mist" -> 1
    "categoryColor3", "clay-2" -> 2
    "categoryColor4", "wisteria" -> 3
    "categoryColor5", "ochre" -> 4
    "categoryColor6", "method" -> 5
    "categoryColor7", "constant" -> 6
    else -> 0
}
