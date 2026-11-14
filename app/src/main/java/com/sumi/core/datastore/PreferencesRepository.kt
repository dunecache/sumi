package com.sumi.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "sumi_preferences")

/** DataStore keys per DATA_MODEL.md §3. Preferences live here; queried/aggregated data lives in Room. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Daily intention goal configuration. An awareness instrument, never an enforcement
 * mechanism: it gates nothing and triggers no notifications.
 *
 * @param goalMinutes configured target, or null when no goal is set.
 * @param enabled when false the goal is configured but paused (UI shows the disabled state).
 */
data class DailyGoalConfig(
    val goalMinutes: Int?,
    val enabled: Boolean,
) {
    val activeGoalMinutes: Int?
        get() = if (enabled) goalMinutes else null
}

/**
 * Today's intention for the Focus tab (§16.2). Stored per local calendar day;
 * "today" is resolved by the caller (same rule as the daily aggregation boundary).
 */
data class DailyIntentionConfig(
    val text: String,
    val dateKey: String,
)

@Singleton
class PreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val DAILY_GOAL_MINUTES = intPreferencesKey("daily_goal_minutes")
        val DAILY_GOAL_ENABLED = booleanPreferencesKey("daily_goal_enabled")
        val SHOW_SYSTEM_APPS = booleanPreferencesKey("show_system_apps")
        val DAILY_INTENTION = stringPreferencesKey("daily_intention")
        val DAILY_INTENTION_DATE = stringPreferencesKey("daily_intention_date")
        val FOCUS_LAST_DURATION_MIN = intPreferencesKey("focus_last_duration_min")
        val FOCUS_REMINDER_ENABLED = booleanPreferencesKey("focus_reminder_enabled")
        val FOCUS_REMINDER_MINUTE_OF_DAY = intPreferencesKey("focus_reminder_minute_of_day")
    }

    val onboardingCompleted: Flow<Boolean> = context.dataStore.data.map { it[Keys.ONBOARDING_COMPLETED] ?: false }
    val themeMode: Flow<ThemeMode> = context.dataStore.data.map {
        try {
            ThemeMode.valueOf(it[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name)
        } catch (_: IllegalArgumentException) {
            ThemeMode.SYSTEM
        }
    }

    val notificationsEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.NOTIFICATIONS_ENABLED] ?: false }

    /**
     * Apps library system-furniture policy: false (default) hides system apps from the
     * Apps list (they still accrue usage and keep their assignments); true mixes them in.
     * Never affects collection, exclusions, or percentages.
     */
    val showSystemApps: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.SHOW_SYSTEM_APPS] ?: false }

    /** Live goal configuration; the Today screen renders from this plus the same daily aggregate as the headline. */
    val dailyGoal: Flow<DailyGoalConfig> = context.dataStore.data.map { prefs ->
        DailyGoalConfig(
            goalMinutes = prefs[Keys.DAILY_GOAL_MINUTES],
            enabled = prefs[Keys.DAILY_GOAL_ENABLED] ?: true,
        )
    }

    suspend fun setOnboardingCompleted(value: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = value }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    /** Sets the target; passing null removes the goal entirely. */
    suspend fun setDailyGoal(goalMinutes: Int?) {
        context.dataStore.edit { prefs ->
            if (goalMinutes != null) {
                prefs[Keys.DAILY_GOAL_MINUTES] = goalMinutes
                prefs[Keys.DAILY_GOAL_ENABLED] = true
            } else {
                prefs.remove(Keys.DAILY_GOAL_MINUTES)
            }
        }
    }

    suspend fun setDailyGoalEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DAILY_GOAL_ENABLED] = enabled }
    }

    suspend fun setNotificationsEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = value }
    }

    suspend fun setShowSystemApps(value: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_SYSTEM_APPS] = value }
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }

    // ---- Daily intention (Focus; stored per local calendar day) ----

    /** Intention set for [dateKey]; absent on every day the user did not set one. */
    val dailyIntention: Flow<DailyIntentionConfig> = context.dataStore.data.map { prefs ->
        DailyIntentionConfig(
            text = prefs[Keys.DAILY_INTENTION].orEmpty(),
            dateKey = prefs[Keys.DAILY_INTENTION_DATE].orEmpty(),
        )
    }

    suspend fun setDailyIntention(dateKey: String, text: String) {
        context.dataStore.edit { prefs ->
            if (text.isBlank()) {
                prefs.remove(Keys.DAILY_INTENTION)
                prefs.remove(Keys.DAILY_INTENTION_DATE)
            } else {
                prefs[Keys.DAILY_INTENTION] = text.trim()
                prefs[Keys.DAILY_INTENTION_DATE] = dateKey
            }
        }
    }

    /** Remembered duration for the next session (default: 25 minutes — §16.3). */
    val focusLastDurationMinutes: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[Keys.FOCUS_LAST_DURATION_MIN] ?: 25
    }

    suspend fun rememberFocusDuration(minutes: Int) {
        context.dataStore.edit { it[Keys.FOCUS_LAST_DURATION_MIN] = minutes }
    }

    // ---- Focus reminder (§16.9); default OFF; local time-of-day, minutes after midnight ----

    val focusReminderEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.FOCUS_REMINDER_ENABLED] ?: false
    }

    /** When enabled, a one-shot nudging prayer time is scheduled ~15 min before it fires. */
    val focusReminderMinuteOfDay: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[Keys.FOCUS_REMINDER_MINUTE_OF_DAY] ?: 540 // default: 09:00 (540 = 9*60)
    }

    suspend fun setFocusReminderEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.FOCUS_REMINDER_ENABLED] = enabled }
    }

    suspend fun setFocusReminderMinuteOfDay(minuteOfDay: Int) {
        context.dataStore.edit { it[Keys.FOCUS_REMINDER_MINUTE_OF_DAY] = minuteOfDay.coerceIn(0, 23 * 60 + 59) }
    }

    suspend fun onboardingCompletedSnapshot(): Boolean =
        context.dataStore.data.map { it[Keys.ONBOARDING_COMPLETED] ?: false }.first()
}
