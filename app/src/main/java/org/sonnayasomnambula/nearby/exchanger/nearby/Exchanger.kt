package org.sonnayasomnambula.nearby.exchanger.nearby

import android.net.Uri
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import org.sonnayasomnambula.nearby.exchanger.model.RemoteDevice
import org.sonnayasomnambula.nearby.exchanger.model.Role

/// Advertising / discovery state
sealed class SearchingMode {
    object Stopped : SearchingMode()
    object Starting : SearchingMode()
    data class Running(
        val role: Role
    ) : SearchingMode()
    data class Failed(
        val message: String,
        val errorCode: Int? = null,
        val throwable: Throwable? = null
    ) : SearchingMode()
}

/// Session for P2P_STAR strategy
sealed class SessionState {
    data class None(
        /// discovered devices
        val devices: List<RemoteDevice> = emptyList()
    ) : SessionState() {
        fun device(endpointId: String): RemoteDevice? =
            devices.find { it.endpointId == endpointId }
        fun withUpdatedDevice(device: RemoteDevice): None {
            val map = devices.associateBy { it.endpointId }.toMutableMap()
            map[device.endpointId] = device
            return copy(
                devices = map.values.sortedBy { it.name }
            )
        }

        fun withoutDevice(endpointId: String): None =
            copy(devices = devices.filterNot { it.endpointId == endpointId })
    }
    data class Connected(
        /// single connected peer
        val device: RemoteDevice
    ) : SessionState()
}

data class TransferStatistics(
    val queue: List<String> = emptyList(),
    val current: String = "",
    val totalSize: Long = 0,
    val totalProgress: Long = 0,
)

data class TransferProgress(
    val currentSize: Long = 0,
    val currentProgress: Long = 0
)

data class TransferState(
    val statistics: TransferStatistics = TransferStatistics(),
    val progress: TransferProgress = TransferProgress()
) {
    fun transferred() : Long = statistics.totalProgress + progress.currentProgress
}

data class ExchangeState(
    val searching: SearchingMode = SearchingMode.Stopped,
    val session: SessionState = SessionState.None(),
    val incoming: TransferState = TransferState(),
    val outgoing: TransferState = TransferState()
)

sealed class ExchangeEvent {
    data class EndpointConnected(val device: RemoteDevice) : ExchangeEvent()
    data class EndpointDisconnected(val device: RemoteDevice) : ExchangeEvent()
    data class RemoteError(val message: String) : ExchangeEvent()
    data class StartError(val throwable: Throwable) : ExchangeEvent()
}

sealed class ExchangeCommand {
    data class ConnectEndpoint(val endpointId: String) : ExchangeCommand()
    data class DisconnectEndpoint(val endpointId: String) : ExchangeCommand()
    data class SendFile(val uri: Uri) : ExchangeCommand()
    data class SendDirectory(val uri: Uri) : ExchangeCommand()
    data object StopTransfers : ExchangeCommand()
}

interface Exchanger {
    fun role() : Role
    fun setSaveDir(uri: Uri?)
    val state: StateFlow<ExchangeState>
    val events: SharedFlow<ExchangeEvent>
    fun execute(command: ExchangeCommand)
    fun start()
    fun stop()
}