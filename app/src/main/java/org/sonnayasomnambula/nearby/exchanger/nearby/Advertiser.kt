package org.sonnayasomnambula.nearby.exchanger.nearby

import android.content.Context
import android.util.Log
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import kotlinx.coroutines.CoroutineScope
import org.sonnayasomnambula.nearby.exchanger.LOG_TRACE
import org.sonnayasomnambula.nearby.exchanger.model.Role

class Advertiser(scope: CoroutineScope, context: Context)
    : NearbyExchanger(Role.ADVERTISER, scope, context) {
    override fun execute(command: ExchangeCommand) {

    }

    override fun start() {
        super.start()
        startAdvertising()
    }

    override fun stop() {
        Nearby.getConnectionsClient(context).stopAdvertising()
        super.stop()
    }

    private fun startAdvertising() {
        val advertisingOptions = AdvertisingOptions.Builder()
            .setStrategy(STRATEGY)
            .build()

        Nearby.getConnectionsClient(context)
            .startAdvertising(
                getLocalUserName(),
                SERVICE_ID,
                connectionLifecycleCallback,
                advertisingOptions
            )
            // 2. Более короткая запись для лямбд. "_" означает, что параметр не используется.
            .addOnSuccessListener { _ ->
                Log.d(LOG_TRACE, "advertising started successfully!")
                _state.value = ExchangeState.Running(emptyList())
            }
            .addOnFailureListener { exception ->
                Log.e(LOG_TRACE, "advertising failed", exception)
                _state.value = ExchangeState.Failed(
                    message = "Failed to start advertising: ${exception.message}",
                    errorCode = (exception as? ApiException)?.statusCode,
                    throwable = exception
                )
            }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {

        }

        override fun onEndpointLost(endpointId: String) {
            // Ранее обнаруженный endpoint (устройство) стал недоступен.
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {

        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    // Мы подключены! Теперь можно отправлять и получать данные.
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                    // Соединение было отклонено одной или обеими сторонами.
                }
                ConnectionsStatusCodes.STATUS_ERROR -> {
                    // Соединение было разорвано до того, как его успели принять.
                }
                else -> {
                    // Неизвестный код статуса
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            // Мы отключились от этого endpoint'а.
            // Больше нельзя отправлять или получать данные.
        }
    }
}