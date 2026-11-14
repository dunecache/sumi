package com.sumi.core.usage

import org.junit.Assert.assertEquals
import org.junit.Test

class UsageDerivationTest {

    @Test
    fun `rangeCaption of an empty range is empty, not a crash`() {
        assertEquals("", rangeCaption(emptyList()))
    }
}
