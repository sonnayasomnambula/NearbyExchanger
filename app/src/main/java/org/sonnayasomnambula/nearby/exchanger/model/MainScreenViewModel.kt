package org.sonnayasomnambula.nearby.exchanger.model

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.sonnayasomnambula.nearby.exchanger.service.RemoteDevice
import org.sonnayasomnambula.nearby.exchanger.app.Storage
import org.sonnayasomnambula.nearby.exchanger.model.MainScreenEffect.*
import org.sonnayasomnambula.nearby.exchanger.service.ExchangeService
import org.sonnayasomnambula.nearby.exchanger.service.ServiceCommand
import org.sonnayasomnambula.nearby.exchanger.service.ServiceEvent
import org.sonnayasomnambula.nearby.exchanger.service.ServiceState

enum class Role { ADVERTISER, DISCOVERER }

enum class ConnectionState { DISCONNECTED, ADVERTISING, DISCOVERING, CONNECTED }

data class SaveDir(
    val name: String,
    val uri: Uri,
)

data class MainScreenState (
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val currentRole: Role? = null,
    val saveDirs: List<SaveDir> = emptyList(),
    val currentDir: Uri? = null,
    val statusText: String = "",
    val availableDevices: List<RemoteDevice> = emptyList(),
)

// activity/composable => model
sealed interface MainScreenEvent {
    data object ActivityStarted: MainScreenEvent
    data object AddDirectoryRequested : MainScreenEvent
    data class RemoveDirectoryRequested(val uri: Uri) : MainScreenEvent
    data class DirectorySelected(val uri: Uri) : MainScreenEvent
    data class RoleSelected(val role: Role) : MainScreenEvent
    data object DisconnectClicked : MainScreenEvent
    data object SendClicked : MainScreenEvent
    data class ServiceStarted(val role: Role): MainScreenEvent
    data object ServiceStopped: MainScreenEvent
    data class DirectoryAccessChecked(val uri: Uri, val hasAccess: Boolean) : MainScreenEvent
    data class PermissionsResult(val granted: Boolean) : MainScreenEvent
}

// model => activity
sealed interface MainScreenEffect {
    data object OpenFolderPicker : MainScreenEffect
    data class ShowMessage(val text: String) : MainScreenEffect
    data class CheckDirectoryAccess(val uri: Uri) : MainScreenEffect
    data class RequestPermissions(val permissions: List<String>) : MainScreenEffect
    data class StartForegroundService(val role: Role) : MainScreenEffect
}

/**
 * Central model class responsible for application logic and UI state management.
 *
 * The Model (implemented as a ViewModel) acts as the single source of truth,
 * coordinating communication between the UI layer (Activity/Composable) and background
 * services via a unidirectional data flow pattern following MVI (Model-View-Intent)
 * architecture. It receives UI events from composables, updates its internal state
 * via MutableStateFlow, and emits one-time side effects through a Channel for the
 * Activity to execute (permissions, folder picker, etc). The Model also
 * sends commands to the bound service for direct control.
 *
 * All state updates trigger UI recomposition automatically through the integration of
 * Kotlin Flow and Jetpack Compose. The Model exposes state as a StateFlow, and the UI
 * collects it via `collectAsState()` in the ViewModel. Whenever a new value is emitted
 * to `_state`, the StateFlow updates its value, causing `collectAsState()` to emit a new
 * value and trigger recomposition of only the composables that read this state.
 *
 * Some implemented scenarios:
 *
 * <h3>Application Start</h3>
 * <pre>
 * // UI sends ActivityStarted event when app launches
 * MainScreenEvent.ActivityStarted
 *     ↓
 * [Model loads saved state (list of folders for data storage)]
 * [Model requests the activity to check permissions for the current folder]
 *     ↓
 * MainScreenEffect.CheckDirectoryAccess(currentDir.uri)
 *     ↓
 * // UI checks access and responds with result
 * MainScreenEvent.DirectoryAccessChecked(uri, hasAccess)
 *     ↓
 * [If access denied, Model removes current directory and requests folder picker]
 *     ↓
 * MainScreenEffect.OpenFolderPicker
 * </pre>
 *
 *
 * <h3>Service Launch Flow</h3>
 * <pre>
 * // User selects role (Advertiser/Discoverer)
 * MainScreenEvent.RoleSelected(role)
 *     ↓
 * [Model determines required permissions for the role]
 *     ↓
 * MainScreenEffect.RequestPermissions(permissionsList)
 *     ↓
 * // User grants/denies permissions
 * MainScreenEvent.PermissionsResult(granted)
 *     ↓
 * [If granted, Model proceeds with service start]
 *     ↓
 * MainScreenEffect.StartForegroundService(role)
 *     ↓
 * // Service starts and binds; UI notifies Model
 * MainScreenEvent.ServiceStarted(role)
 *     ↓
 * [Model stores service reference for communication]
 * [Model updates UI state based on service role]
 * [Model commands service to begin operation]
 *     ↓
 * ServiceCommand.StartSearching
 * </pre>
 */
class MainScreenViewModel(
    private val storage: Storage,
    private val directoryProvider: DirectoryProvider,
    private val permissionPolicy: PermissionPolicy
) : ViewModel() {

    private val LOG_TRACE = "org.sonnayasomnambula.trace"

    private val _state = MutableStateFlow(MainScreenState())
    val state: StateFlow<MainScreenState> = _state

    private val _effects = Channel<MainScreenEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private sealed interface PendingAction {
        data class StartService(val role: Role) : PendingAction
        data class StartSearching(val role: Role) : PendingAction
    }

    private var pendingAction: PendingAction? = null

    fun onEvent(event: MainScreenEvent) {
        Log.d(LOG_TRACE, "model: ${event.toString()}")
        when (event) {
            is MainScreenEvent.ActivityStarted -> onActivityStarted()
            is MainScreenEvent.ServiceStarted -> onServiceStarted(event.role)
            is MainScreenEvent.ServiceStopped -> onServiceStopped()
            is MainScreenEvent.RoleSelected -> onRoleSelected(event.role)
            is MainScreenEvent.AddDirectoryRequested -> requestAddDir()
            is MainScreenEvent.DirectorySelected -> setCurrentDir(event.uri)
            is MainScreenEvent.RemoveDirectoryRequested -> removeDir(event.uri)
            is MainScreenEvent.SendClicked -> onSendClicked()
            is MainScreenEvent.DisconnectClicked -> onDisconnectClicked()
            is MainScreenEvent.DirectoryAccessChecked -> onDirectoryAccessChecked(event.uri, event.hasAccess)
            is MainScreenEvent.PermissionsResult -> onPermissionResult(granted = event.granted)
        }
    }

    private fun onPermissionResult(granted: Boolean) {
        if (granted) {
            viewModelScope.launch {
                val action = requireNotNull(pendingAction)
                pendingAction = null
                when (action) {
                    is PendingAction.StartService -> {
                        _effects.send(MainScreenEffect.StartForegroundService(action.role))
                    }
                    is PendingAction.StartSearching -> {
                        service?.onCommand(ServiceCommand.StartSearching)
                    }
                }
            }
        }
    }

    private fun onServiceStarted(role: Role) {
        viewModelScope.launch {
            _state.update { currentState ->
                currentState.copy(
                    currentRole = role,
                    connectionState = when (role) {
                        Role.ADVERTISER -> ConnectionState.ADVERTISING
                        Role.DISCOVERER -> ConnectionState.DISCOVERING
                    }
                )
            }

            pendingAction = PendingAction.StartSearching(role)
            val permissions = permissionPolicy.permissionsFor(role)
            _effects.send(MainScreenEffect.RequestPermissions(permissions))
        }
    }

    private fun onServiceStopped() {
        viewModelScope.launch {
            _state.update { currentState ->
                currentState.copy(
                    connectionState = ConnectionState.DISCONNECTED,
                    currentRole = null
                )
            }
        }
    }

    private var service: ExchangeService? = null

    // Состояние сервиса для UI
    private val _serviceState = MutableStateFlow<ServiceState>(ServiceState.Initial)
    val serviceState: StateFlow<ServiceState> = _serviceState.asStateFlow()

    // Обработка событий сервиса
    private val _serviceEvents = MutableSharedFlow<ServiceEvent>()
    val serviceEvents: SharedFlow<ServiceEvent> = _serviceEvents.asSharedFlow()

    fun subscribeToService(service: ExchangeService) {
        this.service = service

        viewModelScope.launch {
            service.state.collect { state ->
                _serviceState.value = state

                // Можем также отправлять эффекты для UI при необходимости
                when (state) {
                    is ServiceState.Running -> {
                    }
                    ServiceState.Stopped -> {
                    }
                    ServiceState.Initial -> {
                    }
                }
            }
        }

        // Подписываемся на события
        viewModelScope.launch {
            service.events.collect { event ->
                _serviceEvents.emit(event)
//                when (event) {
//                    ServiceEvent.Stopped -> {
//                        _role.value = null
//                        _effects.emit(MainScreenEffect.ServiceStopped)
//                    }
//                }
            }
        }
    }

    private fun onDirectoryAccessChecked(uri: Uri, hasAccess: Boolean) {
        if (!hasAccess) {
            viewModelScope.launch {
                val curState = _state.value
                val updatedDirs = curState.saveDirs.filterNot { it.toString() == uri.toString() }
                val updatedCurrentDir = if (curState.currentDir?.toString() == uri.toString()) null else curState.currentDir

                storage.updateDirs(updatedDirs)
                storage.updateCurrentDir(updatedCurrentDir)

                _state.update { currentState ->
                    currentState.copy(
                        saveDirs = updatedDirs,
                        currentDir = updatedCurrentDir,
                    )
                }

                _effects.send(MainScreenEffect.OpenFolderPicker)
            }
        }
    }

    private fun onActivityStarted() {
        viewModelScope.launch {
            val savedState = storage.getCurrentState()

            val (dirs, currentDir) = if (savedState.saveDirs.isNotEmpty()) {
                Log.d(LOG_TRACE, "model: loaded dirs ${savedState.saveDirs.joinToString { it.uri.toString() }}")
                Log.d(LOG_TRACE, "model: loaded current dir ${savedState.currentDir?.toString() ?: "null"}")
                savedState.saveDirs to savedState.currentDir
            } else {
                directoryProvider.defaultSaveDirectory()?.let { defaultDir ->
                    listOf(defaultDir) to defaultDir.uri
                } ?: (emptyList<SaveDir>() to null)
            }

            _state.update { it.copy(
                saveDirs = dirs,
                currentDir = currentDir
            ) }

            savedState.currentDir?.let { uri ->
                if (isValidDirectory(uri)) {
                    _effects.send(MainScreenEffect.CheckDirectoryAccess(uri))
                } else {
                    removeDir(uri)
                }
            }
        }
    }

    private fun isValidDirectory(uri: Uri): Boolean {
        return try {
            !uri.toString().isBlank() && !uri.scheme.isNullOrBlank()
        } catch (e: Exception) {
            false
        }
    }

    fun addSaveDir(uri: Uri, name: String) {
        _state.update { state ->
            val alreadyExists = state.saveDirs.any { it.uri == uri }
            if (alreadyExists) {
                state // ignore
            } else {
                state.copy(
                    saveDirs = state.saveDirs + SaveDir(name, uri),
                    currentDir = uri
                )
            }
        }

        viewModelScope.launch {
            val currentState = _state.value
            storage.updateDirs(currentState.saveDirs)
            storage.updateCurrentDir(currentState.currentDir)
        }
    }

    fun removeDir(uri: Uri) {
        _state.update { state ->
            val newDirs = state.saveDirs.filter { it.uri != uri }
            val newCurrentDir = if (state.currentDir == uri) {
                null // Если удаляем текущую выбранную, сбрасываем выбор
            } else {
                state.currentDir
            }

            state.copy(
                saveDirs = newDirs,
                currentDir = newCurrentDir
            )
        }

        viewModelScope.launch {
            val currentState = _state.value
            storage.updateDirs(currentState.saveDirs)
            storage.updateCurrentDir(currentState.currentDir)
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

    private fun onDisconnectClicked() {
        service?.onCommand(ServiceCommand.Stop)
    }

    private fun setCurrentDir(uri: Uri) {
        _state.update { it.copy(currentDir = uri) }
    }

    private fun requestAddDir() {
        viewModelScope.launch {
            _effects.send(MainScreenEffect.OpenFolderPicker)
        }
    }

    private fun onRoleSelected(role: Role) {
        viewModelScope.launch {
            pendingAction = PendingAction.StartService(role)
            val permissions = permissionPolicy.permissionsForServiceStart()
            _effects.send(MainScreenEffect.RequestPermissions(permissions))
        }
    }
}
