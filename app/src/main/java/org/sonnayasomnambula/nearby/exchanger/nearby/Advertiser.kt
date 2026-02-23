package org.sonnayasomnambula.nearby.exchanger.nearby

import android.content.Context
import android.util.Log
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import kotlinx.coroutines.CoroutineScope
import org.sonnayasomnambula.nearby.exchanger.LOG_TRACE
import org.sonnayasomnambula.nearby.exchanger.model.RemoteDevice
import org.sonnayasomnambula.nearby.exchanger.model.Role

class Advertiser(scope: CoroutineScope, context: Context)
    : NearbyExchanger(Role.ADVERTISER, scope, context) {
    override fun execute(command: ExchangeCommand) {
        if (command is ExchangeCommand.StopSearching) {
            stopAdvertising()
        }
    }

    override fun start() {
        startAdvertising()
    }

    override fun stop() {
        stopAdvertising()
    }

    private fun startAdvertising() {
        setMode(ExchangeMode.Stopped)

        val advertisingOptions = AdvertisingOptions.Builder()
            .setStrategy(STRATEGY)
            .build()

        connectionsClient
            .startAdvertising(
                readableDeviceName(),
                SERVICE_ID,
                connectionLifecycleCallback,
                advertisingOptions
            )
            // 2. Более короткая запись для лямбд. "_" означает, что параметр не используется.
            .addOnSuccessListener { _ ->
                Log.d(LOG_TRACE, "advertising started successfully!")
                setMode(ExchangeMode.Running(role()))
            }
            .addOnFailureListener { exception ->
                Log.e(LOG_TRACE, "advertising failed", exception)
                setMode(ExchangeMode.Failed(
                    message = "Failed to start advertising: ${exception.message}",
                    errorCode = (exception as? ApiException)?.statusCode,
                    throwable = exception
                ))
            }
    }

    fun stopAdvertising() {
        if (mode() is ExchangeMode.Running) {
            connectionsClient.stopAdvertising()
            setMode(ExchangeMode.Stopped)
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            Log.d("Advertiser", "Auto-accepting connection from $endpointId")
            connectionsClient.acceptConnection(endpointId, payloadCallback)
            setDevice(RemoteDevice(
                endpointId,
                connectionInfo.endpointName,
                RemoteDevice.ConnectionState.CONNECTING
            ))
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            when (result.status.statusCode) {
                ConnectionsStatusCodes.SUCCESS -> {
                    Log.d(LOG_TRACE, "onConnectionResult: SUCCESS for $endpointId")
                    setDevice(device(endpointId)?.updated(RemoteDevice.ConnectionState.CONNECTED))
                    sendEvent(ExchangeEvent.EndpointConnected(endpointId))
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                    Log.d(LOG_TRACE, "onConnectionResult: REJECTED for $endpointId")
                    setDevice(device(endpointId)?.updated(RemoteDevice.ConnectionState.DISCONNECTED))
                }
                else -> {
                    Log.e(LOG_TRACE, "onConnectionResult: FAILURE for $endpointId, code: ${result.status.statusCode}")
                    setDevice(device(endpointId)?.updated(RemoteDevice.ConnectionState.DISCONNECTED))
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.d(LOG_TRACE, "client $endpointId disconnected")
            setDevice(device(endpointId)?.updated(RemoteDevice.ConnectionState.DISCONNECTED))
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            Log.d(LOG_TRACE, "onPayloadReceived from $endpointId, type: ${payload.type}")

            when (payload.type) {
                Payload.Type.BYTES -> {
                    // Получены байты данных
                    payload.asBytes()?.let { bytes ->
                        val message = String(bytes, Charsets.UTF_8)
                        Log.d(LOG_TRACE, "Received message: $message")
                        // Обработка полученного сообщения
                    }
                }
                Payload.Type.FILE -> {
                    // Получен файл
                    payload.asFile()?.let { file ->
                        Log.d(LOG_TRACE, "Received file: ${file.toString()}")
                        // Обработка полученного файла
                    }
                }
                Payload.Type.STREAM -> {
                    // Получен поток данных
                    Log.d(LOG_TRACE, "Received stream")
                    // Обработка потока
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // Обновление прогресса передачи
            Log.d(LOG_TRACE, "onPayloadTransferUpdate from $endpointId, bytes: ${update.bytesTransferred}/${update.totalBytes}")
        }
    }
}