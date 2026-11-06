package com.sumi.feature.insights

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
import com.sumi.core.designsystem.component.SumiCard
import com.sumi.core.designsystem.component.WeeklyBars
import com.sumi.core.designsystem.theme.SumiSpacing

/** Insights (M3): deterministic facts first; interpretations framed as such. */
@Composable
fun InsightsScreen() {
    Column(Modifier.fillMaxSize().padding(SumiSpacing.screenHorizontal)) {
        Spacer(Modifier.height(SumiSpacing.lg))
        Text(text = "Insights", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(SumiSpacing.md))
        Text(
            text = "Is my usage going up, down, or staying steady?",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(SumiSpacing.sm))
        SumiCard {
            WeeklyBars(dailyTotals = List(7) { 0L }, caption = "Daily screen time this week")
        }
        Spacer(Modifier.height(SumiSpacing.md))
        EmptyState(
            title = "Patterns need a few days",
            message = "Sumi starts learning your day from now. Come back later.",
        )
    }
}
