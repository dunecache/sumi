package com.sumi.feature.today

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sumi.core.designsystem.component.EmptyState
import com.sumi.core.designsystem.theme.SumiSpacing
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * M0 placeholder. M2 adds total screen time, most-used apps, distribution,
 * timeline, and one small observation. Follows DESIGN.md §5.1 order.
 */
@Composable
fun TodayScreen(onOpenSettings: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(SumiSpacing.screenHorizontal)) {
        Spacer(Modifier.height(SumiSpacing.lg))
        Text(
            text = "TODAY · ${LocalDate.now().dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()).uppercase()}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = "Your day, in perspective.", style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(SumiSpacing.lg))
        EmptyState(
            title = "No usage yet",
            message = "Sumi starts learning your day from now. Come back later.",
        )
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onOpenSettings) { Text("Settings") }
    }
}
