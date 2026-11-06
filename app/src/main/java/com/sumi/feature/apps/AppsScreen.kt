package com.sumi.feature.apps

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

/** M0 placeholder; searchable library lands in M2. */
@Composable
fun AppsScreen() {
    Column(Modifier.fillMaxSize().padding(SumiSpacing.screenHorizontal)) {
        Spacer(Modifier.height(SumiSpacing.lg))
        Text(text = "Apps", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(SumiSpacing.md))
        EmptyState(
            title = "No apps yet",
            message = "Sumi starts learning your day from now. Come back later.",
        )
    }
}
