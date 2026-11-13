package com.sumi.domain.categories

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CategoryAnalyticsTest {

    private val names = mapOf(
        "social" to "Social",
        "entertainment" to "Entertainment",
    )

    @Test
    fun `empty usage yields empty breakdown`() {
        val result = CategoryAnalytics.breakdown(
            perApp = emptyList(),
            categoryNames = names,
            assignment = mapOf("com.instagram.android" to "social"),
        )
        assertThat(result.totalDurationMs).isEqualTo(0L)
        assertThat(result.categories).isEmpty()
        assertThat(result.shareOf(CategoryUsage("social", "Social", 0L, 0))).isEqualTo(0.0)
    }

    @Test
    fun `apps aggregate into their assigned categories`() {
        val result = CategoryAnalytics.breakdown(
            perApp = listOf(
                AppUsage("com.instagram.android", 3_600_000L),
                AppUsage("com.twitter.android", 1_200_000L),
                AppUsage("com.netflix.party", 2_400_000L),
            ),
            categoryNames = names,
            assignment = mapOf(
                "com.instagram.android" to "social",
                "com.twitter.android" to "social",
                "com.netflix.party" to "entertainment",
            ),
        )
        assertThat(result.totalDurationMs).isEqualTo(7_200_000L)
        assertThat(result.categories.map { it.name to it.durationMs }).containsExactly(
            "Social" to 4_800_000L,
            "Entertainment" to 2_400_000L,
        ).inOrder()
        assertThat(result.categories.first().appCount).isEqualTo(2)
    }

    @Test
    fun `unassigned apps land in the uncategorized bucket which sorts last`() {
        val result = CategoryAnalytics.breakdown(
            perApp = listOf(
                AppUsage("com.unknown.one", 5_000_000L),
                AppUsage("com.instagram.android", 1_000_000L),
            ),
            categoryNames = names,
            assignment = mapOf("com.instagram.android" to "social"),
        )
        assertThat(result.categories).hasSize(2)
        // Uncategorized is the residual bucket, never ranked among named categories.
        assertThat(result.categories.last().categoryId).isNull()
        assertThat(result.categories.last().name).isEqualTo("Uncategorized")
        assertThat(result.categories.last().durationMs).isEqualTo(5_000_000L)
        assertThat(result.categories.first().durationMs).isEqualTo(1_000_000L)
        assertThat(result.totalDurationMs).isEqualTo(6_000_000L)
    }

    @Test
    fun `category sum always reconciles with per-app sum`() {
        val perApp = listOf(
            AppUsage("a", 1_111L),
            AppUsage("b", 2_222L),
            AppUsage("c", 3_333L),
            AppUsage("d", 4_444L),
        )
        val assignment = mapOf("a" to "social", "b" to "social", "c" to null)
        val result = CategoryAnalytics.breakdown(perApp, names, assignment)
        val categorySum = result.categories.sumOf { it.durationMs }
        assertThat(categorySum).isEqualTo(result.totalDurationMs)
        assertThat(categorySum).isEqualTo(perApp.sumOf { it.durationMs })
    }

    @Test
    fun `share denominator is total tracked usage including uncategorized`() {
        val result = CategoryAnalytics.breakdown(
            perApp = listOf(
                AppUsage("com.instagram.android", 1_000_000L),
                AppUsage("com.unknown.one", 1_000_000L),
            ),
            categoryNames = names,
            assignment = mapOf("com.instagram.android" to "social"),
        )
        val social = result.categories.first()
        assertThat(result.shareOf(social)).isWithin(1e-9).of(0.5)
    }

    @Test
    fun `reassignment re-derives history from the same raw usage`() {
        val perApp = listOf(AppUsage("com.instagram.android", 2_000_000L))
        val before = CategoryAnalytics.breakdown(
            perApp,
            names,
            mapOf("com.instagram.android" to "social"),
        )
        val after = CategoryAnalytics.breakdown(
            perApp,
            names,
            mapOf("com.instagram.android" to "entertainment"),
        )
        // Raw usage untouched: the total is identical, only the derived bucket moves.
        assertThat(before.totalDurationMs).isEqualTo(after.totalDurationMs)
        assertThat(after.categories.single().categoryId).isEqualTo("entertainment")
        assertThat(after.categories.single().durationMs).isEqualTo(2_000_000L)
    }

    @Test
    fun `zero-duration rows are ignored and never create empty buckets`() {
        val result = CategoryAnalytics.breakdown(
            perApp = listOf(AppUsage("com.instagram.android", 0L)),
            categoryNames = names,
            assignment = mapOf("com.instagram.android" to "social"),
        )
        assertThat(result.categories).isEmpty()
        assertThat(result.totalDurationMs).isEqualTo(0L)
    }

    @Test
    fun `equal durations tiebreak alphabetically`() {
        val result = CategoryAnalytics.breakdown(
            perApp = listOf(
                AppUsage("pkg.b", 1_000L),
                AppUsage("pkg.a", 1_000L),
            ),
            categoryNames = mapOf("b" to "Beta", "a" to "Alpha"),
            assignment = mapOf("pkg.b" to "b", "pkg.a" to "a"),
        )
        assertThat(result.categories.map { it.name }).containsExactly("Alpha", "Beta").inOrder()
    }

    @Test
    fun `missing category name falls back to the raw id`() {
        val result = CategoryAnalytics.breakdown(
            perApp = listOf(AppUsage("pkg", 500L)),
            categoryNames = emptyMap(),
            assignment = mapOf("pkg" to "mystery"),
        )
        assertThat(result.categories.single().name).isEqualTo("mystery")
    }
}
