package com.sumi.data.categories

import com.sumi.core.common.AppDispatchers
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * App-start bootstrap for the category system: seed defaults, then apply default
 * classification to never-classified rows (e.g. after upgrade to schema 2). Runs once
 * per process, off the main thread; failures are non-fatal (categories appear on next
 * launch at the latest).
 */
@Singleton
class CategoryBootstrap @Inject constructor(
    private val repository: CategoryRepository,
    private val dispatchers: AppDispatchers,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    fun run() {
        scope.launch {
            runCatching {
                repository.ensureSeeded()
                repository.syncSeededColors()
                repository.classifyPendingAssignments()
            }
        }
    }
}
