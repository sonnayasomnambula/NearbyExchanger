package org.sonnayasomnambula.nearby.exchanger.nearby

import android.content.Context
import android.util.Log
import com.google.android.gms.common.api.ApiException
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
import org.sonnayasomnambula.nearby.exchanger.LOG_TRACE
import org.sonnayasomnambula.nearby.exchanger.model.RemoteDevice
import org.sonnayasomnambula.nearby.exchanger.model.Role

class Discoverer(scope: CoroutineScope, context: Context)
    : NearbyExchanger(Role.DISCOVERER, scope, context) {

    override fun start() {
        startDiscovery()
    }

    override fun stop() {
        dropSession()
        stopDiscovery()
    }

    override fun execute(command: ExchangeCommand) {
        when (command) {
            is ExchangeCommand.ConnectEndpoint -> connectEndpoint(command.endpointId)
            is ExchangeCommand.DisconnectEndpoint -> disconnectEndpoint(command.endpointId)
            is ExchangeCommand.SendDirectory -> fileTransfer.sendDirectory(command.uri)
            is ExchangeCommand.SendFile -> fileTransfer.sendFile(command.uri)
        }
    }

    private fun startDiscovery() {
        setSearchingMode(SearchingMode.Stopped)

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
                setSearchingMode(SearchingMode.Running(role()))
            }
            .addOnFailureListener { exception ->
                Log.e(LOG_TRACE, "discovery failed", exception)
                setSearchingMode(SearchingMode.Failed(
                    message = "Failed to start discovery: ${exception.message}",
                    errorCode = (exception as? ApiException)?.statusCode,
                    throwable = exception
                ))
            }
    }

    private fun stopDiscovery() {
        if (searchingMode() is SearchingMode.Running) {
            connectionsClient.stopDiscovery()
            setSearchingMode(SearchingMode.Stopped)
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Log.d(LOG_TRACE, "endpoint found: [$endpointId] ${info.endpointName}")

            val device = RemoteDevice(
                endpointId = endpointId,
                name = info.endpointName,
                connectionState = RemoteDevice.ConnectionState.DISCONNECTED
            )

            updateSession { session ->
                when (session) {
                    is SessionState.None -> {
                        session.withUpdatedDevice(device)
                    }
                    is SessionState.Connected -> {
                        Log.w(LOG_TRACE, "Already connected, ignoring found $endpointId (forgot to stop discovery?)")
                        session
                    }
                }
            }
        }

        override fun onEndpointLost(endpointId: String) {
            Log.d(LOG_TRACE, "endpoint lost: $endpointId")
            updateSession { session ->
                when (session) {
                    is SessionState.None -> {
                        session.withoutDevice(endpointId)
                    }
                    is SessionState.Connected -> {
                        if (session.device.endpointId == endpointId) {
                            SessionState.Connected(session.device.updated(RemoteDevice.ConnectionState.DISCONNECTED))
                        } else {
                            session
                        }
                    }
                }
            }
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            Log.d(LOG_TRACE, "onConnectionInitiated: $endpointId, token: ${info.authenticationDigits}")
            updateDevice(endpointId, RemoteDevice.ConnectionState.AWAITING_CONFIRM)
            connectionsClient.acceptConnection(endpointId, payloadCallback)
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

                            stopDiscovery()
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
            fileTransfer.readPayload(payload)
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // Обновление прогресса передачи
            Log.d(LOG_TRACE, "onPayloadTransferUpdate from $endpointId, bytes: ${update.bytesTransferred}/${update.totalBytes}")
        }
    }

    private fun connectEndpoint(endpointId: String) {
        Log.d(LOG_TRACE, "Connecting to endpoint: $endpointId")
        updateDevice(endpointId, RemoteDevice.ConnectionState.CONNECTING)

        connectionsClient.requestConnection(
            readableDeviceName(),
            endpointId,
            connectionLifecycleCallback
        ).addOnSuccessListener {
            Log.d(LOG_TRACE, "Connection request sent successfully to $endpointId")
        }.addOnFailureListener { exception ->
            Log.e(LOG_TRACE, "Failed to request connection to $endpointId", exception)
            updateDevice(endpointId, RemoteDevice.ConnectionState.DISCONNECTED)
        }
    }

    private fun disconnectEndpoint(endpointId: String) {
        Log.d(LOG_TRACE, "Disconnecting from endpoint: $endpointId")
        connectionsClient.disconnectFromEndpoint(endpointId)
        updateDevice(endpointId, RemoteDevice.ConnectionState.DISCONNECTED)
    }
}