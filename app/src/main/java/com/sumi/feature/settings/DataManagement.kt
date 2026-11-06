package com.sumi.feature.settings

import android.content.Context
import com.sumi.core.common.AppDispatchers
import com.sumi.core.database.SumiDatabase
import com.sumi.core.datastore.PreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/** Local JSON export (M5). Export never leaves the device unless the user shares the file. */
@Serializable
data class SumiExport(
    val version: Int = 1,
    val dailyUsage: List<com.sumi.core.database.entity.DailyUsage> = emptyList(),
    val dailyAppUsage: List<com.sumi.core.database.entity.DailyAppUsage> = emptyList(),
    val hourlyUsage: List<com.sumi.core.database.entity.HourlyUsage> = emptyList(),
)

@Singleton
class DataManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: SumiDatabase,
    private val preferences: PreferencesRepository,
    private val dispatchers: AppDispatchers,
) {
    /** Deletes all rows and preferences, then returns to onboarding. */
    suspend fun deleteAllData() = withContext(dispatchers.io) {
        db.clearAllTables()
        preferences.clearAll()
    }
}

/** Placeholder copy for in-app privacy statement. */
object PrivacyCopy {
    const val STATEMENT = "Sumi processes and stores your usage on this device only. " +
        "There is no account, no analytics, and no network access."
}
