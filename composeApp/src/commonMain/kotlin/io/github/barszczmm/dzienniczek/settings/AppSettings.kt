package io.github.barszczmm.dzienniczek.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** User settings kept in the app DataStore. */
class AppSettings(private val dataStore: DataStore<Preferences>) {
    private val messagePollingKey = booleanPreferencesKey("settings_message_polling")

    /** Background checking for new messages – off by default. */
    val messagePolling: Flow<Boolean> = dataStore.data.map { it[messagePollingKey] ?: false }

    suspend fun isMessagePollingEnabled(): Boolean = messagePolling.first()

    suspend fun setMessagePolling(enabled: Boolean) {
        dataStore.edit { it[messagePollingKey] = enabled }
    }
}
