package com.sumi.platform.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.sumi.MainActivity
import com.sumi.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Notification scaffolding (M4). Defaults: low-to-default importance, no sound for gentle reminders. */
@Singleton
class SumiNotifications @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * On API 33+ POST_NOTIFICATIONS is a runtime grant the user controls; callers check
     * before posting. Returns true on older platforms (no such permission).
     */
    fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }

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

    /**
     * Low-importance ongoing notification used by the Focus foreground service (§16.5).
     * Tapping it returns to the session (via [MainActivity]'s `open_focus` extra).
     * Carries the calm countdown text; never a sound, never an alarm tone.
     */
    /** The ongoing session notification is quiet; callers rely on it for the FGS. */
    fun buildFocusNotification(
        remainingText: String,
        exits: Int,
        sessionId: Long,
    ): Notification {
        createChannels()
        val openApp = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(EXTRA_OPEN_FOCUS, true)
            putExtra(EXTRA_FOCUS_SESSION_ID, sessionId)
        }
        val pi = PendingIntent.getActivity(
            context,
            0,
            openApp,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, CHANNEL_FOCUS)
            .setContentTitle("Focusing · $remainingText")
            .setContentText(
                if (exits == 0) "Tap to come back to your session" else "$exits gentle ${if (exits == 1) "exit" else "exits"} so far · Tap to continue",
            )
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pi)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setSilent(true)
            .build()
    }

    /** One-off reminder; on denial it is simply not posted. */
    fun postFocusReminder() {
        if (!hasNotificationPermission()) return
        createChannels()
        val openApp = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(EXTRA_OPEN_FOCUS, true)
        }
        val pi = PendingIntent.getActivity(
            context,
            1,
            openApp,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val content = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setContentTitle("Time for a session?")
            .setContentText("A quiet moment, at your pace.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setSilent(true)
            .build()
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_REMINDER, content)
    }

    companion object {
        private const val CHANNEL_REMINDERS = "reminders"
        private const val CHANNEL_FOCUS = "focus"

        /** MainActivity reads this to route a notification tap back to the Focus tab. */
        const val EXTRA_OPEN_FOCUS = "open_focus"

        /** Session id carried by the ongoing Focus notification; informational only. */
        const val EXTRA_FOCUS_SESSION_ID = "focus_session_id"

        private const val NOTIFICATION_REMINDER = 4002
    }
}
