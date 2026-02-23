package org.sonnayasomnambula.nearby.exchanger.model

data class RemoteDevice(
    /// Уникальный идентификатор конечной точки, предоставляемый Nearby API
    val endpointId: String,
    /// [DiscoveredEndpointInfo.endpointName] или [ConnectionInfo.endpointName]
    val name: String,
    /// Текущая стадия обмена с удалённым устройством
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    /// Токен аутентификации (обычно 4-5 цифр), который нужно показать пользователю
    val authenticationToken: String? = null
) {
    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        AWAITING_CONFIRM,
        CONNECTED,
    }

    fun updated(
        authenticationToken: String? = null,
        connectionState: ConnectionState? = null
    ): RemoteDevice {
        return this.copy(
            connectionState = connectionState ?: this.connectionState,
            authenticationToken = authenticationToken ?: this.authenticationToken,
        )
    }

    fun updated(state: ConnectionState): RemoteDevice {
        return this.copy(connectionState = state)
    }
}