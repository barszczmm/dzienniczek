package io.github.barszczmm.dzienniczek.notifications

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Remembers which items of each source were already seen by the background checks. */
class SeenItems(private val dataStore: DataStore<Preferences>) {
    private val json = Json { ignoreUnknownKeys = true }
    private val idListSerializer = ListSerializer(String.serializer())

    /**
     * Returns ids from [currentIds] that were not stored for [sourceKey] before and stores
     * the current list. Returns an empty set the first time a source is seen, so turning
     * a check on does not flood the user with notifications about old items.
     */
    suspend fun diffAndRemember(sourceKey: String, currentIds: List<String>): Set<String> {
        val prefKey = stringPreferencesKey("notif_seen_$sourceKey")
        val previous: List<String>? = dataStore.data.first()[prefKey]?.let {
            runCatching { json.decodeFromString(idListSerializer, it) }.getOrNull()
        }
        // Keep old ids too, so an item that drops off the fetched range is not reported again.
        val merged = (currentIds + (previous ?: emptyList())).distinct().take(MAX_REMEMBERED_IDS)
        dataStore.edit { it[prefKey] = json.encodeToString(idListSerializer, merged) }

        if (previous == null) return emptySet()
        val previousSet = previous.toSet()
        return currentIds.filterNot { it in previousSet }.toSet()
    }

    private companion object {
        const val MAX_REMEMBERED_IDS = 1000
    }
}
