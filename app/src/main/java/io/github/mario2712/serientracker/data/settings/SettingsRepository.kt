package io.github.mario2712.serientracker.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/** State of the background jobs, shown in the settings. */
data class BackgroundSettings(
    val notificationsEnabled: Boolean = false,
    val lastCheckAt: Long? = null,
    val backupEnabled: Boolean = false,
    val backupFolder: String? = null,
    val lastBackupAt: Long? = null,
    val lastBackupError: String? = null,
)

class SettingsRepository(private val context: Context) {

    private val tmdbKey = stringPreferencesKey("tmdb_api_key")
    private val seedImported = booleanPreferencesKey("seed_imported")
    private val notifications = booleanPreferencesKey("notify_new_episodes")
    private val lastCheck = longPreferencesKey("last_new_episode_check")
    private val backupEnabled = booleanPreferencesKey("auto_backup")
    private val backupFolder = stringPreferencesKey("auto_backup_folder")
    private val lastBackup = longPreferencesKey("last_backup")
    private val lastBackupError = stringPreferencesKey("last_backup_error")
    private val listSort = stringPreferencesKey("list_sort")
    private val listGenre = stringPreferencesKey("list_genre")
    private val showExcel = booleanPreferencesKey("show_excel_comparisons")

    /** Show Excel differences and Excel details (off once the app is the only source). */
    val showExcelComparisons: Flow<Boolean> = context.dataStore.data.map { it[showExcel] ?: true }

    suspend fun setShowExcelComparisons(show: Boolean) = set(showExcel, show)

    val tmdbApiKey: Flow<String?> = context.dataStore.data.map { it[tmdbKey] }

    suspend fun currentTmdbApiKey(): String? = tmdbApiKey.first()

    suspend fun setTmdbApiKey(key: String) {
        context.dataStore.edit { it[tmdbKey] = key.trim() }
    }

    suspend fun isSeedImported(): Boolean = context.dataStore.data.first()[seedImported] ?: false

    suspend fun setSeedImported() {
        context.dataStore.edit { it[seedImported] = true }
    }

    val background: Flow<BackgroundSettings> = context.dataStore.data.map { p ->
        BackgroundSettings(
            notificationsEnabled = p[notifications] ?: false,
            lastCheckAt = p[lastCheck],
            backupEnabled = p[backupEnabled] ?: false,
            backupFolder = p[backupFolder],
            lastBackupAt = p[lastBackup],
            lastBackupError = p[lastBackupError],
        )
    }

    suspend fun currentBackground(): BackgroundSettings = background.first()

    suspend fun setNotificationsEnabled(enabled: Boolean) = set(notifications, enabled)
    suspend fun setLastCheck(at: Long) = set(lastCheck, at)
    suspend fun setBackupEnabled(enabled: Boolean) = set(backupEnabled, enabled)
    suspend fun setBackupFolder(uri: String) = set(backupFolder, uri)

    suspend fun setBackupResult(at: Long?, error: String?) {
        context.dataStore.edit { p ->
            if (at != null) p[lastBackup] = at
            if (error != null) p[lastBackupError] = error else p.remove(lastBackupError)
        }
    }

    /** Sort order and genre filter of the series list (names, see SeriesSort). */
    val listPreferences: Flow<Pair<String?, String?>> = context.dataStore.data.map { it[listSort] to it[listGenre] }

    suspend fun setListSort(sort: String) = set(listSort, sort)

    suspend fun setListGenre(genre: String?) {
        context.dataStore.edit { if (genre == null) it.remove(listGenre) else it[listGenre] = genre }
    }

    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
    }
}
