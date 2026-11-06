package com.sumi.feature.focus

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sumi.core.designsystem.component.EmptyState
import com.sumi.core.designsystem.theme.SumiSpacing

/**
 * M0 placeholder. M4 adds intentions, focus sessions, limits, quiet periods.
 * Must not look like a control panel of restrictions; nothing on by default.
 */
@Composable
fun FocusScreen() {
    Column(Modifier.fillMaxSize().padding(SumiSpacing.screenHorizontal)) {
        Spacer(Modifier.height(SumiSpacing.lg))
        Text(text = "Focus", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(SumiSpacing.md))
        EmptyState(
            title = "Nothing set yet",
            message = "Focus tools are optional. When you want a gentle nudge, it will live here.",
        )
    }
}
