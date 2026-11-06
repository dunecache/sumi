package com.sumi

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Minimal sanity test so `:app:testDebugUnitTest` has coverage in M0. Real logic tests live in `:domain`. */
class SanityTest {
    @Test
    fun `app identity is stable`() {
        assertThat("com.sumi").isEqualTo("com.sumi")
    }
}
