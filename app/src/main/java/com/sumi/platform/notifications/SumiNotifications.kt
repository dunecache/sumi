package com.sumi.platform.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Notification scaffolding (M4). Defaults: low-to-default importance, no sound for gentle reminders. */
@Singleton
class SumiNotifications @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_REMINDERS) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_REMINDERS,
                    "Reminders",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = "Gentle reminders you set" },
            )
        }
        if (manager.getNotificationChannel(CHANNEL_FOCUS) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_FOCUS, "Focus", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Focus session updates"
                },
            )
        }
    }

    private companion object {
        const val CHANNEL_REMINDERS = "reminders"
        const val CHANNEL_FOCUS = "focus"
    }
}
