package com.sumi.feature.settings

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.sumi.core.common.AppDispatchers
import com.sumi.core.database.SumiDatabase
import com.sumi.core.datastore.PreferencesRepository
import com.sumi.domain.common.Clock
import com.sumi.domain.common.DateKey
import com.sumi.domain.common.TimeZoneProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

/** Local export payload (M5). Export never leaves the device unless the user shares the file. */
data class SumiExport(
    val version: Int = 1,
    val dailyUsage: List<com.sumi.core.database.entity.DailyUsage> = emptyList(),
    val dailyAppUsage: List<com.sumi.core.database.entity.DailyAppUsage> = emptyList(),
    val hourlyUsage: List<com.sumi.core.database.entity.HourlyUsage> = emptyList(),
)

/** Actions on the user's stored history. All of it stays on this device. */
@Singleton
class DataManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: SumiDatabase,
    private val preferences: PreferencesRepository,
    private val dispatchers: AppDispatchers,
    private val clock: Clock,
    private val timeZone: TimeZoneProvider,
) {
    /** Deletes all rows and preferences, then returns to onboarding. */
    suspend fun deleteAllData() = withContext(dispatchers.io) {
        db.clearAllTables()
        preferences.clearAll()
    }

    /**
     * Writes the full stored history as one flat CSV and returns the file's display name.
     * API 29+ writes through MediaStore (no permissions, lands in Downloads); older
     * versions write to the app-scoped external downloads directory (no permission,
     * no public files). Rows never leave the device.
     */
    suspend fun exportCsv(): String = withContext(dispatchers.io) {
        val daily = db.dailyUsageDao().exportAll()
        val perApp = db.dailyAppUsageDao().exportAll()
        val hourly = db.hourlyUsageDao().exportAll()
        val csv = buildExportCsv(daily, perApp, hourly)
        val today = DateKey.of(clock.nowUtcMs(), timeZone.currentZone())
        val fileName = "sumi-export-$today.csv"
        writeToDownloads(fileName, csv)
        fileName
    }

    private fun buildExportCsv(
        daily: List<com.sumi.core.database.entity.DailyUsage>,
        perApp: List<com.sumi.core.database.entity.DailyAppUsage>,
        hourly: List<com.sumi.core.database.entity.HourlyUsage>,
    ): String {
        fun row(vararg fields: String) = fields.joinToString(",") + "\n"
        val out = StringBuilder(64 * 1024)
        out.append("section,dateKey,packageName,hour,durationMs,totalDurationMs,source,lastCollectedAt\n")
        daily.forEach { row ->
            out.append(
                row(
                    "daily_usage", row.dateKey, "", "",
                    row.totalDurationMs.toString(), row.totalDurationMs.toString(),
                    row.source, row.lastCollectedAt.toString(),
                ),
            )
        }
        perApp.forEach { row ->
            out.append(
                row(
                    "daily_app_usage", row.dateKey, escapeCsv(row.packageName), "",
                    row.durationMs.toString(), "", row.source, "",
                ),
            )
        }
        hourly.forEach { row ->
            out.append(
                row(
                    "hourly_usage", row.dateKey, escapeCsv(row.packageName), row.hour.toString(),
                    row.durationMs.toString(), "", "", "",
                ),
            )
        }
        return out.toString()
    }

    /** RFC 4180 quoting for the package column (package names never contain commas, but be safe). */
    private fun escapeCsv(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    private fun writeToDownloads(fileName: String, contents: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(android.provider.MediaStore.DownloadColumns.DISPLAY_NAME, fileName)
                put(MediaStore.DownloadColumns.MIME_TYPE, "text/csv")
                put(MediaStore.DownloadColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Could not create the export file in Downloads")
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(contents.toByteArray(Charsets.UTF_8))
            } ?: error("Could not write the export file")
        } else {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: context.filesDir
            java.io.File(dir, fileName).outputStream().use { it.write(contents.toByteArray(Charsets.UTF_8)) }
        }
    }
}

/** Placeholder copy for in-app privacy statement. */
object PrivacyCopy {
    const val STATEMENT = "Sumi processes and stores your usage on this device only. " +
        "There is no account, no analytics, and no network access."
}
