package com.sumi.data.categories

import com.sumi.domain.categories.DefaultPackageCategories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Existing installs must receive new seed rows on the next bootstrap without
 * resurrecting anything the user deleted. Only the post-2026-11 ids are backfilled.
 */
class SeedBackfillTest {

    @Test
    fun `backfill adds exactly the new seed ids when nothing exists`() {
        val added = DefaultCategories.backfillSeeds(emptySet()).map { it.id }.toSet()
        assertEquals(
            setOf(
                DefaultPackageCategories.AI,
                DefaultPackageCategories.FINANCE,
                DefaultPackageCategories.BROWSER,
            ),
            added,
        )
    }

    @Test
    fun `backfill never re-adds a legacy seed the user deleted`() {
        // The user deleted games and utilities: they must not reappear.
        val existing = DefaultPackageCategories.DEFAULT_IDS
            .filterNot { it == DefaultPackageCategories.GAMES }
            .filterNot { it == DefaultPackageCategories.UTILITIES }
            .filterNot { it in DefaultCategories.BACKFILL_SEED_IDS }
            .toSet()
        val added = DefaultCategories.backfillSeeds(existing).map { it.id }.toSet()
        assertEquals(
            setOf(
                DefaultPackageCategories.AI,
                DefaultPackageCategories.FINANCE,
                DefaultPackageCategories.BROWSER,
            ),
            added,
        )
    }

    @Test
    fun `backfill skips new seeds that already exist`() {
        val existing = setOf(DefaultPackageCategories.AI)
        val added = DefaultCategories.backfillSeeds(existing).map { it.id }.toSet()
        assertEquals(
            setOf(DefaultPackageCategories.FINANCE, DefaultPackageCategories.BROWSER),
            added,
        )
    }

    @Test
    fun `a user-created category sharing a new id is not duplicated`() {
        val existing = setOf(
            DefaultPackageCategories.AI,
            DefaultPackageCategories.FINANCE,
            DefaultPackageCategories.BROWSER,
        )
        assertTrue(DefaultCategories.backfillSeeds(existing).isEmpty())
    }
}
