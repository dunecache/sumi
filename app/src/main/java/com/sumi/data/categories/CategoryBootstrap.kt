package com.sumi.data.categories

import com.sumi.core.common.AppDispatchers
import com.sumi.domain.usage.UsageRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * App-start bootstrap: seed categories, recolor untouched seeded rows, apply default
 * classification to never-classified rows, then backfill the install library — every
 * launchable installed app gets an `applications` row even before it produced any
 * usage. Runs once per process, off the main thread; failures are non-fatal (rows and
 * categories appear on next launch at the latest).
 */
@Singleton
class CategoryBootstrap @Inject constructor(
    private val repository: CategoryRepository,
    private val usageRepository: UsageRepository,
    private val dispatchers: AppDispatchers,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    fun run() {
        scope.launch {
            runCatching {
                repository.ensureSeeded()
                repository.syncSeededColors()
                repository.classifyPendingAssignments()
                usageRepository.backfillInstalledApps()
            }
        }
    }
}
