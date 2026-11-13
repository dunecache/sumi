package com.sumi.domain.categories

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DefaultPackageCategoriesTest {

    @Test
    fun `well-known packages map to their default category`() {
        assertThat(DefaultPackageCategories.forPackage("com.instagram.android")).isEqualTo("social")
        assertThat(DefaultPackageCategories.forPackage("com.whatsapp")).isEqualTo("communication")
        assertThat(DefaultPackageCategories.forPackage("com.google.android.youtube")).isEqualTo("entertainment")
        assertThat(DefaultPackageCategories.forPackage("com.slack")).isEqualTo("productivity")
        assertThat(DefaultPackageCategories.forPackage("com.duolingo")).isEqualTo("education")
        assertThat(DefaultPackageCategories.forPackage("com.amazon.kindle")).isEqualTo("reading")
        assertThat(DefaultPackageCategories.forPackage("com.mojang.minecraftpe")).isEqualTo("games")
    }

    @Test
    fun `regional and non-brand-token ids map to their categories`() {
        assertThat(DefaultPackageCategories.forPackage("com.ss.android.ugc.trill")).isEqualTo("social") // TikTok (Asia)
        assertThat(DefaultPackageCategories.forPackage("com.zhiliaoapp.musically")).isEqualTo("social") // TikTok (global)
        assertThat(DefaultPackageCategories.forPackage("org.thoughtcrime.securesms")).isEqualTo("communication") // Signal
        assertThat(DefaultPackageCategories.forPackage("com.google.android.gm")).isEqualTo("communication") // Gmail
        assertThat(DefaultPackageCategories.forPackage("com.tencent.mm")).isEqualTo("communication") // WeChat
        assertThat(DefaultPackageCategories.forPackage("jp.naver.line.android")).isEqualTo("communication") // LINE
        assertThat(DefaultPackageCategories.forPackage("com.google.android.apps.meetings")).isEqualTo("communication") // Meet
        assertThat(DefaultPackageCategories.forPackage("com.amazon.avod.thirdpartyclient")).isEqualTo("entertainment") // Prime Video
        assertThat(DefaultPackageCategories.forPackage("com.microsoft.office.word")).isEqualTo("productivity")
        assertThat(DefaultPackageCategories.forPackage("com.tencent.ig")).isEqualTo("games") // PUBG
    }

    @Test
    fun `reddit clients map to social`() {
        // Already covered by the `reddit` substring rule (pinning behaviour):
        assertThat(DefaultPackageCategories.forPackage("com.reddit.frontpage")).isEqualTo("social") // official
        assertThat(DefaultPackageCategories.forPackage("com.andrewshu.android.reddit")).isEqualTo("social") // Reddit is Fun
        assertThat(DefaultPackageCategories.forPackage("it.d4w.android.proreddit")).isEqualTo("social") // Boost for Reddit
        assertThat(DefaultPackageCategories.forPackage("com.laurencedawson.redditbrowser")).isEqualTo("social") // Relay
        // Needs an exact rule (contains no "reddit"):
        assertThat(DefaultPackageCategories.forPackage("com.alienblue.app")).isEqualTo("social") // Alien Blue
    }

    @Test
    fun `exact rules take precedence over substrings`() {
        // Messenger shares the "facebook" substring but is communication, not social.
        assertThat(DefaultPackageCategories.forPackage("com.facebook.orca")).isEqualTo("communication")
        assertThat(DefaultPackageCategories.forPackage("com.facebook.katana")).isEqualTo("social")
    }

    @Test
    fun `unknown packages have no default - never silently classified`() {
        assertThat(DefaultPackageCategories.forPackage("com.example.randomapp")).isNull()
        assertThat(DefaultPackageCategories.forPackage("org.something.obscure")).isNull()
    }

    @Test
    fun `matching is case-insensitive and trims`() {
        assertThat(DefaultPackageCategories.forPackage("  COM.INSTAGRAM.Android ")).isEqualTo("social")
    }

    @Test
    fun `blank package names return null`() {
        assertThat(DefaultPackageCategories.forPackage("")).isNull()
        assertThat(DefaultPackageCategories.forPackage("   ")).isNull()
    }

    @Test
    fun `every default id referenced by rules exists in the taxonomy`() {
        val ids = DefaultPackageCategories.DEFAULT_IDS.toSet()
        // Spot-check each id is producible by at least one rule.
        val producible = listOf(
            "com.instagram.android",
            "com.whatsapp",
            "com.google.android.youtube",
            "com.slack",
            "com.duolingo",
            "com.amazon.kindle",
            "com.mojang.minecraftpe",
            "com.google.android.apps.nbu.files",
        ).mapNotNull { DefaultPackageCategories.forPackage(it) }.toSet()
        assertThat(producible).isEqualTo(ids)
    }
}
