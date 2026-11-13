package com.sumi.feature.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sumi.core.designsystem.component.LoadingState
import com.sumi.core.designsystem.theme.SumiSpacing
import com.sumi.core.designsystem.theme.categoryColorOrNeutral
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp

/** Color tokens a user can pick for a category. Neutral grey stays available for low-key categories. */
internal val CATEGORY_COLOR_TOKENS = listOf(
    "categoryColor1",
    "categoryColor2",
    "categoryColor3",
    "categoryColor4",
    "categoryColor5",
    "categoryColor6",
    "categoryColor7",
    "categoryNeutral",
)

/**
 * Category manager: rename, recolor, reorder, add, or delete categories. Deletion always
 * reassigns member apps (chosen target, default uncategorized). Uncategorized is a view,
 * not a category — it cannot be created, renamed, recolored, or deleted.
 */
@Composable
fun CategoryManagerScreen(
    viewModel: CategoriesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CategoryRow?>(null) }

    if (state.isLoading) {
        LoadingState()
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(SumiSpacing.screenHorizontal)
            .padding(top = SumiSpacing.lg),
    ) {
        Text(text = "Categories", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(SumiSpacing.xs))
        Text(
            text = "One category per app, never double-counted. Uncategorized: ${state.uncategorizedCount} apps.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(SumiSpacing.lg))
        Column(verticalArrangement = Arrangement.spacedBy(SumiSpacing.xs)) {
            state.categories.forEachIndexed { index, category ->
                CategoryManagerRow(
                    category = category,
                    canMoveUp = index > 0,
                    canMoveDown = index < state.categories.size - 1,
                    onMove = { delta -> viewModel.move(category.id, delta) },
                    onClick = { editing = category },
                )
            }
        }
        Spacer(Modifier.height(SumiSpacing.lg))
        Button(
            onClick = { adding = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Add category")
        }
        Spacer(Modifier.height(SumiSpacing.xl))
    }

    if (adding) {
        CategoryEditSheet(
            title = "New category",
            initialName = "",
            initialColor = CATEGORY_COLOR_TOKENS.first(),
            isCreate = true,
            appCount = 0,
            otherCategories = state.categories,
            onDismiss = { adding = false },
            onSave = { name, color ->
                viewModel.create(name, color)
                adding = false
            },
        )
    }

    editing?.let { category ->
        CategoryEditSheet(
            title = "Edit category",
            initialName = category.name,
            initialColor = category.colorToken,
            isCreate = false,
            appCount = category.appCount,
            otherCategories = state.categories.filterNot { it.id == category.id },
            onDismiss = { editing = null },
            onSave = { name, color ->
                viewModel.rename(category.id, name)
                viewModel.setColor(category.id, color)
                editing = null
            },
            onDelete = { target ->
                viewModel.delete(category.id, target)
                editing = null
            },
        )
    }
}

@Composable
private fun CategoryManagerRow(
    category: CategoryRow,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (Int) -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Edit category ${category.name}" }
            .padding(vertical = SumiSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SumiSpacing.sm),
    ) {
        ColorDot(colorToken = category.colorToken, size = ColorDotLarge)
        Column(Modifier.weight(1f)) {
            Text(
                text = category.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
            )
            Text(
                text = "${category.appCount} apps",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { onMove(-1) }, enabled = canMoveUp) {
            Icon(
                Icons.Filled.KeyboardArrowUp,
                contentDescription = "Move ${category.name} up",
            )
        }
        IconButton(onClick = { onMove(1) }, enabled = canMoveDown) {
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = "Move ${category.name} down",
            )
        }
    }
}

@Composable
internal fun ColorDot(colorToken: String, size: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(categoryColorOrNeutral(colorToken)),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryEditSheet(
    title: String,
    initialName: String,
    initialColor: String,
    isCreate: Boolean,
    appCount: Int,
    otherCategories: List<CategoryRow>,
    onDismiss: () -> Unit,
    onSave: (name: String, color: String) -> Unit,
    onDelete: ((reassignTo: String?) -> Unit)? = null,
) {
    var name by remember { mutableStateOf(initialName) }
    var color by remember { mutableStateOf(initialColor) }
    var showReassign by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf<String?>(null) } // null = Uncategorized
    var targetExpanded by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SumiSpacing.screenHorizontal),
        ) {
            Text(text = title, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(SumiSpacing.lg))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(SumiSpacing.lg))
            Text(
                text = "COLOR",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(SumiSpacing.sm))
            Row(
                horizontalArrangement = Arrangement.spacedBy(SumiSpacing.md),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            ) {
                CATEGORY_COLOR_TOKENS.forEach { token ->
                    val selected = token == color
                    Box(
                        modifier = Modifier
                            .size(ColorSwatch)
                            .clip(CircleShape)
                            .background(categoryColorOrNeutral(token))
                            .let { swatch ->
                                if (selected) {
                                    swatch.border(2.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                                } else {
                                    swatch
                                }
                            }
                            .clickableRoleButton(
                                description = if (selected) "Selected color" else "Use this color",
                                onClick = { color = token },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }
            }

            if (showReassign && onDelete != null) {
                Spacer(Modifier.height(SumiSpacing.xl))
                Text(
                    text = "Move $appCount apps somewhere first:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(SumiSpacing.sm))
                ExposedDropdownMenuBox(
                    expanded = targetExpanded,
                    onExpandedChange = { targetExpanded = !targetExpanded },
                ) {
                    OutlinedTextField(
                        value = otherCategories.firstOrNull { it.id == target }?.name ?: "Uncategorized",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Move apps to") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = targetExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true),
                    )
                    ExposedDropdownMenu(
                        expanded = targetExpanded,
                        onDismissRequest = { targetExpanded = false },
                    ) {
                        ReassignMenuItem(label = "Uncategorized", selected = target == null) {
                            target = null
                            targetExpanded = false
                        }
                        otherCategories.forEach { option ->
                            ReassignMenuItem(label = option.name, selected = target == option.id) {
                                target = option.id
                                targetExpanded = false
                            }
                        }
                    }
                }
                Spacer(Modifier.height(SumiSpacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { showReassign = false }) { Text("Keep category") }
                    TextButton(onClick = { onDelete(target) }) { Text("Delete") }
                }
            }

            Spacer(Modifier.height(SumiSpacing.lg))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SumiSpacing.sm, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!isCreate && onDelete != null && !showReassign) {
                    TextButton(onClick = { showReassign = true }) { Text("Delete…") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
                TextButton(
                    onClick = { onSave(name.trim(), color) },
                    enabled = name.isNotBlank(),
                ) { Text(if (isCreate) "Create" else "Save") }
            }
            Spacer(Modifier.height(SumiSpacing.xl))
        }
    }
}

@Composable
private fun ReassignMenuItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = onClick,
        leadingIcon = if (selected) {
            { Icon(Icons.Filled.Check, contentDescription = "Selected") }
        } else {
            null
        },
    )
}

/** Clickable role-button semantics for the color swatches. */
private fun Modifier.clickableRoleButton(
    description: String,
    onClick: () -> Unit,
): Modifier = this.semantics(mergeDescendants = true) {
    role = androidx.compose.ui.semantics.Role.RadioButton
    stateDescription = description
}.clickable(onClick = onClick).semantics { contentDescription = description }

private val ColorDotLarge = 28.dp
private val ColorSwatch = 32.dp
