package com.sumi.platform.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sumi.core.datastore.PreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * The gentle daily reminder (§16.9). Off by default; invites once a day at the time the
 * user picks. Tap opens the app; per §16, posting is enough — no nudge storms.
 */
@HiltWorker
class FocusReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val notifications: SumiNotifications,
    private val preferences: PreferencesRepository,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (!preferences.focusReminderEnabled.first()) return Result.success() // silently dropped if user re-disabled
        notifications.postFocusReminder()
        return Result.success()
    }
}

@Singleton
class FocusReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun scheduleDaily(minuteOfDay: Int) {
        val request = PeriodicWorkRequestBuilder<FocusReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelayToNext(minuteOfDay), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    private companion object {
        const val WORK_NAME = "sumi_focus_reminder_daily"
    }
}

/**
 * Initial delay until the next occurrence of [minuteOfDay] (local minutes after midnight).
 * Wall-clock based; assumes the caller recomputes on every (re)schedule — a periodic
 * worker enqueued today drifts slowly across DST, which is the accepted trade of
 * WorkManager-over-AlarmManager for a gentle, non-urgent nudge.
 *
 * Extracted as a pure function so the day-boundary rule is unit-tested.
 */
internal fun initialDelayToNext(minuteOfDay: Int, now: ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault())): Long {
    val safe = minuteOfDay.coerceIn(0, 23 * 60 + 59)
    val today = now.toLocalDate().atTime(safe / 60, safe % 60).atZone(now.zone)
    val effective = if (today.isAfter(now)) today else today.plusDays(1)
    return java.time.Duration.between(now, effective).toMillis()
}
