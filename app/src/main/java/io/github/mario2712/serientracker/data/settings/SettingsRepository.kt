package io.github.mario2712.serientracker.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private val tmdbKey = stringPreferencesKey("tmdb_api_key")
    private val seedImported = booleanPreferencesKey("seed_imported")

    val tmdbApiKey: Flow<String?> = context.dataStore.data.map { it[tmdbKey] }

    suspend fun currentTmdbApiKey(): String? = tmdbApiKey.first()

    suspend fun setTmdbApiKey(key: String) {
        context.dataStore.edit { it[tmdbKey] = key.trim() }
    }

    suspend fun isSeedImported(): Boolean = context.dataStore.data.first()[seedImported] ?: false

    suspend fun setSeedImported() {
        context.dataStore.edit { it[seedImported] = true }
    }
}
