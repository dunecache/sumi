package com.sumi.feature.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sumi.core.database.dao.ApplicationDao
import com.sumi.data.categories.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One row in the category manager. */
data class CategoryRow(
    val id: String,
    val name: String,
    val colorToken: String,
    val isUserCreated: Boolean,
    val appCount: Int,
)

data class CategoriesUiState(
    val isLoading: Boolean = true,
    val categories: List<CategoryRow> = emptyList(),
    /** Applications with no assignment; residual bucket, shown as information. */
    val uncategorizedCount: Int = 0,
)

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
    applicationDao: ApplicationDao,
) : ViewModel() {

    val uiState: StateFlow<CategoriesUiState> = combine(
        categoryRepository.observeCategories(),
        applicationDao.observeInstalled(),
    ) { categories, installed ->
        // Counts describe *installed* apps — the ones the user can act on right now.
        val perId = installed.mapNotNull { it.categoryId }.groupingBy { it }.eachCount()
        CategoriesUiState(
            isLoading = false,
            categories = categories.map {
                CategoryRow(it.id, it.name, it.colorToken, it.isUserCreated, perId[it.id] ?: 0)
            },
            uncategorizedCount = installed.count { it.categoryId == null },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoriesUiState())

    fun create(name: String, colorToken: String) {
        viewModelScope.launch { categoryRepository.create(name, colorToken) }
    }

    fun rename(id: String, name: String) {
        viewModelScope.launch { categoryRepository.rename(id, name) }
    }

    fun setColor(id: String, colorToken: String) {
        viewModelScope.launch { categoryRepository.setColor(id, colorToken) }
    }

    /** Moves the category one slot up (delta -1) or down (delta +1). */
    fun move(id: String, delta: Int) {
        val ids = uiState.value.categories.map { it.id }.toMutableList()
        val from = ids.indexOf(id)
        val to = from + delta
        if (from < 0 || to < 0 || to >= ids.size) return
        val tmp = ids[from]
        ids[from] = ids[to]
        ids[to] = tmp
        viewModelScope.launch { categoryRepository.reorder(ids) }
    }

    /**
     * Deletes a category and moves its apps to [reassignTo] (null = uncategorized).
     * User assignments stay user assignments; automatic classifications keep following
     * their (now absent) source — re-classification by defaults may reclaim them.
     */
    fun delete(id: String, reassignTo: String?) {
        viewModelScope.launch { categoryRepository.delete(id, reassignTo) }
    }
}
