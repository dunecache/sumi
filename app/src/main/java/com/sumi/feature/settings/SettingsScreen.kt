package com.sumi.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.Surface
import com.sumi.core.datastore.ThemeMode
import com.sumi.core.designsystem.component.DiagnosticRow
import com.sumi.core.designsystem.component.ProgressModule
import com.sumi.core.designsystem.component.StatusIndicator
import com.sumi.core.designsystem.component.StatusTone
import com.sumi.core.designsystem.component.SumiPrimaryButton
import com.sumi.core.designsystem.component.SumiSectionHeader
import com.sumi.core.designsystem.component.SumiTextLink
import com.sumi.core.designsystem.component.WhatSumiDoesNotDoSheet
import com.sumi.core.designsystem.theme.SumiSpacing

/**
 * Settings (M2/M5): privacy, theme, apps library, data management (export/delete),
 * diagnostics. Sections render flat on the ground; sections are separated by rhythm,
 * not containers. The destructive action sits ≥ 24 dp from every other control and
 * opens a confirmation dialog (reference standard §10.4).
 */
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeSelected: (ThemeMode) -> Unit,
    onDeleteAllData: () -> Unit = {},
    exportState: ExportState = ExportState.Idle,
    onExportConfirmed: () -> Unit = {},
    onExportDismissed: () -> Unit = {},
    diagnostics: List<DiagnosticItem> = emptyList(),
    showSystemApps: Boolean = false,
    onShowSystemAppsChange: (Boolean) -> Unit = {},
) {
    var showTrustSheet by remember { mutableStateOf(false) }
    var showExportSheet by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Terminal export states close the sheet; the outcome shows inline in DATA (§10.4).
    androidx.compose.runtime.LaunchedEffect(exportState) {
        if (exportState is ExportState.Saved || exportState is ExportState.Failed) {
            showExportSheet = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(SumiSpacing.screenHorizontal),
    ) {
        Spacer(Modifier.height(SumiSpacing.lg))
        // Large title in content below the subscreen bar (sans, system voice) — §9.3.
        Text(text = "Settings", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(SumiSpacing.md))
        // One-line pointer to the trust sheet (F-11); the full statement lives there.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Everything stays on this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            SumiTextLink(
                text = "See what Sumi does not do",
                onClick = { showTrustSheet = true },
            )
        }
        Spacer(Modifier.height(SumiSpacing.xl))
        SumiSectionHeader("THEME")
        Spacer(Modifier.height(SumiSpacing.sm))
        ThemeMode.entries.forEach { mode ->
            val selected = themeMode == mode
            val label = mode.name.lowercase().replaceFirstChar(Char::uppercase)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = SumiSpacing.rowMinHeight)
                    .clickable(onClick = { onThemeSelected(mode) })
                    .padding(vertical = SumiSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected, onClick = { onThemeSelected(mode) })
                Text(
                    text = label,
                    modifier = Modifier.padding(start = SumiSpacing.sm),
                )
            }
        }
        Spacer(Modifier.height(SumiSpacing.xl))
        SumiSectionHeader("APPS")
        Spacer(Modifier.height(SumiSpacing.sm))
        // Switch row per reference standard §9.7: switch aligned to the title line,
        // full row tappable.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = SumiSpacing.minTouchTarget)
                .clickable(onClick = { onShowSystemAppsChange(!showSystemApps) })
                .semantics(mergeDescendants = true) {
                    role = androidx.compose.ui.semantics.Role.Switch
                    stateDescription = if (showSystemApps) "on" else "off"
                }
                .padding(vertical = SumiSpacing.xs),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f).padding(end = SumiSpacing.md)) {
                Text("Show system apps", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(SumiSpacing.xs))
                Text(
                    text = "Hidden from the Apps library by default. Totals and exclusions stay the same either way.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = showSystemApps,
                onCheckedChange = onShowSystemAppsChange,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(Modifier.height(SumiSpacing.xl))
        SumiSectionHeader("DATA")
        Spacer(Modifier.height(SumiSpacing.sm))
        SumiPrimaryButton(label = "Export data", onClick = { showExportSheet = true })
        when (exportState) {
            is ExportState.Saved -> {
                Spacer(Modifier.height(SumiSpacing.sm))
                StatusIndicator(
                    text = "Saved to Downloads.",
                    tone = StatusTone.Affirmative,
                )
            }
            ExportState.Failed -> {
                Spacer(Modifier.height(SumiSpacing.sm))
                StatusIndicator(
                    text = "Export didn't finish. Try again.",
                    tone = StatusTone.Attention,
                )
            }
            else -> Unit
        }
        // Destructive action: unmistakably separated from the export control (≥ 24 dp, §10.4).
        Spacer(Modifier.height(SumiSpacing.xxl))
        SumiTextLink(
            text = "Delete all data",
            onClick = { showDeleteDialog = true },
            destructive = true,
            centered = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(SumiSpacing.xl))
        SumiSectionHeader("DIAGNOSTICS (ON THIS DEVICE)")
        Spacer(Modifier.height(SumiSpacing.sm))
        Text(
            text = "If Today shows nothing, these tell you why: permission off, or no data stored yet.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(SumiSpacing.sm))
        diagnostics.forEach { item ->
            DiagnosticRow(
                label = item.label,
                value = item.value,
                tone = item.tone?.let {
                    when (it) {
                        DiagnosticTone.Affirmative -> StatusTone.Affirmative
                        DiagnosticTone.Neutral -> StatusTone.Neutral
                    }
                },
            )
        }
        Spacer(Modifier.height(SumiSpacing.lg))
    }

    if (showTrustSheet) {
        WhatSumiDoesNotDoSheet(onDismiss = { showTrustSheet = false })
    }

    if (showExportSheet) {
        ExportSheet(
            exportState = exportState,
            onExport = onExportConfirmed,
            onDismiss = {
                showExportSheet = false
                onExportDismissed()
            },
        )
    }

    if (showDeleteDialog) {
        DeleteAllDataDialog(
            onCancel = { showDeleteDialog = false },
            onConfirm = {
                showDeleteDialog = false
                onDeleteAllData()
            },
        )
    }
}

/** Export flow sheet (§10.4): body, ProgressModule while working, Export/Cancel. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ExportSheet(
    exportState: ExportState,
    onExport: () -> Unit,
    onDismiss: () -> Unit,
) {
    val working = exportState == ExportState.Working
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = { if (!working) onDismiss() },
        sheetState = rememberModalBottomSheetState(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SumiSpacing.screenHorizontal)
                .padding(bottom = SumiSpacing.xxl),
        ) {
            Text(
                text = "Export data",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(SumiSpacing.md))
            Text(
                text = "A CSV of your full usage history will be saved to this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(SumiSpacing.xl))
            if (working) {
                ProgressModule(progress = null, liveLine = "Exporting…")
                Spacer(Modifier.height(SumiSpacing.xl))
            }
            SumiPrimaryButton(
                label = if (working) "Exporting…" else "Export",
                onClick = onExport,
                enabled = !working,
            )
            Spacer(Modifier.height(SumiSpacing.sm))
            SumiTextLink(
                text = "Cancel",
                onClick = onDismiss,
                centered = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Destructive confirmation dialog (§10.4): names the consequence, stacked full-width actions. */
@Composable
private fun DeleteAllDataDialog(
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(onDismissRequest = onCancel) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(SumiSpacing.screenHorizontal)
                    .padding(vertical = SumiSpacing.xl),
            ) {
                Text(
                    text = "Delete all data?",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(SumiSpacing.sm))
                Text(
                    text = "This permanently erases all usage history stored on this device. " +
                        "Settings stay. This cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(SumiSpacing.xl))
                Button(
                    onClick = onCancel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = SumiSpacing.minTouchTarget),
                    shape = androidx.compose.foundation.shape.CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = androidx.compose.ui.graphics.Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Text(text = "Cancel", style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(SumiSpacing.sm))
                Button(
                    onClick = onConfirm,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = SumiSpacing.minTouchTarget),
                    shape = androidx.compose.foundation.shape.CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text(text = "Delete", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
