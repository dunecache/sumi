package com.sumi.data.categories

import org.junit.Assert.assertEquals
import org.junit.Test

class CategoriesLogicTest {

    @Test
    fun `simple names produce dash-separated slugs`() {
        assertEquals("social", categorySlug("Social", emptySet()))
        assertEquals("my-work-apps", categorySlug("My work apps", emptySet()))
        assertEquals("reading-news", categorySlug("Reading & News", emptySet()))
    }

    @Test
    fun `collisions get a numeric suffix`() {
        assertEquals("work-2", categorySlug("Work", setOf("work")))
        assertEquals("work-3", categorySlug("Work", setOf("work", "work-2")))
    }

    @Test
    fun `blank or symbol-only names fall back to a stable base`() {
        assertEquals("category", categorySlug("   ", emptySet()))
        assertEquals("category-2", categorySlug("!!!", setOf("category")))
    }

    @Test
    fun `casing and surrounding punctuation are normalized`() {
        assertEquals("games", categorySlug("--GAMES--", emptySet()))
    }
}
