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

    override fun start() {
        startAdvertising()
    }

    override fun stop() {
        dropSession()
        stopAdvertising()
    }

    override fun execute(command: ExchangeCommand) {
        when (command) {
            is ExchangeCommand.SendDirectory -> fileTransfer.sendDirectory(command.uri)
            is ExchangeCommand.SendFile -> fileTransfer.sendFile(command.uri)
            else -> {}
        }
    }

    private fun startAdvertising() {
        setSearchingMode(SearchingMode.Stopped)

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
                setSearchingMode(SearchingMode.Running(role()))
            }
            .addOnFailureListener { exception ->
                Log.e(LOG_TRACE, "advertising failed", exception)
                setSearchingMode(SearchingMode.Failed(
                    message = "Failed to start advertising: ${exception.message}",
                    errorCode = (exception as? ApiException)?.statusCode,
                    throwable = exception
                ))
            }
    }

    fun stopAdvertising() {
        if (searchingMode() is SearchingMode.Running) {
            connectionsClient.stopAdvertising()
            setSearchingMode(SearchingMode.Stopped)
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            Log.d(LOG_TRACE, "Auto-accepting connection from $endpointId")
            connectionsClient.acceptConnection(endpointId, payloadCallback)

            val device = RemoteDevice(
                endpointId = endpointId,
                name = connectionInfo.endpointName,
                connectionState = RemoteDevice.ConnectionState.AWAITING_CONFIRM
            )

            updateSession { session ->
                when (session) {
                    is SessionState.None -> {
                        session.withUpdatedDevice(device)
                    }
                    is SessionState.Connected -> {
                        Log.w(LOG_TRACE, "Already connected, ignoring connection from $endpointId")
                        session
                    }
                }
            }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            when (result.status.statusCode) {
                ConnectionsStatusCodes.SUCCESS -> {
                    Log.d(LOG_TRACE, "onConnectionResult: SUCCESS for $endpointId")

                    when (val session = _state.value.session) {
                        is SessionState.None -> {
                            val device =
                                session.device(endpointId) ?:
                                RemoteDevice(endpointId, "Unknown device")

                            val connectedDevice =
                                device.updated(RemoteDevice.ConnectionState.CONNECTED)
                            updateSession {
                                SessionState.Connected(connectedDevice)
                            }

                            stopAdvertising()
                            sendEvent(ExchangeEvent.EndpointConnected(connectedDevice))
                        }
                        is SessionState.Connected -> {
                            Log.w(LOG_TRACE, "Unexpected connection from $endpointId: already connected to ${session.device.endpointId}; drop")
                            connectionsClient.disconnectFromEndpoint(endpointId)
                        }
                    }
                }
                else -> {
                    Log.e(LOG_TRACE, "onConnectionResult: FAILURE for $endpointId, code: ${result.status.statusCode}")
                    updateDevice(endpointId, RemoteDevice.ConnectionState.DISCONNECTED)
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.d(LOG_TRACE, "client $endpointId disconnected")
            when (val session = _state.value.session) {
                is SessionState.Connected -> {
                    val device = session.device
                    if (device.endpointId == endpointId) {
                        updateSession { SessionState.None() }
                        sendEvent(ExchangeEvent.EndpointDisconnected(
                            device.updated(RemoteDevice.ConnectionState.DISCONNECTED)))
                    }
                }
                else -> {}
            }
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            Log.d(LOG_TRACE, "onPayloadReceived from $endpointId, type: ${payload.type}")
            fileTransfer.readPayload(payload)
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // Обновление прогресса передачи
            Log.d(LOG_TRACE, "onPayloadTransferUpdate from $endpointId, bytes: ${update.bytesTransferred}/${update.totalBytes}")
        }
    }
}