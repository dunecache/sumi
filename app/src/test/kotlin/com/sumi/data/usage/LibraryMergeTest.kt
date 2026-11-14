package com.sumi.data.usage

import com.sumi.core.database.entity.Application
import com.sumi.platform.applications.InstalledApp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Refresh semantics for the installed-app library. The refresh must never clobber user
 * assignments or user-created categories, removals keep their history, and reinstalls
 * restore the classification.
 */
class LibraryMergeTest {

    private fun installed(pkg: String, system: Boolean = false) = InstalledApp(pkg, system)

    @Test
    fun `missing launchable apps get fresh rows`() {
        val plan = LibraryMerge.plan(
            existing = emptyList(),
            discovered = listOf(installed("com.a"), installed("com.b")),
            excluded = emptySet(),
        )
        assertEquals(listOf("com.a", "com.b"), plan.insertPackages)
        assertEquals(emptyList<Application>(), plan.restoreRows)
        assertEquals(emptyList<String>(), plan.markUninstalled)
    }

    @Test
    fun `excluded device furniture never gets a row`() {
        val plan = LibraryMerge.plan(
            existing = emptyList(),
            discovered = listOf(installed("com.a"), installed("com.launcher")),
            excluded = setOf("com.launcher"),
        )
        assertEquals(listOf("com.a"), plan.insertPackages)
    }

    @Test
    fun `an app that vanished is marked uninstalled, not deleted`() {
        val existing = listOf(
            Application("com.gone", "Gone", categoryId = "social", isCategoryUserSet = true),
            Application("com.kept", "Kept"),
        )
        val plan = LibraryMerge.plan(
            existing = existing,
            discovered = listOf(installed("com.kept")),
            excluded = emptySet(),
        )
        assertEquals(listOf("com.gone"), plan.markUninstalled)
        // The row itself is untouched — assignment and history stay on disk.
        assertEquals(emptyList<Application>(), plan.restoreRows)
    }

    @Test
    fun `a reinstall flips back to installed and keeps the assignment`() {
        val existing = listOf(
            Application(
                packageName = "com.back",
                displayName = "Back",
                categoryId = "social",
                isCategoryUserSet = true,
                isInstalled = false,
                firstSeenAt = 123L,
            ),
        )
        val plan = LibraryMerge.plan(
            existing = existing,
            discovered = listOf(installed("com.back")),
            excluded = emptySet(),
        )
        assertEquals(1, plan.restoreRows.size)
        val restored = plan.restoreRows.single()
        assertEquals(true, restored.isInstalled)
        // The user's assignment survives the round-trip.
        assertEquals("social", restored.categoryId)
        assertEquals(true, restored.isCategoryUserSet)
        // firstSeenAt is not re-stamped — the app never wears "New" twice.
        assertEquals(123L, restored.firstSeenAt)
        assertEquals(emptyList<String>(), plan.insertPackages)
    }

    @Test
    fun `system flag drift is corrected on refresh and user fields kept`() {
        val existing = listOf(
            Application("com.app", "App", categoryId = "games", isCategoryUserSet = false, isSystemApp = false),
        )
        val plan = LibraryMerge.plan(
            existing = existing,
            discovered = listOf(installed("com.app", system = true)),
            excluded = emptySet(),
        )
        val updated = plan.restoreRows.single()
        assertEquals(true, updated.isSystemApp)
        assertEquals("games", updated.categoryId)
        assertEquals(false, updated.isCategoryUserSet)
    }

    @Test
    fun `a fully consistent catalog plans no writes`() {
        val existing = listOf(
            Application("com.a", "A", isSystemApp = false),
            Application("com.b", "B", isSystemApp = true),
        )
        val plan = LibraryMerge.plan(
            existing = existing,
            discovered = listOf(installed("com.a", system = false), installed("com.b", system = true)),
            excluded = emptySet(),
        )
        assertEquals(emptyList<String>(), plan.insertPackages)
        assertEquals(emptyList<Application>(), plan.restoreRows)
        assertEquals(emptyList<String>(), plan.markUninstalled)
    }

    @Test
    fun `a managed row whose package is absent from the catalog is marked uninstalled`() {
        // Exclusion decides inserts; raw discovery decides removals (the caller bails
        // on an empty catalog, so this only runs against a real answer).
        val existing = listOf(Application("com.launcher", "Launcher"))
        val plan = LibraryMerge.plan(
            existing = existing,
            discovered = emptyList(),
            excluded = setOf("com.launcher"),
        )
        assertEquals(listOf("com.launcher"), plan.markUninstalled)
    }

    @Test
    fun `a vanished excluded package is not restored when excluded from candidates`() {
        val existing = listOf(
            Application("com.launcher", "Launcher", isInstalled = false),
        )
        val plan = LibraryMerge.plan(
            existing = existing,
            discovered = listOf(installed("com.launcher")),
            excluded = setOf("com.launcher"),
        )
        // Excluded packages are never restored to the visible library by discovery.
        assertEquals(emptyList<Application>(), plan.restoreRows)
        assertEquals(emptyList<String>(), plan.markUninstalled)
    }
}
