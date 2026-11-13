package com.sumi.data.categories

import com.sumi.core.database.entity.AppCategory
import com.sumi.domain.categories.DefaultPackageCategories

/**
 * Seeded taxonomy (order matters — it defines the default display order).
 * Never delete or rename ids: they are stable references persisted in `applications.categoryId`.
 * Users can create, rename, recolor, reorder, and delete these; deletion reassigns apps.
 *
 * Colors are design tokens into the category palette (plus `categoryNeutral` for grey);
 * never hex values. Defaults avoid red/green semantics entirely.
 */
internal object DefaultCategories {
    val ALL: List<AppCategory> = listOf(
        AppCategory(DefaultPackageCategories.SOCIAL, "Social", "categoryColor1", sortOrder = 0),
        AppCategory(DefaultPackageCategories.COMMUNICATION, "Communication", "categoryColor2", sortOrder = 1),
        AppCategory(DefaultPackageCategories.ENTERTAINMENT, "Entertainment", "categoryColor3", sortOrder = 2),
        AppCategory(DefaultPackageCategories.PRODUCTIVITY, "Productivity", "categoryColor4", sortOrder = 3),
        AppCategory(DefaultPackageCategories.EDUCATION, "Education", "categoryColor5", sortOrder = 4),
        AppCategory(DefaultPackageCategories.READING, "Reading", "categoryColor6", sortOrder = 5),
        AppCategory(DefaultPackageCategories.GAMES, "Games", "categoryColor7", sortOrder = 6),
        AppCategory(DefaultPackageCategories.UTILITIES, "Utilities", "categoryNeutral", sortOrder = 7),
    )
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
