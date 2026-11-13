package com.sumi.domain.categories

/**
 * Static default package → category classification.
 *
 * Defaults are advisory only: they are applied once when an application row is created
 * (or at bootstrap for rows that have never been classified) and **never overwrite a
 * user assignment** (`applications.isCategoryUserSet = true` always wins).
 *
 * Unknown packages have **no** default — they stay uncategorized rather than being
 * silently classified as something specific (PRINCIPLES.md §2.10, hard constraint 10).
 * Matching runs on the full package identifier, never on display names, which can be
 * localized or duplicated.
 */
object DefaultPackageCategories {

    const val SOCIAL = "social"
    const val COMMUNICATION = "communication"
    const val ENTERTAINMENT = "entertainment"
    const val PRODUCTIVITY = "productivity"
    const val EDUCATION = "education"
    const val READING = "reading"
    const val GAMES = "games"
    const val UTILITIES = "utilities"

    /** Default category ids; order mirrors the seeded taxonomy. */
    val DEFAULT_IDS = listOf(
        SOCIAL,
        COMMUNICATION,
        ENTERTAINMENT,
        PRODUCTIVITY,
        EDUCATION,
        READING,
        GAMES,
        UTILITIES,
    )

    // Exact package rules checked first, so they can disambiguate substrings
    // (e.g. Messenger's com.facebook.orca is Communication despite containing "facebook").
    private val EXACT_RULES: Map<String, String> = mapOf(
        "com.facebook.orca" to COMMUNICATION, // Messenger
        "com.twitter.android" to SOCIAL,
        "com.google.android.apps.playbooks" to READING,
        "com.google.android.apps.nbu.files" to UTILITIES, // Files by Google
        "com.google.android.apps.googlechat" to COMMUNICATION,
        "com.amazon.kindle" to READING,
        "com.hbo.hbonow" to ENTERTAINMENT,
        "com.nianticlabs.pokemongo" to GAMES,
    )

    // Substring rules on the lowercased package identifier.
    private val CONTAINS_RULES: List<Pair<String, String>> = listOf(
        // Social
        "instagram" to SOCIAL,
        "facebook" to SOCIAL,
        "tiktok" to SOCIAL,
        "snapchat" to SOCIAL,
        "reddit" to SOCIAL,
        "pinterest" to SOCIAL,
        "tumblr" to SOCIAL,
        "linkedin" to SOCIAL,
        "discord" to SOCIAL,
        // Communication
        "whatsapp" to COMMUNICATION,
        "telegram" to COMMUNICATION,
        "signal" to COMMUNICATION,
        "gmail" to COMMUNICATION,
        "messaging" to COMMUNICATION,
        "sms" to COMMUNICATION,
        "viber" to COMMUNICATION,
        "wechat" to COMMUNICATION,
        "linecorp" to COMMUNICATION,
        "outlook" to COMMUNICATION,
        // Entertainment
        "youtube" to ENTERTAINMENT,
        "netflix" to ENTERTAINMENT,
        "spotify" to ENTERTAINMENT,
        "twitch" to ENTERTAINMENT,
        "primevideo" to ENTERTAINMENT,
        "hulu" to ENTERTAINMENT,
        "soundcloud" to ENTERTAINMENT,
        "deezer" to ENTERTAINMENT,
        "tidal" to ENTERTAINMENT,
        "disney" to ENTERTAINMENT,
        // Productivity
        "slack" to PRODUCTIVITY,
        "notion" to PRODUCTIVITY,
        "docs" to PRODUCTIVITY, // also matches Drive (com.google.android.apps.docs)
        "sheets" to PRODUCTIVITY,
        "slides" to PRODUCTIVITY,
        "keep" to PRODUCTIVITY, // com.google.android.keep
        "teams" to PRODUCTIVITY,
        "zoom" to PRODUCTIVITY,
        "github" to PRODUCTIVITY,
        "gitlab" to PRODUCTIVITY,
        "trello" to PRODUCTIVITY,
        "asana" to PRODUCTIVITY,
        // Education
        "duolingo" to EDUCATION,
        "khan" to EDUCATION,
        "coursera" to EDUCATION,
        "udemy" to EDUCATION,
        "edx" to EDUCATION,
        "quizlet" to EDUCATION,
        "anki" to EDUCATION,
        "brilliant" to EDUCATION,
        "memrise" to EDUCATION,
        // Reading
        "audible" to READING,
        "goodreads" to READING,
        "blinkist" to READING,
        "readitlater" to READING, // Pocket
        "medium" to READING,
        // Games (heuristics are weak; most games intentionally stay uncategorized)
        "candycrush" to GAMES,
        "minecraft" to GAMES,
        "roblox" to GAMES,
        "clashofclans" to GAMES,
        "clashroyale" to GAMES,
        "amongus" to GAMES,
        "pokemon" to GAMES,
        // Utilities
        "calculator" to UTILITIES,
    )

    /**
     * Default category id for [packageName], or `null` when no confident default exists.
     * Case-insensitive; exact rules take precedence over substring rules.
     */
    fun forPackage(packageName: String): String? {
        val pkg = packageName.trim().lowercase()
        if (pkg.isEmpty()) return null
        EXACT_RULES[pkg]?.let { return it }
        return CONTAINS_RULES.firstOrNull { (token, _) -> token in pkg }?.second
    }
}
