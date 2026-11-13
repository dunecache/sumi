package com.sumi.data.categories

import com.sumi.core.database.entity.AppCategory
import com.sumi.domain.categories.DefaultPackageCategories
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The 2026-11 seeds shipped positional tokens; fresh installs seed primitive names.
 * Rows that still carry the original positional token are recolored once at bootstrap;
 * anything the user touched is left exactly as it is.
 */
class SeedColorSyncTest {

    private fun row(
        id: String,
        token: String,
        sortOrder: Int,
        isUserCreated: Boolean = false,
    ) = AppCategory(id, id, token, sortOrder, isUserCreated)

    @Test
    fun `seeded rows still on their original positional token are recolored`() {
        val stored = listOf(
            row(DefaultPackageCategories.SOCIAL, "categoryColor1", 0),
            row(DefaultPackageCategories.PRODUCTIVITY, "categoryColor4", 3),
            row(DefaultPackageCategories.UTILITIES, "categoryNeutral", 7),
        )
        val updated = DefaultCategories.recolorUntouchedDefaults(stored).associateBy { it.id }
        assertEquals("matcha-4", updated[DefaultPackageCategories.SOCIAL]?.colorToken)
        assertEquals("mist", updated[DefaultPackageCategories.PRODUCTIVITY]?.colorToken)
        assertEquals("matcha-2", updated[DefaultPackageCategories.UTILITIES]?.colorToken)
    }

    @Test
    fun `user recolors are never overwritten`() {
        val stored = listOf(
            row(DefaultPackageCategories.SOCIAL, "ochre", 0),
            row(DefaultPackageCategories.GAMES, "clay-2", 6),
        )
        assertEquals(emptyList<AppCategory>(), DefaultCategories.recolorUntouchedDefaults(stored))
    }

    @Test
    fun `seeded rows already on primitive tokens are left alone`() {
        val stored = listOf(row(DefaultPackageCategories.SOCIAL, "matcha-4", 0))
        assertEquals(emptyList<AppCategory>(), DefaultCategories.recolorUntouchedDefaults(stored))
    }

    @Test
    fun `a user-created category sharing a seeded id is never recolored`() {
        val stored = listOf(row(DefaultPackageCategories.SOCIAL, "categoryColor1", 42, isUserCreated = true))
        assertEquals(emptyList<AppCategory>(), DefaultCategories.recolorUntouchedDefaults(stored))
    }

    @Test
    fun `recolor preserves the user's sort order`() {
        val stored = listOf(row(DefaultPackageCategories.READING, "categoryColor6", 11))
        val updated = DefaultCategories.recolorUntouchedDefaults(stored).single()
        assertEquals("method", updated.colorToken)
        assertEquals(11, updated.sortOrder)
    }
}
