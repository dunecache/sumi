package com.sumi.feature.apps

import com.sumi.core.database.entity.AppCategory
import com.sumi.core.database.entity.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppsLogicTest {

    private val chips = listOf(
        CategoryFilterChip("social", "Social", "matcha-4", sortOrder = 0),
        CategoryFilterChip("education", "Education", "wisteria", sortOrder = 4),
    )

    private fun row(
        pkg: String,
        name: String,
        category: String?,
        today: Long,
        week: Long = today,
        categoryChip: CategoryFilterChip? = chips.firstOrNull { it.id == category },
        isNew: Boolean = false,
        isSystemApp: Boolean = false,
    ) = AppRow(
        packageName = pkg,
        displayName = name,
        categoryId = category,
        categoryName = categoryChip?.name,
        categoryColorToken = categoryChip?.colorToken,
        todayMs = today,
        weekMs = week,
        isNew = isNew,
        isSystemApp = isSystemApp,
    )

    private val rows = listOf(
        row("com.instagram.android", "Instagram", "social", 3_600_000L),
        row("com.duolingo", "Duolingo", "education", 1_800_000L),
        row("com.unknown.one", "Unknown One", null, 5_000_000L),
        row("com.other.two", "Other Two", "social", 0L),
    )

    // --- chips built from live categories (incl. large user-created sets) ---

    @Test
    fun `chips come from live categories in manager order`() {
        val categories = listOf(
            AppCategory("education", "Education", "wisteria", sortOrder = 4),
            AppCategory("social", "Social", "matcha-4", sortOrder = 0),
        )
        // Manager order is preserved by the DAO; chips mirror it verbatim.
        val chips = categoryChips(categories)
        assertEquals(listOf("Education", "Social"), chips.map { it.name })
        assertEquals(listOf(4, 0), chips.map { it.sortOrder })
    }

    @Test
    fun `chip list carries every user-created category, no truncation`() {
        val seeded = DefaultCategoriesLike.seedIds
        val userCreated = (1..20).map { index ->
            AppCategory("custom-$index", "Custom $index", "mist", sortOrder = 100 + index, isUserCreated = true)
        }
        val chips = categoryChips(seeded.mapIndexed { index, id ->
            AppCategory(id, "Seed $id", "matcha-4", sortOrder = index)
        } + userCreated)
        assertEquals(seeded.size + userCreated.size, chips.size)
        assertEquals(
            userCreated.map { it.id },
            chips.filter { it.id.startsWith("custom-") }.map { it.id },
        )
    }

    @Test
    fun `all filter keeps everything`() {
        assertEquals(4, filterAndSort(rows, "", chips, AppsFilter.All, AppsSort.MOST_USED_TODAY).size)
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

    // --- search: labels, packages, category names, the residual bucket ---

    @Test
    fun `query matches names and packages case-insensitively`() {
        assertEquals(
            listOf("Instagram"),
            filterAndSort(rows, "INSTAGRAM", chips, AppsFilter.All, AppsSort.MOST_USED_TODAY).map { it.displayName },
        )
        assertEquals(
            listOf("Instagram"),
            filterAndSort(rows, "instagram.android", chips, AppsFilter.All, AppsSort.MOST_USED_TODAY).map { it.displayName },
        )
    }

    @Test
    fun `query matches the category name, not only the app`() {
        val result = filterAndSort(rows, "social", chips, AppsFilter.All, AppsSort.ALPHABETICAL)
        assertEquals(listOf("Instagram", "Other Two"), result.map { it.displayName })
    }

    @Test
    fun `query matches uncategorized apps when typing uncategorized`() {
        val result = filterAndSort(rows, "uncat", chips, AppsFilter.All, AppsSort.ALPHABETICAL)
        assertEquals(listOf("Unknown One"), result.map { it.displayName })
    }

    @Test
    fun `category query does not leak apps from other categories`() {
        val result = filterAndSort(rows, "education", chips, AppsFilter.All, AppsSort.ALPHABETICAL)
        assertEquals(listOf("Duolingo"), result.map { it.displayName })
    }

    // --- sorting ---

    @Test
    fun `most-used-today sort ranks by today's usage with name tiebreak`() {
        val result = filterAndSort(rows, "", chips, AppsFilter.All, AppsSort.MOST_USED_TODAY)
        assertEquals(listOf("Unknown One", "Instagram", "Duolingo", "Other Two"), result.map { it.displayName })
    }

    @Test
    fun `most-used-week sort ranks by the week's usage with name tiebreak`() {
        val weekRows = listOf(
            row("com.a", "Alpha", "social", today = 10_000L, week = 500_000L),
            row("com.b", "Beta", "social", today = 900_000L, week = 100_000L),
        )
        val result = filterAndSort(weekRows, "", chips, AppsFilter.All, AppsSort.MOST_USED_WEEK)
        assertEquals(listOf("Alpha", "Beta"), result.map { it.displayName })
    }

    @Test
    fun `usage sorts tie-break by name, deterministically`() {
        val tied = listOf(
            row("com.b", "Beta", "social", today = 100_000L),
            row("com.a", "Alpha", "social", today = 100_000L),
        )
        val result = filterAndSort(tied, "", chips, AppsFilter.All, AppsSort.MOST_USED_TODAY)
        assertEquals(listOf("Alpha", "Beta"), result.map { it.displayName })
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
        val result = filterAndSort(rows, "instagram", chips, AppsFilter.Category("social"), AppsSort.MOST_USED_TODAY)
        assertEquals(listOf("Instagram"), result.map { it.displayName })
        val empty = filterAndSort(rows, "instagram", chips, AppsFilter.Category("education"), AppsSort.MOST_USED_TODAY)
        assertTrue(empty.isEmpty())
    }

    // --- zero-usage divider partitioning ---

    @Test
    fun `usage sorts partition zero-usage apps below the divider`() {
        val mixed = listOf(
            row("com.zero.a", "Zero A", null, today = 0L, week = 0L),
            row("com.used.b", "Used B", null, today = 1_000L, week = 2_000L),
            row("com.zero.b", "Zero B", null, today = 0L, week = 0L),
        )
        val (used, unused) = splitUsageRows(
            filterAndSort(mixed, "", chips, AppsFilter.All, AppsSort.MOST_USED_TODAY),
            AppsSort.MOST_USED_TODAY,
        )
        assertEquals(listOf("Used B"), used.map { it.displayName })
        assertEquals(listOf("Zero A", "Zero B"), unused.map { it.displayName })
    }

    @Test
    fun `week sort partitions on the week window`() {
        val mixed = listOf(
            row("com.a", "Alpha", null, today = 0L, week = 0L),
            row("com.b", "Beta", null, today = 0L, week = 5_000L),
        )
        val (used, unused) = splitUsageRows(
            filterAndSort(mixed, "", chips, AppsFilter.All, AppsSort.MOST_USED_WEEK),
            AppsSort.MOST_USED_WEEK,
        )
        assertEquals(listOf("Beta"), used.map { it.displayName })
        assertEquals(listOf("Alpha"), unused.map { it.displayName })
    }

    @Test
    fun `alphabetical and category sorts produce no divider`() {
        val mixed = listOf(
            row("com.a", "Alpha", null, today = 0L),
            row("com.b", "Beta", null, today = 5_000L),
        )
        assertEquals(emptyList<AppRow>(), splitUsageRows(mixed, AppsSort.ALPHABETICAL).second)
        assertEquals(emptyList<AppRow>(), splitUsageRows(mixed, AppsSort.CATEGORY).second)
    }

    // --- system-app policy ---

    @Test
    fun `system apps are hidden unless the policy shows them`() {
        val withSystem = rows + row("com.android.settings", "Settings", null, 9_000_000L, isSystemApp = true)
        val hidden = filterAndSort(withSystem, "", chips, AppsFilter.All, AppsSort.MOST_USED_TODAY, includeSystemApps = false)
        assertEquals(listOf("Unknown One", "Instagram", "Duolingo", "Other Two"), hidden.map { it.displayName })
        val shown = filterAndSort(withSystem, "", chips, AppsFilter.All, AppsSort.MOST_USED_TODAY, includeSystemApps = true)
        assertEquals("Settings", shown.first().displayName)
    }

    // --- deep links & selection ---

    @Test
    fun `deep-link token uncategorized pins the residual`() {
        assertEquals(AppsFilter.Uncategorized, appsFilterToken("uncategorized"))
    }

    @Test
    fun `deep-link token null or all is the unfiltered library`() {
        assertEquals(AppsFilter.All, appsFilterToken(null))
        assertEquals(AppsFilter.All, appsFilterToken(""))
        assertEquals(AppsFilter.All, appsFilterToken("all"))
    }

    @Test
    fun `deep-link token is a category id verbatim`() {
        assertEquals(AppsFilter.Category("social"), appsFilterToken("social"))
    }

    @Test
    fun `selection toggles a package in and out`() {
        assertEquals(setOf("com.a"), toggleSelectionSet(emptySet(), "com.a"))
        assertEquals(emptySet<String>(), toggleSelectionSet(setOf("com.a"), "com.a"))
        assertEquals(
            setOf("com.a", "com.b"),
            toggleSelectionSet(setOf("com.a"), "com.b"),
        )
    }

    // --- row building ---

    @Test
    fun `appRows resolves names and tokens from the current assignment`() {
        val applications = listOf(
            Application("com.a", "A", categoryId = "social"),
            Application("com.b", "B", categoryId = null),
        )
        val rows = appRows(applications, emptyMap(), emptyMap(), chips.associateBy { it.id }, nowUtcMs = 0L)
        assertEquals("Social", rows[0].categoryName)
        assertEquals("matcha-4", rows[0].categoryColorToken)
        assertEquals(null, rows[1].categoryName)
        assertEquals(null, rows[1].categoryColorToken)
    }

    @Test
    fun `appRows flags only recently-seen rows as new`() {
        val now = 2_000_000_000_000L
        val fourteenDaysMs = NEW_WINDOW_DAYS * 24L * 60L * 60L * 1000L
        val applications = listOf(
            Application("com.new", "Fresh", firstSeenAt = now - 1000L),
            Application("com.old", "Stale", firstSeenAt = now - fourteenDaysMs - 1L),
            Application("com.none", "Legacy"), // firstSeenAt defaults to 0 = unknown
        )
        val rows = appRows(applications, emptyMap(), emptyMap(), emptyMap(), nowUtcMs = now)
        assertEquals(listOf(true, false, false), rows.map { it.isNew })
    }
}

// CategoryFilterChip has no isUserCreated flag; the large-set test filters by id prefix instead.
private object DefaultCategoriesLike {
    val seedIds = listOf(
        "social",
        "communication",
        "entertainment",
        "productivity",
        "education",
        "reading",
        "games",
        "utilities",
        "ai",
        "finance",
        "browser",
    )
}
