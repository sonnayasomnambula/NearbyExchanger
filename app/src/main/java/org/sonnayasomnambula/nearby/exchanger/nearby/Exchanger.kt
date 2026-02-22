package org.sonnayasomnambula.nearby.exchanger.nearby

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import org.sonnayasomnambula.nearby.exchanger.model.RemoteDevice
import org.sonnayasomnambula.nearby.exchanger.model.Role

sealed class ExchangeState {
    object Initial : ExchangeState()
    object Stopped : ExchangeState()
    data class Running(
        val availableDevices: List<RemoteDevice> = emptyList()
    ) : ExchangeState()

    data class Failed(
        val message: String,
        val errorCode: Int? = null,
        val throwable: Throwable? = null
    ) : ExchangeState()
}

sealed class ExchangeEvent {
}

sealed class ExchangeCommand {

}

interface Exchanger {
    fun role() : Role
    val state: StateFlow<ExchangeState>
    val events: SharedFlow<ExchangeEvent>
    fun execute(command: ExchangeCommand)
    fun start()
    fun stop()
}