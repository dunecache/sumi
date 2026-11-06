package com.sumi

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DummyInstrumentedTest {
    @Test
    fun useAppContext_hasExpectedPackage() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertThat(context.packageName).isEqualTo("com.sumi")
        assertThat(InstrumentationRegistry.getTargetContext()).isNotNull()
    }
}
