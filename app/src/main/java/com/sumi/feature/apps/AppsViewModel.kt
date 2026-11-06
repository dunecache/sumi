package com.sumi.feature.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sumi.core.database.dao.ApplicationDao
import com.sumi.core.database.entity.Application
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class AppsUiState(
    val query: String = "",
    val apps: List<Application> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class AppsViewModel @Inject constructor(
    private val applicationDao: ApplicationDao,
) : ViewModel() {

    val uiState: StateFlow<AppsUiState> = applicationDao.observeInstalled()
        .map { apps -> AppsUiState(apps = apps, isLoading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppsUiState())

    private val _query = kotlinx.coroutines.flow.MutableStateFlow("")
    val query: StateFlow<String> = _query

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun filtered(apps: List<Application>): List<Application> {
        val q = _query.value.trim()
        if (q.isEmpty()) return apps
        return apps.filter { it.displayName.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true) }
    }
}
