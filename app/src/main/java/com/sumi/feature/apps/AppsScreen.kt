package com.sumi.feature.apps

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.sumi.core.designsystem.component.EmptyState
import com.sumi.core.designsystem.component.LoadingState
import com.sumi.core.designsystem.theme.SumiSpacing

/** Apps: searchable library (M2). Row shows icon placeholder, name, category chip. */
@Composable
fun AppsScreen(
    viewModel: AppsViewModel = hiltViewModel(),
    onOpenAppDetail: (packageName: String) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val query by viewModel.query.collectAsState()
    Column(Modifier.fillMaxSize().padding(SumiSpacing.screenHorizontal)) {
        Spacer(Modifier.height(SumiSpacing.lg))
        Text(text = "Apps", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(SumiSpacing.md))
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            label = { Text("Search apps") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(Modifier.height(SumiSpacing.md))
        when {
            state.isLoading -> LoadingState()
            state.apps.isEmpty() -> EmptyState(
                title = "No apps yet",
                message = "Sumi starts learning your day from now. Come back later.",
            )
            else -> {
                val filtered = viewModel.filtered(state.apps)
                if (filtered.isEmpty()) {
                    EmptyState(
                        title = "No matches",
                        message = "Try a different search.",
                    )
                } else {
                    LazyColumn(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(SumiSpacing.sm)) {
                        items(filtered) { app ->
                            androidx.compose.material3.ListItem(
                                headlineContent = { Text(app.displayName) },
                                supportingContent = { Text(app.packageName) },
                            )
                        }
                    }
                }
            }
        }
    }
}
