package com.sumi.feature.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppsLogicTest {

    private val chips = listOf(
        CategoryFilterChip("social", "Social", "categoryColor1", sortOrder = 0),
        CategoryFilterChip("education", "Education", "categoryColor5", sortOrder = 4),
    )

    private fun row(pkg: String, name: String, category: String?, today: Long) =
        AppRow(packageName = pkg, displayName = name, categoryId = category, todayMs = today, weekMs = today)

    private val rows = listOf(
        row("com.instagram.android", "Instagram", "social", 3_600_000L),
        row("com.duolingo", "Duolingo", "education", 1_800_000L),
        row("com.unknown.one", "Unknown One", null, 5_000_000L),
        row("com.other.two", "Other Two", "social", 0L),
    )

    @Test
    fun `all filter keeps everything`() {
        assertEquals(4, filterAndSort(rows, "", chips, AppsFilter.All, AppsSort.MOST_USED).size)
    }

    @Test
    fun `category filter keeps only apps in that category`() {
        val result = filterAndSort(rows, "", chips, AppsFilter.Category("social"), AppsSort.ALPHABETICAL)
        assertEquals(listOf("Instagram", "Other Two"), result.map { it.displayName })
    }

    @Test
    fun `uncategorized filter keeps only apps with no assignment`() {
        val result = filterAndSort(rows, "", chips, AppsFilter.Uncategorized, AppsSort.ALPHABETICAL)
        assertEquals(listOf("Unknown One"), result.map { it.displayName })
    }

    @Test
    fun `query matches names and packages case-insensitively`() {
        assertEquals(
            listOf("Instagram"),
            filterAndSort(rows, "INSTAGRAM", chips, AppsFilter.All, AppsSort.MOST_USED).map { it.displayName },
        )
        assertEquals(
            listOf("Instagram"),
            filterAndSort(rows, "instagram.android", chips, AppsFilter.All, AppsSort.MOST_USED).map { it.displayName },
        )
    }

    @Test
    fun `most-used sort ranks by today's usage with name tiebreak`() {
        val result = filterAndSort(rows, "", chips, AppsFilter.All, AppsSort.MOST_USED)
        assertEquals(listOf("Unknown One", "Instagram", "Duolingo", "Other Two"), result.map { it.displayName })
    }

    @Test
    fun `least-used sort ranks zero-usage first`() {
        val result = filterAndSort(rows, "", chips, AppsFilter.All, AppsSort.LEAST_USED)
        assertEquals("Other Two", result.first().displayName)
    }

    @Test
    fun `alphabetical sort ignores usage`() {
        val result = filterAndSort(rows, "", chips, AppsFilter.All, AppsSort.ALPHABETICAL)
        assertEquals(
            listOf("Duolingo", "Instagram", "Other Two", "Unknown One"),
            result.map { it.displayName },
        )
    }

    @Test
    fun `category sort follows the manager's order and puts uncategorized last`() {
        val result = filterAndSort(rows, "", chips, AppsFilter.All, AppsSort.CATEGORY)
        // social (0) → education (4) → uncategorized (last)
        assertEquals(
            listOf("Instagram", "Other Two", "Duolingo", "Unknown One"),
            result.map { it.displayName },
        )
    }

    @Test
    fun `query combined with a category filter narrows results`() {
        val result = filterAndSort(rows, "instagram", chips, AppsFilter.Category("social"), AppsSort.MOST_USED)
        assertEquals(listOf("Instagram"), result.map { it.displayName })
        val empty = filterAndSort(rows, "instagram", chips, AppsFilter.Category("education"), AppsSort.MOST_USED)
        assertTrue(empty.isEmpty())
    }
}
