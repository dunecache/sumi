package com.sumi.platform.usage

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sumi.domain.usage.RebootBoundarySource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Records reboot instants so reconstruction (R5) never extends sessions across reboots.
 * [BootReceiver] runs on every `BOOT_COMPLETED` and stores the instant synchronously;
 * [PlatformRebootSource] exposes the recorded instants to the repository.
 */
@Singleton
class PlatformRebootSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : RebootBoundarySource {

    override fun rebootInstantsUtcMs(): List<Long> =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY, "")
            ?.split(",")
            ?.mapNotNull { it.toLongOrNull() }
            ?.sorted()
            ?.distinct()
            ?: emptyList()

    companion object {
        const val FILE = "sumi_boot"
        const val KEY = "reboot_instants"

        fun recordBoot(context: Context, instantUtcMs: Long) {
            val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
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
