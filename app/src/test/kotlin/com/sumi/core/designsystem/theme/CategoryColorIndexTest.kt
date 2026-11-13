package com.sumi.core.designsystem.theme

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Positional `categoryColor{n}` tokens are persisted in Room; the primitive aliases are
 * what fresh installs and the picker now use. Both must resolve to the same palette
 * positions, and an unknown token must never fall back to the warning-adjacent slot.
 */
class CategoryColorIndexTest {

    @Test
    fun `positional tokens resolve to their palette positions`() {
        assertEquals(0, categoryColorIndex("categoryColor1"))
        assertEquals(1, categoryColorIndex("categoryColor2"))
        assertEquals(3, categoryColorIndex("categoryColor4"))
        assertEquals(6, categoryColorIndex("categoryColor7"))
    }

    @Test
    fun `primitive aliases resolve to the same palette positions`() {
        assertEquals(0, categoryColorIndex("matcha-4"))
        assertEquals(1, categoryColorIndex("mist"))
        assertEquals(2, categoryColorIndex("clay-2"))
        assertEquals(3, categoryColorIndex("wisteria"))
        assertEquals(4, categoryColorIndex("ochre"))
        assertEquals(5, categoryColorIndex("method"))
        // `constant` remains resolvable for legacy rows; it is never offered in the picker.
        assertEquals(6, categoryColorIndex("constant"))
    }

    @Test
    fun `unknown tokens fall back to the first palette entry, never to the warning slot`() {
        assertEquals(0, categoryColorIndex("not-a-token"))
        assertEquals(0, categoryColorIndex(""))
    }
}
