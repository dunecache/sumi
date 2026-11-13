package com.sumi.data.categories

import com.sumi.core.database.entity.AppCategory
import com.sumi.domain.categories.DefaultPackageCategories

/**
 * Seeded taxonomy (order matters — it defines the default display order).
 * Never delete or rename ids: they are stable references persisted in `applications.categoryId`.
 * Users can create, rename, recolor, reorder, and delete these; deletion reassigns apps.
 *
 * Colors are primitive design tokens from the canonical charts palette (plus `stone-1`
 * for the residual utility slot); never hex values. Defaults avoid red/green semantics
 * and never use `constant` — it is reserved (terracotta is the warning hue, kept for
 * the over-goal state only).
 */
internal object DefaultCategories {
    val ALL: List<AppCategory> = listOf(
        AppCategory(DefaultPackageCategories.SOCIAL, "Social", "matcha-4", sortOrder = 0),
        AppCategory(DefaultPackageCategories.COMMUNICATION, "Communication", "clay-2", sortOrder = 1),
        AppCategory(DefaultPackageCategories.ENTERTAINMENT, "Entertainment", "ochre", sortOrder = 2),
        AppCategory(DefaultPackageCategories.PRODUCTIVITY, "Productivity", "mist", sortOrder = 3),
        AppCategory(DefaultPackageCategories.EDUCATION, "Education", "wisteria", sortOrder = 4),
        AppCategory(DefaultPackageCategories.READING, "Reading", "method", sortOrder = 5),
        AppCategory(DefaultPackageCategories.GAMES, "Games", "ochre", sortOrder = 6),
        AppCategory(DefaultPackageCategories.UTILITIES, "Utilities", "matcha-2", sortOrder = 7),
    )

    /**
     * The color tokens the 2026-11 seeds shipped with, keyed by category id. Used only
     * to recognize rows that still carry their original positional seed token.
     */
    private val LEGACY_SEEDED_COLOR_TOKENS: Map<String, String> = mapOf(
        DefaultPackageCategories.SOCIAL to "categoryColor1",
        DefaultPackageCategories.COMMUNICATION to "categoryColor2",
        DefaultPackageCategories.ENTERTAINMENT to "categoryColor3",
        DefaultPackageCategories.PRODUCTIVITY to "categoryColor4",
        DefaultPackageCategories.EDUCATION to "categoryColor5",
        DefaultPackageCategories.READING to "categoryColor6",
        DefaultPackageCategories.GAMES to "categoryColor7",
        DefaultPackageCategories.UTILITIES to "categoryNeutral",
    )

    /**
     * Recolors seeded categories that still carry their original positional token
     * (2026-11) to the primitive-named default from [ALL]. Any row the user touched
     * (recolor, or user-created) is left alone, and rows already on a primitive name
     * are not rewritten — so this is idempotent. `sortOrder` is preserved, so a user
     * reorder survives too.
     */
    fun recolorUntouchedDefaults(stored: List<AppCategory>): List<AppCategory> {
        val byId = ALL.associateBy { it.id }
        return stored.mapNotNull { row ->
            val fresh = byId[row.id] ?: return@mapNotNull null
            val legacy = LEGACY_SEEDED_COLOR_TOKENS[row.id] ?: return@mapNotNull null
            if (!row.isUserCreated && row.colorToken == legacy && row.colorToken != fresh.colorToken) {
                row.copy(colorToken = fresh.colorToken)
            } else {
                null
            }
        }
    }
}

/**
 * Slug for a user-created category: lowercase, dash-separated, suffixed with `-2`, `-3`…
 * to stay unique against [existingIds]. Blank or symbol-only names fall back to
 * "category". Pure and tested (CategoriesLogicTest).
 */
internal fun categorySlug(name: String, existingIds: Set<String>): String {
    val base = name.trim()
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .ifEmpty { "category" }
    var candidate = base
    var suffix = 2
    while (candidate in existingIds) {
        candidate = "$base-$suffix"
        suffix += 1
    }
    return candidate
}
