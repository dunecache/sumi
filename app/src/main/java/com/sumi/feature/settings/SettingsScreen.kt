package com.sumi.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.sumi.core.datastore.ThemeMode
import com.sumi.core.designsystem.theme.SumiSpacing

/** Settings (M2/M5): privacy, theme, data management (export/delete). */
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeSelected: (ThemeMode) -> Unit,
    onDeleteAllData: () -> Unit = {},
    onExportData: () -> Unit = {},
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
                Text(
                    text = mode.name.lowercase().replaceFirstChar(Char::uppercase),
                    modifier = Modifier.padding(start = SumiSpacing.sm),
                )
            }
        }
        Spacer(Modifier.height(SumiSpacing.xl))
        Text(text = "Data", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(SumiSpacing.sm))
        Button(onClick = onExportData, modifier = Modifier.fillMaxWidth()) { Text("Export data") }
        Spacer(Modifier.height(SumiSpacing.sm))
        TextButton(onClick = onDeleteAllData, modifier = Modifier.fillMaxWidth()) {
            Text("Delete all data")
        }
    }
}
