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

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }

    suspend fun onboardingCompletedSnapshot(): Boolean =
        context.dataStore.data.map { it[Keys.ONBOARDING_COMPLETED] ?: false }.first()
}
