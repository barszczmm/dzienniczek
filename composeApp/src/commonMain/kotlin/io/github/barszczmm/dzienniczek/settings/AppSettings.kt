package io.github.barszczmm.dzienniczek.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import io.github.barszczmm.dzienniczek.notifications.BackgroundSchedule
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** User settings kept in the app DataStore. */
class AppSettings(private val dataStore: DataStore<Preferences>) {
    private val messagePollingKey = booleanPreferencesKey("settings_message_polling")
    private val backgroundChecksKey = stringSetPreferencesKey("settings_background_checks")
    private val checkStartHourKey = intPreferencesKey("settings_check_start_hour")
    private val checkEndHourKey = intPreferencesKey("settings_check_end_hour")

    /** Background checking (messages and the other [backgroundChecks]) – off by default. */
    val messagePolling: Flow<Boolean> = dataStore.data.map { it[messagePollingKey] ?: false }

    suspend fun isMessagePollingEnabled(): Boolean = messagePolling.first()

    suspend fun setMessagePolling(enabled: Boolean) {
        dataStore.edit { it[messagePollingKey] = enabled }
    }

    /** What the background check looks for. */
    val backgroundChecks: Flow<Set<BackgroundCheck>> = dataStore.data.map { prefs ->
        prefs[backgroundChecksKey]
            ?.mapNotNull { name -> BackgroundCheck.entries.firstOrNull { it.name == name } }
            ?.toSet()
            ?: BackgroundCheck.DEFAULT
    }

    /** Hours of the day the background checks run in: start..end (end exclusive, 24 = midnight). */
    val checkHours: Flow<IntRange> = dataStore.data.map { prefs ->
        val start = prefs[checkStartHourKey] ?: BackgroundSchedule.DEFAULT_START_HOUR
        val end = prefs[checkEndHourKey] ?: BackgroundSchedule.DEFAULT_END_HOUR
        start..end
    }

    suspend fun setCheckHours(startHour: Int, endHour: Int) {
        dataStore.edit {
            it[checkStartHourKey] = startHour
            it[checkEndHourKey] = endHour
        }
    }

    suspend fun setBackgroundCheck(check: BackgroundCheck, enabled: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[backgroundChecksKey] ?: BackgroundCheck.DEFAULT.map { it.name }.toSet()
            prefs[backgroundChecksKey] = if (enabled) current + check.name else current - check.name
        }
    }
}
