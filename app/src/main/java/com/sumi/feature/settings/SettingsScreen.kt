package com.sumi.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.sumi.core.datastore.ThemeMode
import com.sumi.core.designsystem.component.SumiSectionHeader
import com.sumi.core.designsystem.theme.SumiSpacing

/** Settings (M2/M5): privacy, theme, apps library, data management (export/delete), diagnostics. */
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeSelected: (ThemeMode) -> Unit,
    onDeleteAllData: () -> Unit = {},
    onExportData: () -> Unit = {},
    diagnosticsLines: List<String> = emptyList(),
    showSystemApps: Boolean = false,
    onShowSystemAppsChange: (Boolean) -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(SumiSpacing.screenHorizontal),
    ) {
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
        SumiSectionHeader("THEME")
        Spacer(Modifier.height(SumiSpacing.sm))
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
        SumiSectionHeader("APPS")
        Spacer(Modifier.height(SumiSpacing.sm))
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = SumiSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = SumiSpacing.md)) {
                Text("Show system apps", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(SumiSpacing.xs))
                Text(
                    text = "Hidden from the Apps library by default. Totals and exclusions stay the same either way.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = showSystemApps,
                onCheckedChange = onShowSystemAppsChange,
            )
        }
        Spacer(Modifier.height(SumiSpacing.xl))
        SumiSectionHeader("DATA")
        Spacer(Modifier.height(SumiSpacing.sm))
        Button(onClick = onExportData, modifier = Modifier.fillMaxWidth()) { Text("Export data") }
        Spacer(Modifier.height(SumiSpacing.sm))
        // Destructive action: the error role is reserved for data deletion confirmation.
        TextButton(
            onClick = onDeleteAllData,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Text("Delete all data")
        }
        Spacer(Modifier.height(SumiSpacing.xl))
        SumiSectionHeader("DIAGNOSTICS (ON THIS DEVICE)")
        Spacer(Modifier.height(SumiSpacing.sm))
        Text(
            text = "If Today shows nothing, these tell you why: permission off, or no data stored yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(SumiSpacing.sm))
        diagnosticsLines.forEach { line ->
            Text(
                text = "• $line",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(SumiSpacing.lg))
    }
}
