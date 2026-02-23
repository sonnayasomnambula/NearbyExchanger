package org.sonnayasomnambula.nearby.exchanger.nearby

import android.content.Context
import android.os.Build
import com.google.android.gms.nearby.Nearby
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.sonnayasomnambula.nearby.exchanger.model.RemoteDevice
import org.sonnayasomnambula.nearby.exchanger.model.Role

abstract class NearbyExchanger(
    private val exchangerRole: Role,
    private val coroutineScope: CoroutineScope,
    protected val context: Context
) : Exchanger {
    override fun role(): Role = exchangerRole

    // Состояние
    protected val _state = MutableStateFlow(
        ExchangeState(
            mode = ExchangeMode.Stopped,
            devices = emptyList()
        )
    )
    override val state: StateFlow<ExchangeState> = _state.asStateFlow()

    // События
    protected val _events = MutableSharedFlow<ExchangeEvent>()
    override val events: SharedFlow<ExchangeEvent> = _events.asSharedFlow()

    protected val connectionsClient = Nearby.getConnectionsClient(context)

    protected fun readableDeviceName()  = "${Build.MANUFACTURER} ${Build.MODEL}"

    protected fun setDevice(device: RemoteDevice?) {
        if (device != null) {
            _state.update { currentState ->
                val devices = currentState.devices.associateBy { it.endpointId }.toMutableMap()
                devices[device.endpointId] = device
                currentState.copy(devices = devices.values.toList().sortedBy { it.name })
            }
        }
    }

    protected fun removeDevice(endpointId: String) {
        _state.update { currentState ->
            val devices = currentState.devices.filterNot { it.endpointId == endpointId }
            currentState.copy(devices = devices)
        }
    }

    protected fun device(endpointId: String) : RemoteDevice? {
        return _state.value.devices.find { it.endpointId == endpointId }
    }

    protected fun setMode(mode: ExchangeMode) {
        _state.update { currentState ->
            currentState.copy(mode = mode)
        }
    }

    protected fun mode() : ExchangeMode {
        return _state.value.mode
    }

    protected fun launch(block: suspend CoroutineScope.() -> Unit): Job {
        return coroutineScope.launch { block() }
    }

    protected fun sendEvent(event: ExchangeEvent) {
        launch {
            _events.emit(event)
        }
    }
}