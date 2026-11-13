package com.sumi.feature.focus

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.sumi.core.designsystem.component.EmptyState
import com.sumi.core.designsystem.component.SumiCard
import com.sumi.core.designsystem.theme.SumiSpacing

/** Focus (M4): intentions, limits, quiet periods. Nothing on by default; calm cards, no lock icons. */
@Composable
fun FocusScreen() {
    var intention by remember { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(SumiSpacing.screenHorizontal),
    ) {
        Spacer(Modifier.height(SumiSpacing.lg))
        Text(text = "Focus", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(SumiSpacing.md))
        Text(
            text = "Would you like to set an intention for today?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(SumiSpacing.sm))
        SumiCard {
            OutlinedTextField(
                value = intention,
                onValueChange = { intention = it },
                label = { Text("Today's intention") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(SumiSpacing.sm))
            Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("Save intention") }
        }
        Spacer(Modifier.height(SumiSpacing.md))
        EmptyState(
            title = "Nothing else set yet",
            message = "Focus tools are optional. When you want a gentle nudge, it will live here.",
        )
    }
}
