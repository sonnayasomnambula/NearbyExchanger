package org.sonnayasomnambula.nearby.exchanger.nearby

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Log
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
import java.io.IOException

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
        fileTransfer.setSaveDir(uri)
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

    protected fun dropSession() {
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

    class TransferableFile (
        private val uri: Uri,
        private val contentResolver: ContentResolver,
        override val path: String = "",
        override val size: Long = 0,
    ) : TransferEngine.FileImpl() {

        fun openDescriptor(mode: String = "r"): ParcelFileDescriptor {
            return contentResolver.openFileDescriptor(uri, mode)
                ?: throw IOException("Cannot open file descriptor for $uri")
        }

        override fun save(destination: TransferEngine.File) {
            val targetUri = (destination as TransferableFile).uri
            createDirectories(targetUri, contentResolver)
            moveTo(uri, targetUri, contentResolver)
        }

        override fun createChild(entry: JsonSerializer.FileEntry) : TransferEngine.File {
            val uri = this.uri
                .buildUpon()
                .appendPath(entry.path)
                .build()

            return TransferableFile(
                uri,
                this.contentResolver,
                entry.path,
                entry.size
            )
        }

        companion object {
            fun walkDirectory(root: Uri, context: Context) : List<TransferableFile> {
                val rootDocument = DocumentFile.fromTreeUri(context, root)
                    ?: throw IllegalArgumentException("Invalid directory URI: $root")

                return buildList {
                    val rootName = rootDocument.name ?: ""
                    collectFiles(rootDocument, rootName, context, this)
                }
            }

            private fun collectFiles(
                document: DocumentFile,
                currentPath: String,
                context: Context,
                fileList: MutableList<TransferableFile>
            ) {
                when {
                    document.isDirectory -> {
                        document.listFiles().forEach { child ->
                            val newPath = if (currentPath.isEmpty()) {
                                child.name ?: ""
                            } else {
                                "$currentPath/${child.name ?: ""}"
                            }
                            collectFiles(child, newPath, context, fileList)
                        }
                    }
                    document.isFile -> {
                        document.length().takeIf { it > 0 }?.let { size ->
                            val fileName = document.name ?: "unknown"
                            val relativePath = if (currentPath.isEmpty()) fileName else "$currentPath/$fileName"
                            fileList.add(
                                TransferableFile(
                                    document.uri,
                                    context.contentResolver,
                                    relativePath,
                                    size))
                        }
                    }
                }
            }

            fun fromUri(uri: Uri, context: Context): TransferableFile {
                val contentResolver = context.contentResolver
                val fileName = when (uri.scheme) {
                    "content" -> {
                        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                            val nameIndex = cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)
                            if (cursor.moveToFirst()) cursor.getString(nameIndex) else null
                        }
                    }
                    "file" -> uri.path?.substringAfterLast('/')
                    else -> null
                } ?: "unknown"

                val fileSize = contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                    descriptor.statSize
                } ?: 0L

                return TransferableFile(
                    uri = uri,
                    contentResolver = contentResolver,
                    path = fileName,
                    size = fileSize
                )
            }

            private fun createDirectories(uri: Uri, contentResolver: ContentResolver) {
                val path = uri.path ?: return
                val parts = path.split('/').filter { it.isNotEmpty() }
                val builder = uri.buildUpon()
                builder.path("")

                for (part in parts) {
                    builder.appendPath(part)
                    val dirUri = builder.build()

                    try {
                        contentResolver.openOutputStream(dirUri, "wt")?.close()
                    } catch (e: Exception) {
                        Log.w(LOG_TRACE,"Unable to create directory $dirUri", e)
                    }
                }
            }

            private fun moveTo(sourceUri: Uri, destinationUri: Uri, contentResolver: ContentResolver) {
                try {
                    contentResolver.openInputStream(sourceUri)?.use { input ->
                            contentResolver.openOutputStream(destinationUri, "wt")?.use { output ->
                                    input.copyTo(output)
                                    output.flush()
                                } ?: throw IOException("Failed to open output stream for $destinationUri")
                        } ?: throw IOException("Failed to open input stream for $sourceUri")
                } finally {
                    contentResolver.delete(sourceUri, null, null)
                }
            }
        }
    }

    protected inner class FileTransfer {
        private val engine = TransferEngine()

        val device: RemoteDevice? = (_state.value.session as? SessionState.Connected)?.device

        fun setSaveDir(uri: Uri?) {
            engine.saveDir = if (uri == null) null else TransferableFile(
                uri,
                context.contentResolver
            )
        }

        fun sendFile(uri: Uri) {
            val files = listOf(TransferableFile.fromUri(uri, context))
            val actions = engine.send(files)
            perform(actions)
        }

        fun sendDirectory(uri: Uri) {
            val files = TransferableFile.walkDirectory(uri, context)
            val actions = engine.send(files)
            perform(actions)
        }

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

        private fun readMessage(message: String) {
            val actions = engine.readMessage(message)
            perform(actions)
        }

        private fun readFile(file: Payload.File) {
            val tempUri = requireNotNull(file.asUri()) {
                "Received file without URI: $file"
            }
            val tempFile = TransferableFile(
                tempUri,
                context.contentResolver
            )

            val actions = engine.readFile(tempFile)
            perform(actions)
        }

        fun perform(actions: List<TransferEngine.Action>) {
            for (action in actions) {
                when (action) {
                    is TransferEngine.Action.Network.SendFile -> performSendFile(action.file)
                    is TransferEngine.Action.Network.SendMessage -> performSendMessage(action.message)
                    is TransferEngine.Action.Local.Save -> performSave(action.source, action.destination)
                    is TransferEngine.Action.Local.Notify -> performNotify(action.message)
                    is TransferEngine.Action.Local.Warning -> performWarning(action.message)
                    is TransferEngine.Action.Local.Progress -> updateProgress(action.direction, action.progress)
                    is TransferEngine.Action.Local.Statistics -> updateStatistics(action.direction, action.statistics)
                }
            }
        }

        private fun performSave(
            source: TransferEngine.File,
            destination: TransferEngine.File
        ) {
            source.save(destination)
        }

        private fun performSendFile(
            file: TransferEngine.File
        ) {
            device?.let { device ->
                val descriptor = (file as TransferableFile).openDescriptor()
                connectionsClient.sendPayload(device.endpointId, Payload.fromFile(descriptor))
            }
        }

        private fun performSendMessage(
            message: String
        ) {
            device?.let { device ->
                val bytes = message.toByteArray(Charsets.UTF_8)
                Log.d(LOG_TRACE, "send ${bytes.size} b to ${device.endpointId}")
                connectionsClient.sendPayload(device.endpointId, Payload.fromBytes(bytes))
            }
        }

        private fun performNotify(
            message: String
        ) {
            sendEvent(ExchangeEvent.RemoteError(message))
        }

        private fun performWarning(
            message: String
        ) {
            Log.w(LOG_TRACE, message)
        }

        private fun updateProgress(
            direction: TransferEngine.Direction,
            progress: Long
        ) {
            Log.d(LOG_TRACE, "[${direction.name}] $progress b sent")
        }

        private fun updateStatistics(
            direction: TransferEngine.Direction,
            statistics: TransferEngine.TransferStatistics
        ) {
            statistics.let {
                Log.d(LOG_TRACE, "[${direction.name}] ${it.current} | ${it.currentBytes} / ${it.totalBytes} b | ${it.queue.size} left")
            }
        }
    }

    protected val fileTransfer = FileTransfer()
}