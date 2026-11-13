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
