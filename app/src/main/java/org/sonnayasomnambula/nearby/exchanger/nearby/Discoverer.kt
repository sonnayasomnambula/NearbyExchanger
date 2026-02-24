package org.sonnayasomnambula.nearby.exchanger.nearby

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.update
import org.sonnayasomnambula.nearby.exchanger.LOG_TRACE
import org.sonnayasomnambula.nearby.exchanger.model.RemoteDevice
import org.sonnayasomnambula.nearby.exchanger.model.Role

class Discoverer(scope: CoroutineScope, context: Context)
    : NearbyExchanger(Role.DISCOVERER, scope, context) {

    override fun start() {
        startDiscovery()
    }

    override fun stop() {
        dropDevices()
        stopDiscovery()
    }

    override fun execute(command: ExchangeCommand) {
        when (command) {
            is ExchangeCommand.ConnectEndpoint -> connectEndpoint(command.endpointId)
            is ExchangeCommand.DisconnectEndpoint -> disconnectEndpoint(command.endpointId)
            is ExchangeCommand.StopSearching -> stopDiscovery()
            is ExchangeCommand.SendDirectory -> sendDirectory(command.uri)
            is ExchangeCommand.SendFile -> sendFile(command.uri)
        }
    }

    private fun startDiscovery() {
        setMode(ExchangeMode.Stopped)

        val discoveryOptions = DiscoveryOptions.Builder()
            .setStrategy(STRATEGY)
            .build()

        connectionsClient
            .startDiscovery(
                SERVICE_ID,
                endpointDiscoveryCallback,
                discoveryOptions)
            .addOnSuccessListener {
                Log.d(LOG_TRACE, "discovery started successfully!")
                setMode(ExchangeMode.Running(role()))
            }
            .addOnFailureListener { exception ->
                Log.e(LOG_TRACE, "discovery failed", exception)
                setMode(ExchangeMode.Failed(
                    message = "Failed to start discovery: ${exception.message}",
                    errorCode = (exception as? ApiException)?.statusCode,
                    throwable = exception
                ))
            }
    }

    private fun stopDiscovery() {
        if (mode() is ExchangeMode.Running) {
            connectionsClient.stopDiscovery()
            setMode(ExchangeMode.Stopped)
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Log.d(LOG_TRACE, "endpoint found: [$endpointId] ${info.endpointName}")
            setDevice(RemoteDevice(
                endpointId,
                info.endpointName,
                RemoteDevice.ConnectionState.DISCONNECTED
            ))
        }

        override fun onEndpointLost(endpointId: String) {
            Log.d(LOG_TRACE, "endpoint lost: $endpointId")
            removeDevice(endpointId)
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            Log.d(LOG_TRACE, "onConnectionInitiated: $endpointId, token: ${info.authenticationDigits}")
            setDevice(device(endpointId)?.updated(info.authenticationDigits, RemoteDevice.ConnectionState.AWAITING_CONFIRM))
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            when (result.status.statusCode) {
                ConnectionsStatusCodes.SUCCESS -> {
                    Log.d(LOG_TRACE, "onConnectionResult: SUCCESS for $endpointId")
                    val device = device(endpointId)?.updated(RemoteDevice.ConnectionState.CONNECTED)
                    if (device != null) {
                        setDevice(device)
                        sendEvent(ExchangeEvent.EndpointConnected(device))
                    }
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
            Log.d(LOG_TRACE, "onDisconnected: $endpointId")
            val device = device(endpointId)?.updated(RemoteDevice.ConnectionState.DISCONNECTED)
            if (device != null) {
                setDevice(device)
                sendEvent(ExchangeEvent.EndpointDisconnected(device))
            }
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

    private fun dropDevices() {
        for (device in _state.value.devices) {
            connectionsClient.disconnectFromEndpoint(device.endpointId)
        }
        _state.update { currentState ->
            currentState.copy(devices = emptyList())
        }
    }

    private fun connectEndpoint(endpointId: String) {
        Log.d(LOG_TRACE, "Connecting to endpoint: $endpointId")
        setDevice(device(endpointId)?.updated(RemoteDevice.ConnectionState.CONNECTING))

        connectionsClient.requestConnection(
            readableDeviceName(),
            endpointId,
            connectionLifecycleCallback
        ).addOnSuccessListener {
            Log.d(LOG_TRACE, "Connection request sent successfully to $endpointId")
        }.addOnFailureListener { exception ->
            Log.e(LOG_TRACE, "Failed to request connection to $endpointId", exception)
            setDevice(device(endpointId)?.updated(RemoteDevice.ConnectionState.DISCONNECTED))
        }
    }

    private fun disconnectEndpoint(endpointId: String) {
        Log.d(LOG_TRACE, "Disconnecting from endpoint: $endpointId")
        connectionsClient.disconnectFromEndpoint(endpointId)
        setDevice(device(endpointId)?.updated(RemoteDevice.ConnectionState.DISCONNECTED))
    }

    private fun sendFile(uri: Uri) {
        Log.d(LOG_TRACE, "send file $uri")
    }

    private fun sendDirectory(uri: Uri) {
        Log.d(LOG_TRACE, "send dir $uri")
    }
}