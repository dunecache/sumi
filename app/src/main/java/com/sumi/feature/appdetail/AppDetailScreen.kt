package com.sumi.feature.appdetail

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

/** App detail (M2): individual usage history. Placeholder shows empty state until data flows. */
@Composable
fun AppDetailScreen(packageName: String) {
    Column(Modifier.fillMaxSize().padding(SumiSpacing.screenHorizontal)) {
        Spacer(Modifier.height(SumiSpacing.lg))
        Text(text = packageName.substringAfterLast('.'), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(SumiSpacing.md))
        Text(text = packageName, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(SumiSpacing.lg))
        EmptyState(
            title = "No history yet",
            message = "Usage history for this app will appear here.",
        )
    }
}
