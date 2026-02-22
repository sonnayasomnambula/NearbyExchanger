package org.sonnayasomnambula.nearby.exchanger.nearby

import android.content.Context
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.sonnayasomnambula.nearby.exchanger.model.Role

abstract class NearbyExchanger(
    private val exchangerRole: Role,
    private val coroutineScope: CoroutineScope,
    protected val context: Context
) : Exchanger {
    override fun role(): Role = exchangerRole

    // Состояние
    protected val _state = MutableStateFlow<ExchangeState>(ExchangeState.Initial)
    override val state: StateFlow<ExchangeState> = _state.asStateFlow()

    // События
    protected val _events = MutableSharedFlow<ExchangeEvent>()
    override val events: SharedFlow<ExchangeEvent> = _events.asSharedFlow()

    protected fun getLocalUserName()  = "${Build.MANUFACTURER} ${Build.MODEL}"

    protected fun launch(block: suspend CoroutineScope.() -> Unit): Job {
        return coroutineScope.launch { block() }
    }

    override fun start() {
        _state.value = ExchangeState.Initial
    }

    override fun stop() {
        _state.value = ExchangeState.Stopped
    }
}