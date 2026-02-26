package org.sonnayasomnambula.nearby.exchanger.model

data class RemoteDevice(
    /// Уникальный идентификатор конечной точки, предоставляемый Nearby API
    val endpointId: String,
    /// [DiscoveredEndpointInfo.endpointName] или [ConnectionInfo.endpointName]
    val name: String,
    /// Текущая стадия обмена с удалённым устройством
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
) {
    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        AWAITING_CONFIRM,
        CONNECTED,
    }

    fun updated(state: ConnectionState): RemoteDevice {
        return this.copy(connectionState = state)
    }
}