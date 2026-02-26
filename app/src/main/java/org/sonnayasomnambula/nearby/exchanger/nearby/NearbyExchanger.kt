package org.sonnayasomnambula.nearby.exchanger.nearby

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.compose.foundation.gestures.TransformableState
import androidx.documentfile.provider.DocumentFile
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.Payload
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
import org.sonnayasomnambula.nearby.exchanger.LOG_TRACE
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
        ExchangeState()
    )
    override val state: StateFlow<ExchangeState> = _state.asStateFlow()

    // События
    protected val _events = MutableSharedFlow<ExchangeEvent>()
    override val events: SharedFlow<ExchangeEvent> = _events.asSharedFlow()

    override fun setSaveDir(uri: Uri?) {
        fileTransfer.currentDir = uri
    }

    protected val connectionsClient = Nearby.getConnectionsClient(context)

    protected fun readableDeviceName()  = "${Build.MANUFACTURER} ${Build.MODEL}"

    protected fun setSearchingMode(mode: SearchingMode) {
        _state.update { currentState ->
            currentState.copy(searching = mode)
        }
    }

    protected fun searchingMode() : SearchingMode {
        return _state.value.searching
    }

    protected fun updateSession(transform: (SessionState) -> SessionState) {
        _state.update { it.copy(session = transform(it.session)) }
    }

    protected fun updateDevice(endpointId: String, state: RemoteDevice.ConnectionState) {
        when (val session = _state.value.session) {
            is SessionState.Connected -> {
                if (session.device.endpointId == endpointId) {
                    val device = session.device.updated(state)
                    updateSession {
                        SessionState.Connected(device)
                    }
                }
            }
            is SessionState.None -> {
                session.device(endpointId)?.let { device ->
                    updateSession {
                        session.withUpdatedDevice(device.updated(state))
                    }
                }
            }
        }
    }

    protected fun dropDevices() {
        when (val session = _state.value.session) {
            is SessionState.Connected -> {
                if (session.device.connectionState != RemoteDevice.ConnectionState.DISCONNECTED) {
                    connectionsClient.disconnectFromEndpoint(session.device.endpointId)
                    updateDevice(session.device.endpointId, RemoteDevice.ConnectionState.DISCONNECTED)
                }
            }
            is SessionState.None -> {
                _state.update { currentState ->
                    currentState.copy(session = SessionState.None())
                }
            }
        }
    }

    protected fun launch(block: suspend CoroutineScope.() -> Unit): Job {
        return coroutineScope.launch { block() }
    }

    protected fun sendEvent(event: ExchangeEvent) {
        launch {
            _events.emit(event)
        }
    }

    sealed class TransferState {
        object Idle : TransferState()

        data class ListReady(
            val files: List<FileEntry>,
            val requestId: Int
        ) : TransferState()

        data class FileReady(
            val files: List<FileEntry>,
            val fileEntry: FileEntry,
            val requestId: Int
        ) : TransferState()
    }

    protected inner class FileTransfer {

        var currentDir: Uri? = null
        var device: RemoteDevice? = null

        private var requestId = 0
        private var sendState : TransferState = TransferState.Idle
        private var recvState : TransferState = TransferState.Idle

        fun readPayload(payload: Payload) {
            when (payload.type) {
                Payload.Type.BYTES -> {
                    payload.asBytes()?.let { bytes ->
                        val message = String(bytes, Charsets.UTF_8)
                        readMessage(message)
                    }
                }
                Payload.Type.FILE -> {
                    payload.asFile()?.let { file ->
                        readFile(file)
                    }
                }
                else-> {}
            }
        }

        private fun readFile(file: Payload.File) {
            Log.d(LOG_TRACE, "Received file: ${file.toString()}")
        }

        private fun readMessage(message: String) {
            Log.d(LOG_TRACE, "Received message: $message")
            val serializer = FileListSerializer()

            try {
                val response = serializer.decodeResponse(message)
            } catch (e: Exception) {

            }


            try {
                when (recvState) {
                    is TransferState.Idle -> {
                        val decoded = serializer.decodeList(message)
                        recvState = TransferState.ListReady(decoded.files, decoded.request)

                        val jsonString = serializer.encode(decoded.request, FileListSerializer.READY)
                        sendPayload(jsonString)
                    }
                    is TransferState.ListReady -> {
                        val decoded = serializer.decodeEntry(message)
                        recvState = TransferState.FileReady(
                            (recvState as TransferState.ListReady).files,
                            FileEntry(decoded.path, decoded.size),
                            decoded.request
                        )
                    }

                    else -> {
                        throw IllegalStateException("invalid recvState")
                    }
                }
            } catch (e: Exception) {
                Log.w(LOG_TRACE, "FileTransfer: Unable to read message", e)
                recvState = TransferState.Idle
            }
        }

        fun sendFile(uri: Uri) {
            Log.d(LOG_TRACE, "send file $uri")
        }

        fun sendDirectory(uri: Uri) {
            if (sendState != TransferState.Idle) return

            try {
                val serializer = FileListSerializer()
                val outgoing = serializer.buildFileList(uri, context)

                Log.d(LOG_TRACE, "send directory $uri")
                Log.d(LOG_TRACE, "Total files: ${outgoing.size}")

                val requestId = ++this.requestId

                sendState = TransferState.ListReady(outgoing, requestId)

                val jsonString = serializer.encode(requestId, outgoing)
                sendPayload(jsonString)
            } catch (e: Exception) {
                Log.e(LOG_TRACE, "Failed to create manifest", e)
            }
        }

        private fun sendPayload(payload: String) {
            device?.let { device ->
                val bytes = payload.toByteArray(Charsets.UTF_8)
                connectionsClient.sendPayload(device.endpointId, Payload.fromBytes(bytes))
            }
        }
    }

    protected val fileTransfer = FileTransfer()
}

fun FileListSerializer.buildFileList(uri: Uri, context: Context): List<FileEntry> {
    val rootDoc = DocumentFile.fromTreeUri(context, uri)
        ?: throw IllegalArgumentException("Invalid directory URI")

    val entries = mutableListOf<FileEntry>()
    walkDirectory(rootDoc, "", entries)
    entries.sortBy { it.path }

    return entries
}

private fun FileListSerializer.walkDirectory(
    doc: DocumentFile,
    currentPath: String,
    entries: MutableList<FileEntry>
) {
    if (!doc.exists()) return

    if (doc.isDirectory) {
        doc.listFiles().forEach { child ->
            val newPath = if (currentPath.isEmpty()) {
                child.name ?: ""
            } else {
                "$currentPath/${child.name}"
            }
            walkDirectory(child, newPath, entries)
        }
    } else if (doc.isFile) {
        doc.name?.let { fileName ->
            entries.add(
                FileEntry(
                    path = currentPath,
                    size = doc.length()
                )
            )
        }
    }
}