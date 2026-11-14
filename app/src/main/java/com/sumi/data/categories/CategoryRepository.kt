package com.sumi.data.categories

import com.sumi.core.common.AppDispatchers
import com.sumi.core.database.SumiDatabase
import com.sumi.core.database.dao.AppCategoryDao
import com.sumi.core.database.dao.ApplicationDao
import com.sumi.core.database.entity.AppCategory
import com.sumi.domain.categories.DefaultPackageCategories
import androidx.room.withTransaction
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Category system data layer (ARCHITECTURE.md §13).
 *
 * Categories are user-manageable entities persisted in `app_categories`. Applications
 * carry exactly one primary `categoryId` (or null = uncategorized); user assignments
 * (`isCategoryUserSet`) are never overwritten by default classification.
 *
 * Raw usage rows are never rewritten by category operations: category totals are
 * derived at read time (see CategoryAnalytics), so reassignment re-derives all
 * historical views from the untouched per-app daily records.
 */
@Singleton
class CategoryRepository @Inject constructor(
    private val db: SumiDatabase,
    private val categoryDao: AppCategoryDao,
    private val applicationDao: ApplicationDao,
    private val dispatchers: AppDispatchers,
) {

    /**
     * Seeds the default taxonomy. Fresh installs (no rows at all) get [DefaultCategories.ALL];
     * existing installs get only the seeds that did not exist when they upgraded (see
     * [DefaultCategories.backfillSeeds] — ids the user deleted are never re-added).
     * Idempotent.
     */
    suspend fun ensureSeeded() = withContext(dispatchers.io) {
        val count = categoryDao.count()
        if (count == 0) {
            categoryDao.insertAll(DefaultCategories.ALL)
            return@withContext
        }
        val existing = categoryDao.getAll().map { it.id }.toSet()
        val missing = DefaultCategories.backfillSeeds(existing)
        if (missing.isNotEmpty()) categoryDao.insertAll(missing)
    }

    /**
     * Recolors seeded categories that still carry their original 2026-11 positional
     * token to the primitive-named default. Rows the user recolored (or reordered —
     * `sortOrder` is preserved) are untouched. Idempotent.
     */
    suspend fun syncSeededColors() = withContext(dispatchers.io) {
        val updated = DefaultCategories.recolorUntouchedDefaults(categoryDao.getAll())
        if (updated.isEmpty()) return@withContext
        db.withTransaction {
            updated.forEach { categoryDao.upsert(it) }
        }
    }

    /**
     * Applies default classification to applications that have never been handled:
     * rows with no user assignment and no category, whose package matches a confident
     * default. Unknown packages are left uncategorized — never silently classified.
     */
    suspend fun classifyPendingAssignments() = withContext(dispatchers.io) {
        ensureSeeded()
        applicationDao.getUnclassified().forEach { row ->
            DefaultPackageCategories.forPackage(row.packageName)?.let { default ->
                applicationDao.setCategory(row.packageName, default, userSet = false)
            }
        }
    }

    fun observeCategories(): Flow<List<AppCategory>> = categoryDao.observeAll()

    /**
     * Creates a user category; name blank → null. Ids are stable slugs.
     * @return the new category's id, or null when the name was blank.
     */
    suspend fun create(name: String, colorToken: String): String? = withContext(dispatchers.io) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@withContext null
        val existing = categoryDao.getAll()
        val slug = categorySlug(trimmed, existing.map { it.id }.toSet())
        val nextOrder = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1
        categoryDao.insertAll(listOf(AppCategory(slug, trimmed, colorToken, nextOrder, isUserCreated = true)))
        slug
    }

    suspend fun rename(id: String, name: String) = withContext(dispatchers.io) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@withContext
        categoryDao.getById(id)?.let { categoryDao.upsert(it.copy(name = trimmed)) }
    }

    suspend fun setColor(id: String, colorToken: String) = withContext(dispatchers.io) {
        categoryDao.getById(id)?.let { categoryDao.upsert(it.copy(colorToken = colorToken)) }
    }

    /** Rewrites sortOrder to match the given id order (first = 0). */
    suspend fun reorder(orderedIds: List<String>) = withContext(dispatchers.io) {
        db.withTransaction {
            orderedIds.forEachIndexed { index, id ->
                categoryDao.getById(id)?.let { categoryDao.upsert(it.copy(sortOrder = index)) }
            }
        }
    }

    /**
     * Deletes a category and moves its applications.
     *
     * Target semantics: `reassignTo == null` → moved apps become uncategorized.
     * User-assigned rows stay user-assigned; auto-classified rows keep
     * `isCategoryUserSet = false` so default classification may claim them again.
     */
    suspend fun delete(id: String, reassignTo: String?) = withContext(dispatchers.io) {
        // A category can never be reassigned onto itself (that would defeat deletion).
        val target = reassignTo?.takeIf { it != id }
        db.withTransaction {
            applicationDao.reassignUserAssigned(id, target)
            applicationDao.reassignAutoAssigned(id, target)
            categoryDao.deleteById(id)
        }
    }

    /** Explicit user assignment; always wins over defaults. Pass null for uncategorized. */
    suspend fun setAppCategory(packageName: String, categoryId: String?) = withContext(dispatchers.io) {
        applicationDao.setCategory(packageName, categoryId, userSet = true)
    }
}
