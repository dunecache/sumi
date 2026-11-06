package com.sumi.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.sumi.core.datastore.ThemeMode
import com.sumi.core.designsystem.theme.SumiSpacing

/** M0 skeleton: privacy statement + theme. Data management (export/delete) lands in M2/M5. */
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeSelected: (ThemeMode) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(SumiSpacing.screenHorizontal)) {
        Spacer(Modifier.height(SumiSpacing.lg))
        Text(text = "Settings", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(SumiSpacing.md))
        Text(
            text = "Sumi processes and stores your usage on this device only. " +
                "There is no account, no analytics, and no network access.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(SumiSpacing.xl))
        Text(text = "Theme", style = MaterialTheme.typography.titleMedium)
        ThemeMode.entries.forEach { mode ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = SumiSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = themeMode == mode, onClick = { onThemeSelected(mode) })
                Text(text = mode.name.lowercase().replaceFirstChar(Char::uppercase), modifier = Modifier.padding(start = SumiSpacing.sm))
            }
        }
    }
}
