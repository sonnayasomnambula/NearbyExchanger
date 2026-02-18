package org.sonnayasomnambula.nearby.exchanger.service

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import org.sonnayasomnambula.nearby.exchanger.model.Role

data class RemoteDevice(
    /// Уникальный идентификатор конечной точки, предоставляемый Nearby API
    val endpointId: String,
    /// [DiscoveredEndpointInfo.endpointName] или [ConnectionInfo.endpointName]
    val name: String,
    /// Токен аутентификации (обычно 4-5 цифр), который нужно показать пользователю
    val authenticationToken: String? = null,
    /// Текущая стадия обмена с удалённым устройством
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED
) {
    enum class ConnectionState {
        DISCONNECTED,
        DISCOVERED,
        CONNECTING,
        AWAITING_CONFIRM,
        CONNECTED,
    }

    fun update(
        name: String? = null,
        authenticationToken: String? = null,
        connectionState: ConnectionState? = null
    ): RemoteDevice {
        return this.copy(
            name = name ?: this.name,
            authenticationToken = authenticationToken ?: this.authenticationToken,
            connectionState = connectionState ?: this.connectionState,
        )
    }
}

sealed class ServiceState {
    object Initial : ServiceState()
    object Stopped : ServiceState()
    data class Running(
        val availableDevices: List<RemoteDevice> = emptyList()
    ) : ServiceState()
}

sealed class ServiceEvent {
}

sealed class ServiceCommand {
    object Stop : ServiceCommand()
}

interface ExchangeService {
    val state: StateFlow<ServiceState>
    val events: SharedFlow<ServiceEvent>
    fun onCommand(command: ServiceCommand)
    fun role() : Role
}