package org.sonnayasomnambula.nearby.exchanger

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class Role { ADVERTISER, DISCOVERER }

enum class ConnectionState { NOT_CONNECTED, ADVERTISING, DISCOVERING, CONNECTED }

data class SaveLocation(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val uri: Uri, // URI выбранной папки
    val isSelected: Boolean = false
)

data class MainScreenState (
    val connectionState: ConnectionState = ConnectionState.NOT_CONNECTED,
    val selectedRole: Role? = null,
    val saveLocations: List<SaveLocation> = emptyList(),
    val statusText: String = ""
)

sealed interface MainScreenEffect {
    data object OpenFolderPicker : MainScreenEffect
    data class ShowMessage(val text: String) : MainScreenEffect
}

sealed interface MainScreenEvent {
    data class RoleSelected(val role: Role) : MainScreenEvent
    data object AddLocationRequested : MainScreenEvent
    data class LocationSelected(val id: String) : MainScreenEvent
    data object SendClicked : MainScreenEvent
    data object ActivityStarted: MainScreenEvent
}

class MainScreenViewModel : ViewModel() {

    private val _state = MutableStateFlow(MainScreenState())
    val state: StateFlow<MainScreenState> = _state

    private val _effects = Channel<MainScreenEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun onEvent(event: MainScreenEvent) {
        when (event) {
            is MainScreenEvent.ActivityStarted -> onActivityStarted()
            is MainScreenEvent.RoleSelected -> onRoleSelected(event.role)
            is MainScreenEvent.AddLocationRequested -> onAddLocationRequested()
            is MainScreenEvent.LocationSelected -> onLocationSelected(event.id)
            is MainScreenEvent.SendClicked -> onSendClicked()
        }
    }

    private fun onActivityStarted() {
        
    }

    fun onLocationPicked(uri: Uri, name: String) {
        _state.update { state ->
            state.copy(
                saveLocations = state.saveLocations + SaveLocation(
                    name = name,
                    uri = uri,
                    isSelected = state.saveLocations.isEmpty()
                )
            )
        }
    }

    private fun onSendClicked() {
        val state = _state.value

        if (state.selectedRole == null) {
            viewModelScope.launch {
                _effects.send(MainScreenEffect.ShowMessage("Выберите роль"))
            }
            return
        }
    }

    private fun onLocationSelected(id: String) {
        _state.update { state ->
            state.copy(
                saveLocations = state.saveLocations.map {
                    it.copy(isSelected = it.id == id)
                }
            )
        }
    }

    private fun onAddLocationRequested() {
        viewModelScope.launch {
            _effects.send(MainScreenEffect.OpenFolderPicker)
        }
    }

    private fun onRoleSelected(role: Role) {
        _state.update {
            it.copy(selectedRole = role)
        }
    }
}