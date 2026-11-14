package com.sumi.platform.focus

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.sumi.data.focus.FocusSessionRepository
import com.sumi.domain.common.Clock
import com.sumi.domain.focus.FocusMath
import com.sumi.platform.notifications.SumiNotifications
import com.sumi.domain.usage.UsageEventSource
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service for an active Focus session (ARCHITECTURE.md §16.5).
 *
 * Special use — not data sync: this is a user-chosen "stay with me" countdown. The FGS
 * carries a calm ongoing notification (tappable, low importance) so the session is not
 * lost to rotation/back/the recents stack.
 *
 * Source of truth is the database, not this class: the service reads the open session
 * on start and re-reads it each tick. If the service dies anyway, the session row
 * outlives it; the app seals any open session the next time it starts (see
 * `CategoryBootstrap`), stamping it `reboot` at the recorded boot instant.
 *
 * Drift (§16.6): every [FocusMath.DRIFT_INTERVAL_MS] the service queries the events in
 * the last minute and counts non-Sumi foregrounds. Consecutive 2 minutes away → the
 * session ends with outcome `away_limit`. We record nothing about the host app — just
 * that the user left, and how often.
 */
@AndroidEntryPoint
class FocusSessionService : Service() {

    @Inject lateinit var focusSessions: FocusSessionRepository
    @Inject lateinit var clock: Clock
    @Inject lateinit var notifications: SumiNotifications
    /** Read usage events to count gentle exits — the same query the engine uses, scoped to a minute. */
    @Inject lateinit var eventSource: UsageEventSource

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private var tickJob: Job? = null
    private var lastDriftCheckUtcMs = 0L
    private var nextDriftCheckUtcMs = 0L
    private var awaySinceUtcMs: Long? = null
    private var driftState = FocusMath.ExitState()

    override fun onCreate() {
        super.onCreate()
        notifications.createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_END -> {
                endCurrentSession(
                    endedAtUtcMs = clock.nowUtcMs(),
                    outcome = com.sumi.core.database.entity.FocusOutcome.ENDED_EARLY,
                )
            }
            else -> {
                startOrResume()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): android.os.IBinder? = null

    /** Mounts the ongoing notification and starts the tick loop; no-op when no session is open. */
    private fun startOrResume() {
        tickJob?.cancel()
        scope.launch {
            val session = focusSessions.openSessionOrNull()
            if (session == null) {
                stopSelfSafely()
                return@launch
            }
            lastDriftCheckUtcMs = clock.nowUtcMs()
            val notification = notifications.buildFocusNotification(
                remainingText = formatRemaining(session.startedAtUtcMs, session.intendedMinutes, clock.nowUtcMs()),
                exits = session.exitCount,
                sessionId = session.id,
            )
            startForegroundCompat(notification)
            tickJob = scope.launch { tickLoop(session.id) }
        }
    }

    /** Updates the notification once per second and ends the session when it rings through. */
    private suspend fun tickLoop(sessionId: Long) {
        while (true) {
            val session = focusSessions.openSessionOrNull()
            if (session == null || session.id != sessionId) {
                stopSelfSafely()
                return
            }
            val now = clock.nowUtcMs()
            if (FocusMath.isComplete(session.startedAtUtcMs, session.intendedMinutes, now)) {
                focusSessions.endCompleted()
                stopSelfSafely()
                return
            }
            updateNotification(session, now)
            driftCheck(now)
            kotlinx.coroutines.delay(FocusMath.POLL_INTERVAL_MS)
        }
    }

    /** Drift pass, once per [FocusMath.DRIFT_INTERVAL_MS]: new non-Sumi foregrounds become exits. */
    private suspend fun driftCheck(nowUtcMs: Long) {
        if (nowUtcMs < nextDriftCheckUtcMs) return
        nextDriftCheckUtcMs = nowUtcMs + FocusMath.DRIFT_INTERVAL_MS
        val from = lastDriftCheckUtcMs
        lastDriftCheckUtcMs = nowUtcMs
        val raw = try {
            eventSource.loadRawEvents(from, nowUtcMs)
        } catch (_: Exception) {
            // Drift awareness degrades gracefully when usage access is revoked mid-session;
            // the session itself keeps working — the user is told once, at the Focus screen.
            return
        }
        raw.forEach { event ->
            if (event.type != com.sumi.domain.usage.UsageEventType.FOREGROUND) return@forEach
            if (event.packageName.isBlank()) return@forEach // screen interactive events: no app context
            val isSumi = event.packageName == packageName
            val previous = driftState
            val next = FocusMath.onForeground(previous, isSumiPackage = isSumi)
            driftState = next
            if (isSumi) {
                awaySinceUtcMs = null
                return@forEach
            }
            if (!previous.away && next.away) {
                awaySinceUtcMs = event.timestampUtcMs
                focusSessions.recordExit()
            }
        }
        val awaySince = awaySinceUtcMs
        if (awaySince != null && nowUtcMs - awaySince >= FocusMath.AUTO_END_AWAY_MS) {
            endCurrentSession(endedAtUtcMs = nowUtcMs, outcome = com.sumi.core.database.entity.FocusOutcome.AWAY_LIMIT)
        }
    }

    private fun updateNotification(session: com.sumi.core.database.entity.FocusSession, nowUtcMs: Long) {
        val notification = notifications.buildFocusNotification(
            remainingText = formatRemaining(session.startedAtUtcMs, session.intendedMinutes, nowUtcMs),
            exits = session.exitCount,
            sessionId = session.id,
        )
        if (notifications.hasNotificationPermission()) {
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, notification)
        }
    }

    private fun startForegroundCompat(notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            // API 34+: declare the user-chosen countdown type. Guarded so the NewApi
            // check is satisfied; the value is a known constant for WrongConstant.
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            @Suppress("DEPRECATION")
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopSelfSafely() {
        stopForegroundCompat()
        // Detach alone would strand a static "Focusing" notification in the shade;
        // cancel it — the session is over, so no notification should outlive it.
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        stopSelf()
    }

    private fun stopForegroundCompat() {
        // minSdk 26 covers STOP_FOREGROUND_DETACH (API 24+).
        stopForeground(STOP_FOREGROUND_DETACH)
    }

    private fun endCurrentSession(endedAtUtcMs: Long, outcome: String) {
        scope.launch {
            focusSessions.closeOpenSession(endedAtUtcMs, outcome)
            stopSelfSafely()
        }
    }

    override fun onDestroy() {
        tickJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun formatRemaining(startedAtUtcMs: Long, intendedMinutes: Int, nowUtcMs: Long): String {
        val remaining = FocusMath.remainingMs(startedAtUtcMs, intendedMinutes, nowUtcMs)
        val minutesLeft = ((remaining + 59_999L) / 60_000L).toInt() // ceiling: 24:59 still reads 25m
        return if (minutesLeft > 0) "${minutesLeft}m left" else "wrapping up"
    }

    companion object {
        const val ACTION_START = "com.sumi.action.FOCUS_START"
        const val ACTION_END = "com.sumi.action.FOCUS_END"

        /** Low importance, ongoing. */
        private const val NOTIFICATION_ID = 4001

        /** Start (or resume onto) an existing notification+service pair for this session. */
        fun start(context: android.content.Context) {
            val intent = Intent(context, FocusSessionService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: android.content.Context) {
            val intent = Intent(context, FocusSessionService::class.java).setAction(ACTION_END)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
