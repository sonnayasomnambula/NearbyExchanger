package org.sonnayasomnambula.nearby.exchanger

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException

interface Storage {
    suspend fun updateLocations(locations: List<SaveLocation>)
    suspend fun updateCurrentLocation(uri: Uri?)
    suspend fun getCurrentState(): MainScreenState
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "main_screen_state")

class DataStoreStorage(private val context: Context) : Storage {

    private companion object {
        // Ключи для хранения данных
        private val CURRENT_ROLE_KEY = stringPreferencesKey("current_role")
        private val LOCATIONS_KEY = stringPreferencesKey("locations")
        private val CURRENT_LOCATION_KEY = stringPreferencesKey("current_location")
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
                currentRole = preferences[CURRENT_ROLE_KEY]?.let { roleString ->
                    try {
                        Role.valueOf(roleString)
                    } catch (e: IllegalArgumentException) {
                        null
                    }
                },
                locations = preferences[LOCATIONS_KEY]?.let { locationsJson ->
                    try {
                        json.decodeFromString<List<SaveLocationDto>>(locationsJson)
                            .map { it.toSaveLocation() }
                    } catch (e: Exception) {
                        emptyList()
                    }
                } ?: emptyList(),
                currentLocation = preferences[CURRENT_LOCATION_KEY]?.let { uriString ->
                    try {
                        Uri.parse(uriString)
                    } catch (e: Exception) {
                        null
                    }
                }
            )
        }

    // Сохранение всего состояния
    suspend fun saveMainScreenState(state: MainScreenState) {
        context.dataStore.edit { preferences ->
            preferences[CURRENT_ROLE_KEY] = state.currentRole?.name ?: ""

            val locationsJson = json.encodeToString(
                state.locations.map { SaveLocationDto.fromSaveLocation(it) }
            )
            preferences[LOCATIONS_KEY] = locationsJson

            preferences[CURRENT_LOCATION_KEY] = state.currentLocation?.toString() ?: ""
        }
    }

    override suspend fun updateLocations(locations: List<SaveLocation>) {
        context.dataStore.edit { preferences ->
            val locationsJson = json.encodeToString(
                locations.map { SaveLocationDto.fromSaveLocation(it) }
            )
            preferences[LOCATIONS_KEY] = locationsJson
        }
    }

    override suspend fun updateCurrentLocation(uri: Uri?) {
        context.dataStore.edit { preferences ->
            if (uri != null) {
                preferences[CURRENT_LOCATION_KEY] = uri.toString()
            } else {
                preferences.remove(CURRENT_LOCATION_KEY)
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
                    currentRole = preferences[CURRENT_ROLE_KEY]?.let { roleString ->
                        try {
                            Role.valueOf(roleString)
                        } catch (e: IllegalArgumentException) {
                            null
                        }
                    },
                    locations = preferences[LOCATIONS_KEY]?.let { locationsJson ->
                        try {
                            json.decodeFromString<List<SaveLocationDto>>(locationsJson)
                                .map { it.toSaveLocation() }
                        } catch (e: Exception) {
                            emptyList()
                        }
                    } ?: emptyList(),
                    currentLocation = preferences[CURRENT_LOCATION_KEY]?.let { uriString ->
                        try {
                            Uri.parse(uriString)
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

// DTO для сериализации SaveLocation (Uri не сериализуется напрямую)
@kotlinx.serialization.Serializable
private data class SaveLocationDto(
    val name: String,
    val uriString: String
) {
    fun toSaveLocation(): SaveLocation {
        return SaveLocation(name, Uri.parse(uriString))
    }

    companion object {
        fun fromSaveLocation(saveLocation: SaveLocation): SaveLocationDto {
            return SaveLocationDto(
                name = saveLocation.name,
                uriString = saveLocation.uri.toString()
            )
        }
    }
}