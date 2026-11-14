package com.sumi.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Shape tokens per the 2026-11-14 reference standard (DESIGN.md §14.4):
// r-sm 12 (inputs, progress track) · r-md 16 (module cards, menus) · r-lg 28 (sheets, dialogs) · r-full pills.
val SumiShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(28.dp),
)

object SumiSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val section = 48.dp
    val screenHorizontal = 24.dp // canonical 24 dp screen margin (reference standard §8.3)
    val minTouchTarget = 48.dp
    val rowMinHeight = 56.dp // tappable list/radio rows (reference standard §8.3)
    val maxVoid = 240.dp // max continuous void on narrative screens (~30% of 909 dp screen)
}
