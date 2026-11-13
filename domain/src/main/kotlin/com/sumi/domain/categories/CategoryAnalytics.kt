package com.sumi.domain.categories

/**
 * Category analytics model (DATA_MODEL.md §4, ARCHITECTURE.md §13).
 *
 * Derivation model: category totals are **never stored**. Every read recomputes the
 * breakdown from immutable per-app usage rows joined with the *current* assignment.
 * Consequences (see the reassignment policy in ARCHITECTURE.md):
 *  - Changing an app's category retroactively re-derives all historical category views.
 *  - Raw usage records (sessions, per-app daily rows) are never rewritten.
 *  - The invariant `Σ category durations == Σ per-app usage` holds for every window,
 *    because both sums are derived from the same rows.
 */
data class AppUsage(val packageName: String, val durationMs: Long)

/**
 * One derived category bucket.
 *
 * @param categoryId stable slug from `app_categories`, or [UNCATEGORIZED_ID] for the
 *   residual bucket of apps with no assignment (apps are never silently classified,
 *   PRINCIPLES.md §2.10).
 */
data class CategoryUsage(
    val categoryId: String?,
    val name: String,
    val durationMs: Long,
    val appCount: Int,
)

/** Derived category view of one usage window. */
data class CategoryBreakdown(
    val totalDurationMs: Long,
    /** Ordered largest-first; the Uncategorized bucket is always last (residual, not a category). */
    val categories: List<CategoryUsage>,
) {
    /**
     * Share of [totalDurationMs] (the documented denominator: *all* tracked usage,
     * including the uncategorized bucket — never just the visible categories).
     */
    fun shareOf(category: CategoryUsage): Double =
        if (totalDurationMs <= 0L) 0.0 else category.durationMs.toDouble() / totalDurationMs.toDouble()
}

object CategoryAnalytics {

    /** Sentinel id for the uncategorized residual bucket. */
    val UNCATEGORIZED_ID: String? = null

    /**
     * Derives the breakdown from per-app usage for a window and the current assignment.
     *
     * @param categoryNames display names for known category ids (from `app_categories`);
     *   ids missing here keep their raw id as a fallback name.
     * @param assignment packageName → categoryId (null = uncategorized). Apps absent from
     *   the map are treated as uncategorized.
     * @param uncategorizedName label for the residual bucket.
     */
    fun breakdown(
        perApp: List<AppUsage>,
        categoryNames: Map<String, String>,
        assignment: Map<String, String?>,
        uncategorizedName: String = "Uncategorized",
    ): CategoryBreakdown {
        if (perApp.isEmpty()) return CategoryBreakdown(0L, emptyList())
        val buckets = linkedMapOf<String?, LongAccumulator>()
        for (usage in perApp) {
            if (usage.durationMs <= 0L) continue
            val id = assignment[usage.packageName]
            val bucket = buckets.getOrPut(id) { LongAccumulator() }
            bucket.total += usage.durationMs
            bucket.apps += 1
        }
        val named = buckets.map { (id, acc) ->
            val name = when {
                id == null -> uncategorizedName
                else -> categoryNames[id] ?: id // raw id fallback when a category vanished
            }
            CategoryUsage(id, name, acc.total, acc.apps)
        }
        val namedSorted = named
            .filter { it.categoryId != null }
            .sortedWith(compareByDescending<CategoryUsage> { it.durationMs }.thenBy { it.name })
        val uncategorized = named.filter { it.categoryId == null }
        val total = perApp.sumOf { it.durationMs.coerceAtLeast(0L) }
        return CategoryBreakdown(total, namedSorted + uncategorized)
    }

    private data class LongAccumulator(var total: Long = 0L, var apps: Int = 0)
}
