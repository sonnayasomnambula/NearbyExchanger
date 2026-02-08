package org.sonnayasomnambula.nearby.exchanger

import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.core.net.toUri

enum class Role { ADVERTISER, DISCOVERER }

enum class ConnectionState { NOT_CONNECTED, ADVERTISING, DISCOVERING, CONNECTED }

data class SaveLocation(
    val name: String,
    val uri: Uri,
)

data class MainScreenState (
    val connectionState: ConnectionState = ConnectionState.NOT_CONNECTED,
    val currentRole: Role? = null,
    val locations: List<SaveLocation> = emptyList(),
    val currentLocation: Uri? = null,
    val statusText: String = ""
)

sealed interface MainScreenEffect {
    data object OpenFolderPicker : MainScreenEffect
    data class ShowMessage(val text: String) : MainScreenEffect
}

sealed interface MainScreenEvent {
    data class RoleSelected(val role: Role) : MainScreenEvent
    data object AddLocationRequested : MainScreenEvent
    data class RemoveLocationRequested(val uri: Uri) : MainScreenEvent
    data class LocationSelected(val uri: Uri) : MainScreenEvent
    data object SendClicked : MainScreenEvent
    data object ActivityStarted: MainScreenEvent
}

class MainScreenViewModel(
    private val storage: Storage
) : ViewModel() {

    private val _state = MutableStateFlow(MainScreenState())
    val state: StateFlow<MainScreenState> = _state

    private val _effects = Channel<MainScreenEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun onEvent(event: MainScreenEvent) {
        when (event) {
            is MainScreenEvent.ActivityStarted -> onActivityStarted()
            is MainScreenEvent.RoleSelected -> onRoleSelected(event.role)
            is MainScreenEvent.AddLocationRequested -> requestAddLocation()
            is MainScreenEvent.LocationSelected -> setCurrentLocation(event.uri)
            is MainScreenEvent.RemoveLocationRequested -> removeSaveLocation(event.uri)
            is MainScreenEvent.SendClicked -> onSendClicked()
        }
    }

    private fun onActivityStarted() {
        viewModelScope.launch {
            // 1. Загружаем сохранённое состояние
            val savedState = storage.getCurrentState()

            // 2. Обновляем UI с загруженными данными
            _state.update { it.copy(
                currentRole = savedState.currentRole,
                locations = savedState.locations,
                currentLocation = savedState.currentLocation
            ) }

            // 3. Добавляем Downloads если нет
            addDownloadsIfNeeded(savedState.locations)

            // 4. Проверяем текущую локацию
            savedState.currentLocation?.let { uri ->
                // TODO
            }
        }
    }

    private suspend fun addDownloadsIfNeeded(existingLocations: List<SaveLocation>) {
        val downloadsUri = getDownloadsUri() ?: return

        val downloadsAlreadyExists = existingLocations.any { location ->
            location.uri == downloadsUri
        }

        if (!downloadsAlreadyExists) {
            val downloadsLocation = SaveLocation(
                name = "Downloads",
                uri = downloadsUri
            )

            // Добавляем в storage
            storage.addLocation(downloadsLocation)

            // Обновляем state
            _state.update { currentState ->
                currentState.copy(
                    locations = currentState.locations + downloadsLocation
                )
            }
        }
    }

    private fun getDownloadsUri(): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Для Android 10+ используем MediaStore
            Environment.DIRECTORY_DOWNLOADS?.let { directory ->
                "content://media/external/downloads".toUri()
            }
        } else {
            // Для старых версий
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)?.let { file ->
                Uri.fromFile(file)
            }
        }
    }

    fun addSaveLocation(uri: Uri, name: String) {
        _state.update { state ->
            val alreadyExists = state.locations.any { it.uri == uri }
            if (alreadyExists) {
                state // ignore
            } else {
                state.copy(
                    locations = state.locations + SaveLocation(name, uri),
                    currentLocation = uri
                )
            }
        }
    }

    fun removeSaveLocation(uri: Uri) {
        _state.update { state ->
            val newLocations = state.locations.filter { it.uri != uri }
            val newCurrentUri = if (state.currentLocation == uri) {
                null // Если удаляем текущую выбранную, сбрасываем выбор
            } else {
                state.currentLocation
            }

            state.copy(
                locations = newLocations,
                currentLocation = newCurrentUri
            )
        }
    }

    private fun onSendClicked() {
        val state = _state.value

        if (state.currentRole == null) {
            viewModelScope.launch {
                _effects.send(MainScreenEffect.ShowMessage("Выберите роль"))
            }
            return
        }
    }

    private fun setCurrentLocation(uri: Uri) {
        _state.update { it.copy(currentLocation = uri) }
    }

    private fun requestAddLocation() {
        viewModelScope.launch {
            _effects.send(MainScreenEffect.OpenFolderPicker)
        }
    }

    private fun onRoleSelected(role: Role) {
        _state.update {
            it.copy(currentRole = role)
        }
    }
}