package com.sumi.domain.categories

/** Neutral category taxonomy (PRINCIPLES.md: no category is "good" or "bad"). */
object Categories {
    val ALL = listOf("social", "entertainment", "learning", "work", "communication", "other")

    fun colorTokenFor(id: String): String = when (id) {
        "social" -> "categoryColor1"
        "entertainment" -> "categoryColor2"
        "learning" -> "categoryColor3"
        "work" -> "categoryColor4"
        "communication" -> "categoryColor5"
        else -> "categoryColor6"
    }

    /** Default package-prefix mapping; user overrides win (see Application.isCategoryUserSet). */
    fun defaultFor(packageName: String): String {
        val pkg = packageName.lowercase()
        return when {
            pkg.contains("facebook") || pkg.contains("instagram") || pkg.contains("tiktok") || pkg.contains("twitter") || pkg.contains("reddit") -> "social"
            pkg.contains("youtube") || pkg.contains("netflix") || pkg.contains("spotify") || pkg.contains("twitch") -> "entertainment"
            pkg.contains("edu") || pkg.contains("khan") || pkg.contains("duolingo") || pkg.contains("coursera") -> "learning"
            pkg.contains("slack") || pkg.contains("notion") || pkg.contains("docs") || pkg.contains("sheets") -> "work"
            pkg.contains("whatsapp") || pkg.contains("telegram") || pkg.contains("gmail") || pkg.contains("messages") -> "communication"
            else -> "other"
        }
    }
}
