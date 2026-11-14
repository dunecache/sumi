package com.sumi.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * Canonical semantic tokens (Morning Matcha light / Evening Zen dark), sourced from
 * the design-system repository and reconciled with the 2026-11-14 "Design Reference
 * Standard v1.1" critique (see DESIGN.md §14). The dark palette values were measured
 * from the shipped build and are canonical; the light destructive red was re-tokened
 * to `red-600 #A63A2B` to clear the WCAG AA 4.5:1 floor (was 4.49:1). Morning Matcha
 * is otherwise unchanged. Values are WCAG-validated; do not tweak hexes here — amend
 * the canonical spec first, then sync.
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
    val BackgroundLight = Color(0xFFF4F1EA) // base background (paper, measured)
    val SurfaceLight = Color(0xFFE8E6DA) // bottom-nav strip / panel surface (measured)
    val SurfaceElevatedLight = Color(0xFFFAF9F4) // elevated surfaces (cards, modals)
    val SurfaceSunkenLight = Color(0xFFE2DED2) // sunken surfaces (code backgrounds)
    val SurfaceAccentLight = Color(0xFFDCE2D1) // soft highlight / selected surfaces (matcha-1)
    val OnBackgroundLight = Color(0xFF36382F) // primary text (stone-3, measured)
    val OnSurfaceMutedLight = Color(0xFF565145) // secondary / muted text (stone-2, darkened for 7:1)
    val BorderSubtleLight = Color(0xFFD5D0C2) // hairlines / separators (decorative, paper-3)
    val BorderStrongLight = Color(0xFF827E74) // essential boundaries (inputs, active edges)
    val AccentPrimaryLight = Color(0xFF89977A) // matcha accent — decorative fills only (matcha-3)
    val AccentStrongLight = Color(0xFF5C664B) // accessible accent (text, checkboxes, active tab, measured)
    val AccentSecondaryLight = Color(0xFFAAB49A) // sage, secondary accent (matcha-2)
    val SelectionLight = Color(0xFFDCE2D1) // nav active pill / selected container (matcha-1)
    val FocusLight = Color(0xFF626D4F) // matcha-4
    val SuccessLight = Color(0xFF5C664A)
    val WarningLight = Color(0xFF795E2C)
    val OnAccentLight = Color(0xFFF5F1E6) // text on olive and on destructive red fills
    val DangerLight = Color(0xFFA63A2B) // red-600 — destructive text and fills (5.7:1, F-05)
    val InfoLight = Color(0xFF48676B)
    val ClayLight = Color(0xFFB68E78) // tertiary accent (warm clay, clay-1)

    // Named primitives used by the usage UI (synced above):
    val Stone1Light = Color(0xFF77796D) // neutral grey — uncategorized residual bucket
    val Matcha1Light = Color(0xFFDCE2D1) // quiet track fill for the daily intention ring
    val TerracottaLight = Color(0xFF9A514A) // warning hue — ONLY for the over-goal state
    val DisabledFillLight = Color(0xFFDCD7CB) // disabled control fill
    val DisabledLabelLight = Color(0xFF6F695B) // disabled control text (3.8:1 vs disabled fill)

    // Diff domain (validated pairs from tokens/diff.json; reused for error containers).
    val DiffRemovedFgLight = Color(0xFF914B45)
    val DiffRemovedBgLight = Color(0xFFEAD8D2)

    // ---- Evening Zen (dark): green-tinted charcoal, luminous sage (canonical v1.1 measured) ----
    // Named primitives: `background` = ink-0, `surface` = ink-1, `surface-elevated` = ink-2,
    // `surface-sunken` = derived (#10160F). One elevated surface is shared by module cards
    // and the bottom navigation bar (`surface-2`).
    val BackgroundDark = Color(0xFF181F18) // ink-0 (measured)
    val SurfaceDark = Color(0xFF273327) // surface-2 — module cards AND bottom bar (measured)
    val SurfaceElevatedDark = Color(0xFF364735) // dialogs / sheets above surface-2
    val SurfaceSunkenDark = Color(0xFF10160F) // sunken surfaces (code backgrounds)
    val SurfaceAccentDark = Color(0xFF3B4C35) // matcha-1
    val OnBackgroundDark = Color(0xFFE8EEE3) // primary text (stone-3, measured)
    val OnSurfaceMutedDark = Color(0xFFB1BAAE) // secondary / muted text (measured; dark merges tertiary into this)
    val BorderSubtleDark = Color(0xFF3D4A3B) // border-subtle (derived; not ink-3)
    val BorderStrongDark = Color(0xFF829875) // matcha-2
    val AccentPrimaryDark = Color(0xFFB1C39D) // sage-green — category/decorative fills (measured)
    val AccentStrongDark = Color(0xFFD2E4BC) // olive-200 — accent fill: buttons, chips, progress, links (measured)
    val AccentSecondaryDark = Color(0xFF829874) // matcha-2 (mid-green, measured)
    val SelectionDark = Color(0xFF3B4C35) // matcha-1
    val Olive100Dark = Color(0xFFE5EEE2) // olive-100 — bottom-nav active indicator pill (measured)
    val ChartBarDark = Color(0xFFB0BBAD) // chart bars and analytic progress fills (measured)
    val OnAccentDark = Color(0xFF2B3824) // on-accent — labels on olive-200 (measured)
    val FocusDark = Color(0xFFB1C39D) // sage-green
    val SuccessDark = Color(0xFFD2E4BC) // olive-200
    val WarningDark = Color(0xFFD8BD7E) // ochre
    val DangerDark = Color(0xFFD1A9A3) // red-300 — destructive text and fills (measured)
    val InfoDark = Color(0xFFA5C5C2) // mist
    val ClayDark = Color(0xFFC19A82) // clay-1
    val ChartBarLight = Color(0xFF565145) // chart bars in light theme (mirrors ink-secondary)

    val DiffRemovedFgDark = Color(0xFFD1A9A3)
    val DiffRemovedBgDark = Color(0xFF49332F)

    // Named primitives used by the usage UI (synced above):
    val Stone1Dark = Color(0xFFA6B09D) // neutral grey — uncategorized residual bucket
    val Matcha1Dark = Color(0xFF3B4C35) // quiet track fill for the daily intention ring
    val TerracottaDark = Color(0xFFD68D82) // warning hue — ONLY for the over-goal state
    val DisabledFillDark = Color(0xFF2A2F2A) // disabled control fill
    val DisabledLabelDark = Color(0xFF79806F) // disabled control text (3.3:1 vs disabled fill)

    // Category/chart palettes (canonical charts.json, 7 muted hues per mode).
    // Never relied on alone — always paired with a label (DESIGN.md §3.1).
    // Positions stay stable for persisted `categoryColor{n}` tokens; the *names* in the
    // comments are the primitives each position maps to (Step 2 aliases them in code).
    // Morning: [0] matcha-4 · [1] mist · [2] clay-2 · [3] wisteria · [4] ochre · [5] method · [6] constant.
    // Evening (v1.1 measured): [0] sage-green · [1] mist · [2] tan · [3] lilac · [4] amber · [5] mid-green · [6] terracotta.
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
        Color(0xFFB1C39D), // sage-green — social (measured)
        Color(0xFFA5C5C2), // mist/cyan — education (measured)
        Color(0xFFD8B49A), // tan — browser, communication (measured)
        Color(0xFFC1B2D3), // lilac — AI (measured)
        Color(0xFFD8BD7E), // amber — games, entertainment (measured)
        Color(0xFF829874), // mid-green — utilities, reading (measured)
        Color(0xFFD68D82), // terracotta (reserved, never assigned; warning hue)
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
