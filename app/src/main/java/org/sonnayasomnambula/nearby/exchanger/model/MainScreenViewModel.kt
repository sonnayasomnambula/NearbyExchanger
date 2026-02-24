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
import org.sonnayasomnambula.nearby.exchanger.LOG_TRACE
import org.sonnayasomnambula.nearby.exchanger.__func__
import org.sonnayasomnambula.nearby.exchanger.app.Storage
import org.sonnayasomnambula.nearby.exchanger.nearby.ExchangeCommand
import org.sonnayasomnambula.nearby.exchanger.nearby.Exchanger
import org.sonnayasomnambula.nearby.exchanger.nearby.ExchangeEvent
import org.sonnayasomnambula.nearby.exchanger.nearby.ExchangeMode
import org.sonnayasomnambula.nearby.exchanger.nearby.ExchangeState

enum class Role { ADVERTISER, DISCOVERER }

enum class ConnectionState { DISCONNECTED, ADVERTISING, DISCOVERING, CONNECTED, ERROR }

val ConnectionState.correspondingRole: Role?
    get() = when (this) {
        ConnectionState.ADVERTISING -> Role.ADVERTISER
        ConnectionState.DISCOVERING -> Role.DISCOVERER
        else -> null
    }

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
    val devices: List<RemoteDevice> = emptyList(),
)

// activity/composable => model
sealed interface MainScreenEvent {
    data object ActivityStarted: MainScreenEvent
    data object AddDirectoryRequested : MainScreenEvent
    data class RemoveDirectoryRequested(val uri: Uri) : MainScreenEvent
    data class DirectorySelected(val uri: Uri) : MainScreenEvent
    data class RoleSelected(val role: Role) : MainScreenEvent
    data object DisconnectClicked : MainScreenEvent
    data object SendFileClicked : MainScreenEvent
    data object SendFolderClicked : MainScreenEvent
    data class ServiceStarted(val role: Role): MainScreenEvent
    data object ServiceStopped: MainScreenEvent
    data class DirectoryAccessChecked(val uri: Uri, val hasAccess: Boolean) : MainScreenEvent
    data class PermissionsResult(val granted: Boolean) : MainScreenEvent
    data class DeviceClicked(val device: RemoteDevice) : MainScreenEvent
}

// model => activity
sealed interface MainScreenEffect {
    data class CheckDirectoryAccess(val uri: Uri) : MainScreenEffect
    data class RequestPermissions(val permissions: List<String>) : MainScreenEffect
    data class StartForegroundService(val role: Role) : MainScreenEffect
    data object StopForegroundService : MainScreenEffect
    data class ShowDisconnectedAlert(val device: RemoteDevice) : MainScreenEffect
    data class PickFile(val readOnly: Boolean) : MainScreenEffect
    data class PickDirectory(val readOnly: Boolean) : MainScreenEffect
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

    private val _screenState = MutableStateFlow(MainScreenState())
    val screenState: StateFlow<MainScreenState> = _screenState

    private val _activityEffects = Channel<MainScreenEffect>(Channel.BUFFERED)
    val activityEffects = _activityEffects.receiveAsFlow()

    private var exchanger: Exchanger? = null

    private val _exchangerState = MutableStateFlow<ExchangeState>(ExchangeState())
    val exchangerState: StateFlow<ExchangeState> = _exchangerState.asStateFlow()

    private val _exchangerEvents = MutableSharedFlow<ExchangeEvent>()
    val exchangerEvents: SharedFlow<ExchangeEvent> = _exchangerEvents.asSharedFlow()

    private sealed interface PendingAction {
        data class StartService(val role: Role) : PendingAction
        data object AddSaveDirectory : PendingAction
        data object SendFile : PendingAction
        data object SendDirectory : PendingAction
    }

    private var pendingAction: PendingAction? = null

    fun onScreenEvent(event: MainScreenEvent) {
        Log.d(LOG_TRACE, "model: ${event.toString()}")
        when (event) {
            is MainScreenEvent.ActivityStarted -> onActivityStarted()
            is MainScreenEvent.ServiceStarted -> onServiceStarted(event.role)
            is MainScreenEvent.ServiceStopped -> onServiceStopped()
            is MainScreenEvent.RoleSelected -> onRoleSelected(event.role)
            is MainScreenEvent.AddDirectoryRequested -> requestAddDir()
            is MainScreenEvent.DirectorySelected -> setCurrentDir(event.uri)
            is MainScreenEvent.RemoveDirectoryRequested -> removeDir(event.uri)
            is MainScreenEvent.SendFileClicked -> onSendFileClicked()
            is MainScreenEvent.SendFolderClicked -> onSendFolderClicked()
            is MainScreenEvent.DisconnectClicked -> onDisconnectClicked()
            is MainScreenEvent.DirectoryAccessChecked -> onDirectoryAccessChecked(event.uri, event.hasAccess)
            is MainScreenEvent.PermissionsResult -> onPermissionResult(granted = event.granted)
            is MainScreenEvent.DeviceClicked -> onDeviceClicked(event.device)
        }
    }

    private fun onDeviceClicked(device: RemoteDevice) {
        when (device.connectionState) {
            RemoteDevice.ConnectionState.DISCONNECTED -> {
                exchanger?.execute(ExchangeCommand.ConnectEndpoint(device.endpointId))
            }
            else -> {
                exchanger?.execute(ExchangeCommand.DisconnectEndpoint(device.endpointId))
            }
        }

    }

    private fun onPermissionResult(granted: Boolean) {
        if (granted) {
            viewModelScope.launch {
                val action = requireNotNull(pendingAction)
                pendingAction = null
                if (action is PendingAction.StartService) {
                    _activityEffects.send(MainScreenEffect.StartForegroundService(action.role))
                }
            }
        }
    }

    private fun onServiceStarted(role: Role) {
        // all be done in subscribeToExchanger()
    }

    private fun onServiceStopped() {
        viewModelScope.launch {
            _screenState.update { currentState ->
                currentState.copy(
                    connectionState = ConnectionState.DISCONNECTED,
                    currentRole = null,
                    statusText = "",
                    devices = emptyList()
                )
            }
        }
    }

    private fun determineConnectionState(exchangerState: ExchangeState): ConnectionState {
        return when (val mode = exchangerState.mode) {
            is ExchangeMode.Failed -> ConnectionState.ERROR

            is ExchangeMode.Running -> {
                when (mode.role) {
                    Role.ADVERTISER -> ConnectionState.ADVERTISING
                    Role.DISCOVERER -> ConnectionState.DISCOVERING
                }
            }

            ExchangeMode.Stopped -> {
                // Если есть хотя бы одно подключённое устройство
                if (exchangerState.devices.any { it.connectionState == RemoteDevice.ConnectionState.CONNECTED }) {
                    ConnectionState.CONNECTED
                } else {
                    ConnectionState.DISCONNECTED
                }
            }
        }
    }

    private fun determineStatusText(exchangerState: ExchangeState): String {
        return when (val mode = exchangerState.mode) {
            is ExchangeMode.Failed -> mode.message
            else -> ""
        }
    }

    fun subscribeToExchanger(exchanger: Exchanger) {
        Log.d(LOG_TRACE, __func__())
        this.exchanger = exchanger

        viewModelScope.launch {
            exchanger.state.collect { exchangerState ->
                _exchangerState.value = exchangerState

                _screenState.update { currentState ->
                    currentState.copy(
                        connectionState = determineConnectionState(exchangerState),
                        currentRole = exchanger.role(),
                        devices = exchangerState.devices,
                        statusText = determineStatusText(exchangerState)
                    )
                }
            }
        }

        viewModelScope.launch {
            exchanger.events.collect { event ->
                when (event) {
                    is ExchangeEvent.EndpointConnected -> {
                        exchanger.execute(ExchangeCommand.StopSearching)
                    }
                    is ExchangeEvent.EndpointDisconnected -> {
                        _activityEffects.send(MainScreenEffect.ShowDisconnectedAlert(event.device))
                    }
                }
                _exchangerEvents.emit(event)
            }
        }
    }

    private fun onDirectoryAccessChecked(uri: Uri, hasAccess: Boolean) {
        if (!hasAccess) {
            viewModelScope.launch {
                val curState = _screenState.value
                val updatedDirs = curState.saveDirs.filterNot { it.toString() == uri.toString() }
                val updatedCurrentDir = if (curState.currentDir?.toString() == uri.toString()) null else curState.currentDir

                storage.updateDirs(updatedDirs)
                storage.updateCurrentDir(updatedCurrentDir)

                _screenState.update { currentState ->
                    currentState.copy(
                        saveDirs = updatedDirs,
                        currentDir = updatedCurrentDir,
                    )
                }

                pendingAction = PendingAction.AddSaveDirectory
                _activityEffects.send(MainScreenEffect.PickDirectory(readOnly = false))
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

            _screenState.update { it.copy(
                saveDirs = dirs,
                currentDir = currentDir
            ) }

            savedState.currentDir?.let { uri ->
                if (isValidDirectory(uri)) {
                    _activityEffects.send(MainScreenEffect.CheckDirectoryAccess(uri))
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

    fun filePicked(uri: Uri) {
        val action = requireNotNull(pendingAction)
        pendingAction = null
        if (action is PendingAction.SendFile) {
            exchanger?.execute(ExchangeCommand.SendFile(uri))
        }
    }

    fun directoryPicked(uri: Uri, name: String) {
        val action = requireNotNull(pendingAction)
        pendingAction = null
        if (action is PendingAction.AddSaveDirectory) {
            addSaveDirectory(uri, name)
        }

        if (action is PendingAction.SendDirectory) {
            exchanger?.execute(ExchangeCommand.SendDirectory(uri))
        }
    }

    private fun addSaveDirectory(uri: Uri, name: String) {
        _screenState.update { state ->
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
            val currentState = _screenState.value
            storage.updateDirs(currentState.saveDirs)
            storage.updateCurrentDir(currentState.currentDir)
        }
    }

    fun removeDir(uri: Uri) {
        _screenState.update { state ->
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
            val currentState = _screenState.value
            storage.updateDirs(currentState.saveDirs)
            storage.updateCurrentDir(currentState.currentDir)
        }
    }

    private fun onSendFileClicked() {
        viewModelScope.launch {
            pendingAction = PendingAction.SendFile
            _activityEffects.send(MainScreenEffect.PickFile(readOnly = true))
        }
    }

    private fun onSendFolderClicked() {
        viewModelScope.launch {
            pendingAction = PendingAction.SendDirectory
            _activityEffects.send(MainScreenEffect.PickDirectory(readOnly = true))
        }
    }

    private fun onDisconnectClicked() {
        viewModelScope.launch {
            _activityEffects.send(MainScreenEffect.StopForegroundService)
        }
    }

    private fun setCurrentDir(uri: Uri) {
        _screenState.update { it.copy(currentDir = uri) }
    }

    private fun requestAddDir() {
        viewModelScope.launch {
            pendingAction = PendingAction.AddSaveDirectory
            _activityEffects.send(MainScreenEffect.PickDirectory(readOnly = false))
        }
    }

    private fun onRoleSelected(role: Role) {
        viewModelScope.launch {
            pendingAction = PendingAction.StartService(role)
            val permissions = permissionPolicy.permissionsFor(role)
            _activityEffects.send(MainScreenEffect.RequestPermissions(permissions))
        }
    }
}
