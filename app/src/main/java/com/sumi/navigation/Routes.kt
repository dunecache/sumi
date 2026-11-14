package com.sumi.navigation

/** Route names. Onboarding graph sits outside the 4-destination bottom bar. */
object Routes {
    const val ONBOARDING_WELCOME = "onboarding/welcome"
    const val ONBOARDING_PRIVACY = "onboarding/privacy"
    const val ONBOARDING_USAGE_ACCESS = "onboarding/usage_access"
    const val ONBOARDING_INITIAL_SYNC = "onboarding/initial_sync"
    const val ONBOARDING_FIRST_OVERVIEW = "onboarding/first_overview"

    const val TODAY = "today"
    const val INSIGHTS = "insights"
    const val FOCUS = "focus"
    const val APPS = "apps"
    const val SETTINGS = "settings"
    const val CATEGORY_MANAGER = "categories"

    /** Deep-link: `apps?filter=uncategorized` (or a category id). */
    fun appsWithFilter(filter: String) = "$APPS?filter=$filter"

    fun appDetail(packageName: String) = "app-detail/$packageName"
    fun appDetailPattern() = "app-detail/{packageName}"

    fun categoryDetail(categoryId: String, range: String? = null): String =
        if (range.isNullOrEmpty()) {
            "category/$categoryId"
        } else {
            "category/$categoryId?range=$range"
        }

    fun categoryDetailPattern() = "category/{categoryId}?range={range}"
}
