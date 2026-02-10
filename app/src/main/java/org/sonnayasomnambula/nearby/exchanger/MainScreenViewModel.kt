package org.sonnayasomnambula.nearby.exchanger

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

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

// model => activity
sealed interface MainScreenEffect {
    data object OpenFolderPicker : MainScreenEffect
    data class ShowMessage(val text: String) : MainScreenEffect
    data class CheckLocationAccess(val uri: Uri) : MainScreenEffect
}

// activity/composable => model
sealed interface MainScreenEvent {
    data class RoleSelected(val role: Role) : MainScreenEvent
    data object AddLocationRequested : MainScreenEvent
    data class RemoveLocationRequested(val uri: Uri) : MainScreenEvent
    data class LocationSelected(val uri: Uri) : MainScreenEvent
    data object SendClicked : MainScreenEvent
    data object ActivityStarted: MainScreenEvent
    data class LocationAccessChecked(val uri: Uri, val hasAccess: Boolean) : MainScreenEvent
}

class MainScreenViewModel(
    private val storage: Storage,
    private val locationProvider: LocationProvider
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
            is MainScreenEvent.LocationAccessChecked -> onLocationAccessChecked(event.uri, event.hasAccess)
        }
    }

    private fun onLocationAccessChecked(uri: Uri, hasAccess: Boolean) {
        if (!hasAccess) {
            viewModelScope.launch {
                val curState = _state.value
                val updatedLocations = curState.locations.filterNot { it.toString() == uri.toString() }
                val updatedCurrentLocation = if (curState.currentLocation?.toString() == uri.toString()) null else curState.currentLocation

                storage.updateLocations(updatedLocations)
                storage.updateCurrentLocation(updatedCurrentLocation)

                _state.update { currentState ->
                    currentState.copy(
                        locations = updatedLocations,
                        currentLocation = updatedCurrentLocation,
                    )
                }

                _effects.send(MainScreenEffect.OpenFolderPicker) // TODO RequestLocationAccess(LOST_PERMISSION)
            }
        }
    }

    private fun onActivityStarted() {
        viewModelScope.launch {
            val savedState = storage.getCurrentState()

            val (locations, currentLocation) = if (savedState.locations.isNotEmpty()) {
                savedState.locations to savedState.currentLocation
            } else {
                locationProvider.getDefaultLocation()?.let { defaultLocation ->
                    listOf(defaultLocation) to defaultLocation.uri
                } ?: (emptyList<SaveLocation>() to null)
            }

            _state.update { it.copy(
                locations = locations,
                currentLocation = currentLocation
            ) }

            savedState.currentLocation?.let { uri ->
                if (isValidLocation(uri)) {
                    _effects.send(MainScreenEffect.CheckLocationAccess(uri))
                } else {
                    removeSaveLocation(uri)
                }
            }
        }
    }

    private fun isValidLocation(uri: Uri): Boolean {
        return try {
            !uri.toString().isBlank() && !uri.scheme.isNullOrBlank()
        } catch (e: Exception) {
            false
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

        viewModelScope.launch {
            val currentState = _state.value
            storage.updateLocations(currentState.locations)
            storage.updateCurrentLocation(currentState.currentLocation)
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

        viewModelScope.launch {
            val currentState = _state.value
            storage.updateLocations(currentState.locations)
            storage.updateCurrentLocation(currentState.currentLocation)
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
