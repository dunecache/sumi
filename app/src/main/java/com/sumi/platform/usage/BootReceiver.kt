package com.sumi.platform.usage

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.sumi.domain.usage.RebootBoundarySource
import com.sumi.domain.usage.RebootDetection
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Records reboot instants so reconstruction (R5) never extends sessions across reboots.
 * Two independent sources are unioned, best case wins:
 *  - [BootReceiver] on every `BOOT_COMPLETED` (delivered when the app/process is alive
 *    and the OEM does not withhold it);
 *  - the monotonic clock — `SystemClock.elapsedRealtime()` resets on reboot, so a
 *    regression since the last observation reveals a reboot even when the broadcast
 *    never arrived (force-stopped app, direct-boot gap, aggressive OEM).
 * [PlatformRebootSource] exposes the union; reboot boundaries are wall-clock instants.
 */
@Singleton
class PlatformRebootSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : RebootBoundarySource {

    override fun rebootInstantsUtcMs(): List<Long> {
        detectRebootFromMonotonicClock()
        return prefs(context).getString(KEY, "")
            ?.split(",")
            ?.mapNotNull { it.toLongOrNull() }
            ?.sorted()
            ?.distinct()
            ?: emptyList()
    }

    private fun detectRebootFromMonotonicClock() {
        val p = prefs(context)
        val storedElapsed = p.getLong(KEY_LAST_ELAPSED, -1L)
        val storedWall = p.getLong(KEY_LAST_WALL, 0L)
        val nowElapsed = SystemClock.elapsedRealtime()
        val nowWall = System.currentTimeMillis()
        val bootInstant = RebootDetection.detectBootInstant(
            storedElapsedMs = storedElapsed,
            storedWallMs = storedWall,
            currentElapsedMs = nowElapsed,
            currentWallMs = nowWall,
        )
        p.edit()
            .putLong(KEY_LAST_ELAPSED, nowElapsed)
            .putLong(KEY_LAST_WALL, nowWall)
            .apply()
        if (bootInstant != null) {
            recordBoot(context, bootInstant)
        }
    }

    companion object {
        const val FILE = "sumi_boot"
        const val KEY = "reboot_instants"
        const val KEY_LAST_ELAPSED = "last_elapsed"
        const val KEY_LAST_WALL = "last_wall"

        private fun prefs(context: Context) =
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

        fun recordBoot(context: Context, instantUtcMs: Long) {
            val prefs = prefs(context)
            val existing = prefs.getString(KEY, "")?.split(",")?.mapNotNull { it.toLongOrNull() } ?: emptyList()
            val updated = (existing + instantUtcMs).sorted().distinct().takeLast(100)
            prefs.edit().putString(KEY, updated.joinToString(",")).commit()
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            PlatformRebootSource.recordBoot(context.applicationContext, System.currentTimeMillis())
        }
    }
}
