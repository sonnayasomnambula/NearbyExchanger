package org.sonnayasomnambula.nearby.exchanger.nearby

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import org.sonnayasomnambula.nearby.exchanger.model.Role

class Discoverer(scope: CoroutineScope, context: Context)
    : NearbyExchanger(Role.ADVERTISER, scope, context) {
    override fun start() {
        super.start()
    }

    override fun stop() {
        super.stop()
    }

    override fun execute(command: ExchangeCommand) {

    }
}