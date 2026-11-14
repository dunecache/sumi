package com.sumi.feature.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.mergeDescendants
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sumi.core.designsystem.theme.SumiSpacing
import com.sumi.core.designsystem.theme.categoryColorOrNeutral
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check

/**
 * Color tokens rotated through for inline-created categories. Primitive names from the
 * canonical charts palette; `stone-1` (the residual) and `constant` (reserved) never
 * appear. The full manager remains the place to pick a color deliberately.
 */
internal val INLINE_CREATE_TOKENS = listOf(
    "matcha-4",
    "mist",
    "clay-2",
    "wisteria",
    "ochre",
    "method",
    "matcha-2",
)

/** Deterministic token for the Nth inline-created category (wraps around the palette). */
internal fun nextInlineCreateToken(existingCount: Int): String =
    INLINE_CREATE_TOKENS[existingCount.mod(INLINE_CREATE_TOKENS.size)]

/** One pickable category. Uncategorized is implicit — it is not a category. */
data class CategoryPickerOption(
    val id: String,
    val name: String,
    val colorToken: String,
)

/**
 * THE category picker: every category plus the Uncategorized residual, and — when the
 * caller opts in — an inline "New category…" row that creates one right here and hands
 * the name back (create + assign happen in the caller, in one flow).
 *
 * Used by the Apps library (bulk assign and single reassign) and the app detail screen.
 * User choices here are assignments, so they always win over default classification.
 *
 * @param title e.g. "Move 5 apps to…" or "Category".
 * @param showSelection render the check on the currently-assigned row (single reassign).
 * @param selectedId the current assignment when [showSelection] (null = uncategorized).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CategoryPickerSheet(
    title: String,
    options: List<CategoryPickerOption>,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
    subtitle: String? = null,
    showSelection: Boolean = false,
    selectedId: String? = null,
    onCreateCategory: ((name: String) -> Unit)? = null,
) {
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SumiSpacing.screenHorizontal),
        ) {
            Text(text = title, style = MaterialTheme.typography.headlineSmall)
            if (subtitle != null) {
                Spacer(Modifier.height(SumiSpacing.xs))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(SumiSpacing.lg))
            PickerRow(
                name = "Uncategorized",
                colorToken = "stone-1",
                selected = showSelection && selectedId == null,
                onClick = { onSelect(null) },
            )
            options.forEach { option ->
                PickerRow(
                    name = option.name,
                    colorToken = option.colorToken,
                    selected = showSelection && selectedId == option.id,
                    onClick = { onSelect(option.id) },
                )
            }

            if (onCreateCategory != null) {
                Spacer(Modifier.height(SumiSpacing.sm))
                if (!creating) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(SumiSpacing.minTouchTarget)
                            .clickable(
                                onClickLabel = "Create a new category",
                                onClick = { creating = true },
                            )
                            .semantics(mergeDescendants = true) {
                                contentDescription = "Create a new category"
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "New category…",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = SumiSpacing.md),
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { if (it.length <= MAX_NEW_CATEGORY_NAME) newName = it },
                        label = { Text("New category name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(SumiSpacing.sm))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { creating = false; newName = "" }) { Text("Cancel") }
                        TextButton(
                            onClick = { onCreateCategory(newName.trim()) },
                            enabled = newName.isNotBlank(),
                        ) { Text("Create") }
                    }
                }
            }
            Spacer(Modifier.height(SumiSpacing.xl))
        }
    }
}

@Composable
private fun PickerRow(
    name: String,
    colorToken: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(SumiSpacing.minTouchTarget)
            .clickable(onClickLabel = "Select $name", onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "Move apps to $name" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(PICKER_DOT)
                .clip(CircleShape)
                .background(categoryColorOrNeutral(colorToken)),
        )
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .weight(1f)
                .padding(start = SumiSpacing.md),
            maxLines = 1,
        )
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private const val MAX_NEW_CATEGORY_NAME = 40
private val PICKER_DOT = 24.dp
