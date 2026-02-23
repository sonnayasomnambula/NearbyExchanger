package org.sonnayasomnambula.nearby.exchanger.nearby

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import org.sonnayasomnambula.nearby.exchanger.model.RemoteDevice
import org.sonnayasomnambula.nearby.exchanger.model.Role

sealed class ExchangeMode {
    object Stopped : ExchangeMode()
    data class Running(
        val role: Role
    ) : ExchangeMode()
    data class Failed(
        val message: String,
        val errorCode: Int? = null,
        val throwable: Throwable? = null
    ) : ExchangeMode()
}

data class ExchangeState(
    val mode: ExchangeMode = ExchangeMode.Stopped,
    val devices: List<RemoteDevice> = emptyList()
)

sealed class ExchangeEvent {
    data class EndpointConnected(val endpointId: String) : ExchangeEvent()
}

sealed class ExchangeCommand {
    data class ConnectEndpoint(val endpointId: String) : ExchangeCommand()
    data class DisconnectEndpoint(val endpointId: String) : ExchangeCommand()
    object StopSearching : ExchangeCommand()
}

interface Exchanger {
    fun role() : Role
    val state: StateFlow<ExchangeState>
    val events: SharedFlow<ExchangeEvent>
    fun execute(command: ExchangeCommand)
    fun start()
    fun stop()
}