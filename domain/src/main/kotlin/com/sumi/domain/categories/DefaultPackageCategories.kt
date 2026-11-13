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
    const val AI = "ai"
    const val FINANCE = "finance"
    const val BROWSER = "browser"

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
        AI,
        FINANCE,
        BROWSER,
    )

    // Exact package rules checked first, so they can disambiguate substrings
    // (e.g. Messenger's com.facebook.orca is Communication despite containing "facebook").
    // Rule for new additions: a substring rule covers anything whose package id contains
    // the token unambiguously; exact rules exist for the rest (regional app ids, apps
    // whose ids don't contain their brand token).
    private val EXACT_RULES: Map<String, String> = mapOf(
        "com.facebook.orca" to COMMUNICATION, // Messenger
        "com.twitter.android" to SOCIAL,
        "com.google.android.apps.playbooks" to READING,
        "com.google.android.apps.nbu.files" to UTILITIES, // Files by Google
        "com.google.android.apps.googlechat" to COMMUNICATION,
        "com.amazon.kindle" to READING,
        "com.hbo.hbonow" to ENTERTAINMENT,
        "com.nianticlabs.pokemongo" to GAMES,
        // Social — TikTok ships a different package id per region.
        "com.ss.android.ugc.trill" to SOCIAL, // TikTok (Asia)
        "com.zhiliaoapp.musically" to SOCIAL, // TikTok (global)
        "com.alienblue.app" to SOCIAL, // Alien Blue (defunct; kept for legacy devices)
        // Communication
        "org.thoughtcrime.securesms" to COMMUNICATION, // Signal
        "com.google.android.gm" to COMMUNICATION, // Gmail
        "com.tencent.mm" to COMMUNICATION, // WeChat
        "jp.naver.line.android" to COMMUNICATION, // LINE (the `linecorp` substring rule misses it)
        "com.skype.raider" to COMMUNICATION,
        "com.skype.m2" to COMMUNICATION, // Skype Lite
        "com.google.android.apps.meetings" to COMMUNICATION, // Meet
        // Entertainment
        "com.amazon.avod.thirdpartyclient" to ENTERTAINMENT, // Prime Video
        // Productivity
        "com.google.android.calendar" to PRODUCTIVITY,
        "com.microsoft.office.word" to PRODUCTIVITY,
        "com.microsoft.office.excel" to PRODUCTIVITY,
        "com.microsoft.office.powerpoint" to PRODUCTIVITY,
        "com.microsoft.office.onenote" to PRODUCTIVITY,
        "com.evernote" to PRODUCTIVITY,
        "com.google.android.apps.tasks" to PRODUCTIVITY,
        // Reading
        "com.overdrive.mobile.android.libby" to READING, // Libby
        // Games
        "com.supercell.brawlstars" to GAMES,
        "com.mobile.legends" to GAMES, // Mobile Legends
        "com.garena.game.codm" to GAMES, // CoD Mobile
        "com.tencent.ig" to GAMES, // PUBG Mobile
        "com.epicgames.fortnite" to GAMES,
        "com.mihoyo.genshinimpact" to GAMES,
        "com.imangi.templerun2" to GAMES, // Temple Run 2
        // Utilities
        "com.google.android.calculator" to UTILITIES,
        // AI assistants
        "com.moonshot.kimichat" to AI, // Kimi
        "ai.perplexity.app.android" to AI, // Perplexity
        "com.tongyi.intl" to AI, // Tongyi / Qwen
        "com.openai.chatgpt" to AI, // ChatGPT
        "ai.x.grok" to AI, // Grok
        "com.anthropic.claude" to AI, // Claude
        "com.deepseek.chat" to AI, // DeepSeek
        "com.google.android.apps.labs.language.tailwind" to AI, // Google Labs (language models)
        // Finance
        "com.tradingview.tradingviewapp" to FINANCE,
        "com.binance.dev" to FINANCE, // Binance
        "com.redotpay" to FINANCE, // RedotPay
        "ru.bpc.mobilebank.bpc" to FINANCE, // BPC mobile bank
        // Browsers
        "com.brave.browser" to BROWSER,
        "org.mozilla.firefox" to BROWSER,
        "com.duckduckgo.mobile.android" to BROWSER,
        // Communication — apps whose ids don't contain their brand token
        "com.google.android.apps.tachyon" to COMMUNICATION, // Google Duo / Meet (legacy id)
        "ch.protonmail.android" to COMMUNICATION, // Proton Mail
        // Utilities — download managers, VPN, developer tools
        "idm.internet.download.manager" to UTILITIES,
        "com.dv.adm" to UTILITIES,
        "ch.protonvpn.android" to UTILITIES,
        "proton.android.pass" to UTILITIES, // Proton Pass
        "com.termux" to UTILITIES, // terminal (power user tool)
        "com.esp_flash.esp_flash_app" to UTILITIES,
        "io.serialflow.espflash" to UTILITIES,
        "com.f0x1d.logfox" to UTILITIES, // logcat viewer
        "app.local1st.files" to UTILITIES, // files manager
        "me.zhanghai.android.files" to UTILITIES, // Material Files
        "com.x8bit.bitwarden" to UTILITIES, // Bitwarden
        "io.ente.auth" to UTILITIES, // Ente Auth
        "com.truecaller" to UTILITIES,
        "dev.leonlatsch.photok" to UTILITIES, // PhotoK editor
        "app.morphe.android.apps.photos" to UTILITIES, // Gallery (patched)
        "com.digibites.accubattery" to UTILITIES, // AccuBattery
        "com.cloudflare.onedotonedotonedotone" to UTILITIES, // 1.1.1.1
        "com.djezzy.internet" to UTILITIES, // Djezzy (carrier app)
        "com.algeria.selfcare.app.android" to UTILITIES, // carrier self-care
        "com.google.android.apps.chromecast.app" to UTILITIES,
        // Entertainment — media streaming / downloaders / music id
        "com.nuvio.app" to ENTERTAINMENT, // Nuvio (media streaming)
        "com.stremio.one" to ENTERTAINMENT,
        "com.simkl.lists" to ENTERTAINMENT, // SIMKL (media tracking)
        "com.nuvio" to ENTERTAINMENT,
        "com.metrolist.music" to ENTERTAINMENT, // Metrolist (YouTube Music client)
        "app.morphe.android.youtube" to ENTERTAINMENT,
        "app.morphe.android.apps.youtube.music" to ENTERTAINMENT,
        "com.deniscerri.ytdl" to ENTERTAINMENT, // NewPipe family (video)
        "com.deniscerri.ytdl.nodejs" to ENTERTAINMENT,
        "com.deniscerri.ytdl.ffmpeg" to ENTERTAINMENT,
        "com.google.android.videos" to ENTERTAINMENT, // Play Movies & TV (legacy)
        "com.shazam.android" to ENTERTAINMENT,
        // Reading — manga/book readers and newsletter/magazine sources
        "app.mihon" to READING,
        "app.komikku" to READING,
        "eu.kanade.tachiyomi.yokai" to READING, // Yokai
        "org.readera" to READING, // ReadEra
        "com.substack.app" to READING,
        "com.google.android.apps.magazines" to READING, // Play Newsstand (legacy)
        "com.serein.reader" to READING,
        "com.faultexception.reader" to READING,
        "eu.kanade.tachiyomi.extension.all.mangaplus" to READING,
        "eu.kanade.tachiyomi.extension.en.weebcentral" to READING,
        "eu.kanade.tachiyomi.extension.all.comicklive" to READING,
        "eu.kanade.tachiyomi.extension.en.ehentai" to READING,
        "eu.kanade.tachiyomi.extension.all.nhentaito" to READING,
        "eu.kanade.tachiyomi.extension.en.hentainexus" to READING,
        "eu.kanade.tachiyomi.extension.all.mangadex" to READING,
        // Education
        "com.kanso.dictionary" to EDUCATION,
        "com.tv5monde.apprendre" to EDUCATION, // TV5Monde Apprendre
        // Games
        "com.innersloth.spacemafia" to GAMES,
        "com.blackpanther.ninjaarashi2" to GAMES, // Ninja Arashi 2
        "com.noodlecake.altosadventure" to GAMES, // Alto's Adventure
        "com.noodlecake.altosodyssey" to GAMES, // Alto's Odyssey
        "com.ansangha.drdriving" to GAMES, // Dr. Driving
        "com.nytimes.crossword" to GAMES,
        // Productivity — notes
        "md.obsidian" to PRODUCTIVITY, // Obsidian
        "com.philkes.notallyx" to PRODUCTIVITY, // Notally
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
