package org.sonnayasomnambula.nearby.exchanger.app

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.sonnayasomnambula.nearby.exchanger.model.MainScreenState
import org.sonnayasomnambula.nearby.exchanger.model.SaveDir
import java.io.IOException
import androidx.core.net.toUri

interface Storage {
    suspend fun updateDirs(dirs: List<SaveDir>)
    suspend fun updateCurrentDir(uri: Uri?)
    suspend fun getCurrentState(): MainScreenState
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "main_screen_state")

class DataStoreStorage(private val context: Context) : Storage {

    private companion object {
        // Ключи для хранения данных
        private val KEY_DIRS = stringPreferencesKey("dirs")
        private val KEY_CURRENT_DIR = stringPreferencesKey("current_dir")
    }

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    // Flow для наблюдения за состоянием
    val mainScreenState: Flow<MainScreenState> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            MainScreenState(
                saveDirs = preferences[KEY_DIRS]?.let { dirsJson ->
                    try {
                        json.decodeFromString<List<SaveDirDto>>(dirsJson)
                            .map { it.toSaveDir() }
                    } catch (e: Exception) {
                        emptyList()
                    }
                } ?: emptyList(),
                currentDir = preferences[KEY_CURRENT_DIR]?.let { uriString ->
                    try {
                        uriString.toUri()
                    } catch (e: Exception) {
                        null
                    }
                }
            )
        }

    // Сохранение всего состояния
    suspend fun saveMainScreenState(state: MainScreenState) {
        context.dataStore.edit { preferences ->
            val dirsJson = json.encodeToString(
                state.saveDirs.map { SaveDirDto.fromSaveDir(it) }
            )

            preferences[KEY_DIRS] = dirsJson
            preferences[KEY_CURRENT_DIR] = state.currentDir?.toString() ?: ""
        }
    }

    override suspend fun updateDirs(dirs: List<SaveDir>) {
        context.dataStore.edit { preferences ->
            val dirsJson = json.encodeToString(
                dirs.map { SaveDirDto.fromSaveDir(it) }
            )
            preferences[KEY_DIRS] = dirsJson
        }
    }

    override suspend fun updateCurrentDir(uri: Uri?) {
        context.dataStore.edit { preferences ->
            if (uri != null) {
                preferences[KEY_CURRENT_DIR] = uri.toString()
            } else {
                preferences.remove(KEY_CURRENT_DIR)
            }
        }
    }

    override suspend fun getCurrentState(): MainScreenState {
        return context.dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                MainScreenState(
                    saveDirs = preferences[KEY_DIRS]?.let { dirsJson ->
                        try {
                            json.decodeFromString<List<SaveDirDto>>(dirsJson)
                                .map { it.toSaveDir() }
                        } catch (e: Exception) {
                            emptyList()
                        }
                    } ?: emptyList(),
                    currentDir = preferences[KEY_CURRENT_DIR]?.let { uriString ->
                        try {
                            uriString.toUri()
                        } catch (e: Exception) {
                            null
                        }
                    }
                )
            }
            .first()
    }

    // Очистка всех данных
    suspend fun clearAll() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}

// DTO для сериализации SaveDir (Uri не сериализуется напрямую)
@Serializable
private data class SaveDirDto(
    val name: String,
    val uriString: String
) {
    fun toSaveDir(): SaveDir {
        return SaveDir(name, uriString.toUri())
    }

    companion object {
        fun fromSaveDir(saveDir: SaveDir): SaveDirDto {
            return SaveDirDto(
                name = saveDir.name,
                uriString = saveDir.uri.toString()
            )
        }
    }
}