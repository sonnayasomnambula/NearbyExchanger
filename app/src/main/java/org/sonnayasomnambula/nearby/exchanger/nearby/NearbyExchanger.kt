package org.sonnayasomnambula.nearby.exchanger.nearby

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.text.format.Formatter
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
import java.io.File
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

    private fun updateProgress(
        direction: TransferEngine.Direction,
        size: Long,
        progress: Long
    ) {
        val sizeString = Formatter.formatShortFileSize(context, size)
        val progressString = Formatter.formatShortFileSize(context, progress)
        Log.d(LOG_TRACE, "[${direction.name}] $progressString / $sizeString b")

        _state.update { currentState ->
            when (direction) {
                TransferEngine.Direction.In -> {
                    currentState.copy(
                        incoming = currentState.incoming.copy(
                            progress = TransferProgress(
                                currentSize = size,
                                currentProgress = progress
                            )
                        )
                    )
                }
                TransferEngine.Direction.Out -> {
                    currentState.copy(
                        outgoing = currentState.outgoing.copy(
                            progress = TransferProgress(
                                currentSize = size,
                                currentProgress = progress
                            )
                        )
                    )
                }
            }
        }
    }

    private fun updateStatistics(
        direction: TransferEngine.Direction,
        statistics: TransferEngine.TransferStatistics
    ) {
        statistics.let {
            val totalSize = Formatter.formatShortFileSize(context, it.totalSize)
            val totalProgress = Formatter.formatShortFileSize(context, it.totalProgress)
            Log.d(LOG_TRACE, "[${direction.name}] ${it.current} | $totalProgress / $totalSize b | ${it.queue.size} left")

            _state.update { currentState ->
                when (direction) {
                    TransferEngine.Direction.In -> {
                        currentState.copy(
                            incoming = currentState.incoming.copy(
                                statistics = TransferStatistics(
                                    queue = it.queue,
                                    current = it.current,
                                    totalSize = it.totalSize,
                                    totalProgress = it.totalProgress
                                )
                            )
                        )
                    }
                    TransferEngine.Direction.Out -> {
                        currentState.copy(
                            outgoing = currentState.outgoing.copy(
                                statistics = TransferStatistics(
                                    queue = it.queue,
                                    current = it.current,
                                    totalSize = it.totalSize,
                                    totalProgress = it.totalProgress
                                )
                            )
                        )
                    }
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
        private val context: Context,
        override val path: String = "",
        override val size: Long = 0,
        override val mime: String = ""
    ) : TransferEngine.FileImpl() {
        val contentResolver: ContentResolver
            get() = context.contentResolver

        fun openDescriptor(mode: String = "r"): ParcelFileDescriptor {
            return contentResolver.openFileDescriptor(uri, mode)
                ?: throw IOException("Cannot open file descriptor for $uri")
        }

        override fun createFrom(entry: JsonSerializer.FileEntry) : TransferEngine.File {
            return TransferableFile(
                Uri.EMPTY,
                this.context,
                entry.path,
                entry.size,
                entry.mime
            )
        }

        override fun save(directory: TransferEngine.File, path: String, mime: String) {
            val directoryUri = (directory as TransferableFile).uri
            moveTo(directoryUri, path, mime)
        }

        override fun delete() {
            try {
                context.contentResolver.delete(uri, null, null)
            } catch (e: Exception) {
                Log.e("TransferableFile", "Failed to delete file: $uri", e)
            }
        }

        private fun moveTo(
            directoryUri: Uri,
            path: String,
            mime: String
        ) {
            Log.d(LOG_TRACE, "moveTo $directoryUri / $path")

            val fileName = path.substringAfterLast('/')
            val subPath = path.substringBeforeLast('/', "")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && DocumentsContract.isTreeUri(directoryUri)) {
                moveToTreeUri(directoryUri, fileName, subPath, mime)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && directoryUri.toString().startsWith("content://media/")) {
                moveToMediaStore(directoryUri, fileName, subPath, mime)
            } else {
                moveToLegacy(directoryUri, fileName, subPath)
            }
        }

        private fun moveToTreeUri(
            directoryUri: Uri,
            fileName: String,
            subPath: String,
            mime: String
        ) {
            val root = DocumentFile.fromTreeUri(context, directoryUri)
                ?: throw IllegalStateException("Cannot access directory $directoryUri")

            var current = root

            if (subPath.isNotEmpty()) {
                val parts = subPath.split("/")
                for (name in parts) {
                    if (name.isEmpty()) continue

                    val next = current.findFile(name)
                    current = next ?: current.createDirectory(name)
                            ?: throw IOException("Cannot create directory: $name")
                }
            }

            val file = current.createFile(mime, fileName)
                ?: throw IOException("Cannot create file")

            copyContent(uri, file.uri, context.contentResolver)
            context.contentResolver.delete(uri, null, null)
        }

        private fun moveToMediaStore(
            directoryUri: Uri,
            fileName: String,
            subPath: String,
            mime: String
        ) {
            val relativePath = buildString {
                append(Environment.DIRECTORY_DOWNLOADS)
                if (subPath.isNotEmpty()) {
                    append("/").append(subPath)
                }
            }

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            }

            val newUri = contentResolver.insert(directoryUri, values)
                ?: throw IOException("Failed to create destination file")

            try {
                copyContent(uri, newUri, contentResolver)
                contentResolver.delete(uri, null, null)
            } catch (e: Exception) {
                contentResolver.delete(newUri, null, null)
                throw e
            }
        }

        private fun moveToLegacy(
            directoryUri: Uri,
            fileName: String,
            subPath: String
        ) {
            val destinationDir =
                File(directoryUri.path ?: throw IOException("Invalid destination path"))
            val targetDir = if (subPath.isNotEmpty()) File(destinationDir, subPath) else destinationDir

            if (!targetDir.exists() && !targetDir.mkdirs()) {
                throw IOException("Failed to create directory: $targetDir")
            }

            val targetFile = File(targetDir, fileName)
            val targetUri = Uri.fromFile(targetFile)

            copyContent(uri, targetUri, contentResolver)
            contentResolver.delete(uri, null, null)
        }

        private fun copyContent(
            sourceUri: Uri,
            targetUri: Uri,
            contentResolver: ContentResolver
        ) {
            contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                contentResolver.openOutputStream(targetUri)?.use { outputStream ->
                    inputStream.copyTo(outputStream)
                } ?: throw IOException("Failed to open destination stream")
            } ?: throw IOException("Failed to open source stream")
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
                        document.length().takeIf { it > 0 }?.let { fileSize ->
                            val fileName = document.name ?: "unknown"
                            val relativePath = currentPath.ifEmpty { fileName }
                            val mimeType = context.contentResolver.getType(document.uri) ?: "application/octet-stream"
                            fileList.add(
                                TransferableFile(
                                    document.uri,
                                    context,
                                    relativePath,
                                    fileSize,
                                    mimeType
                                )
                            )
                        }
                    }
                }
            }

            fun fromUri(uri: Uri, context: Context): TransferableFile {
                val contentResolver = context.contentResolver
                val fileName = when (uri.scheme) {
                    "content" -> {
                        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                            val nameIndex =
                                cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)
                            if (cursor.moveToFirst()) cursor.getString(nameIndex) else null
                        }
                    }

                    "file" -> uri.path?.substringAfterLast('/')
                    else -> null
                } ?: "unknown"

                val fileSize = contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                    descriptor.statSize
                } ?: 0L

                val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"

                return TransferableFile(
                    uri = uri,
                    context = context,
                    path = fileName,
                    size = fileSize,
                    mime = mimeType
                )
            }
        }
    }

    protected inner class FileTransfer {
        private val engine = TransferEngine()

        val device: RemoteDevice?
            get() = (_state.value.session as? SessionState.Connected)?.device

        fun stopTransfers() {
            val actions = engine.stopTransfers()
            perform(actions)
        }

        fun setSaveDir(uri: Uri?) {
            engine.saveDir = if (uri == null) null else TransferableFile(
                uri,
                context
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
                        readFile(file, payload.id)
                    }
                }
                else-> {}
            }
        }

        private fun readMessage(message: String) {
            val actions = engine.readMessage(message)
            perform(actions)
        }

        private fun readFile(file: Payload.File, payloadId: Long) {
            val tempUri = requireNotNull(file.asUri()) {
                "Received file without URI: $file"
            }
            val tempFile = TransferableFile(
                tempUri,
                context
            )

            val actions = engine.readFile(tempFile, payloadId)
            perform(actions)
        }

        fun perform(actions: List<TransferEngine.Action>) {
            for (action in actions) {
                when (action) {
                    is TransferEngine.Action.Network.SendFile -> performSendFile(action.file)
                    is TransferEngine.Action.Network.SendMessage -> performSendMessage(action.message)
                    is TransferEngine.Action.Network.CancelPayloads -> for (payloadId in action.payloads) connectionsClient.cancelPayload(payloadId)
                    is TransferEngine.Action.Local.Save -> performSave(action.source, action.directory, action.path, action.mime)
                    is TransferEngine.Action.Local.Notify -> performNotify(action.message)
                    is TransferEngine.Action.Local.Warning -> performWarning(action.message)
                    is TransferEngine.Action.Local.Progress -> updateProgress(action.direction, action.size, action.progress)
                    is TransferEngine.Action.Local.Statistics -> updateStatistics(action.direction, action.statistics)
                }
            }
        }

        private fun performSave(
            source: TransferEngine.File,
            directory: TransferEngine.File,
            path: String,
            mime: String
        ) {
            Log.d(LOG_TRACE, "save $path")
            source.save(directory, path, mime)
        }

        private fun performSendFile(
            file: TransferEngine.File
        ) {
            device?.let { device ->
                val descriptor = (file as TransferableFile).openDescriptor()
                val payload = Payload.fromFile(descriptor)
                engine.savePayloadId(TransferEngine.Direction.Out, payload.id, file)
                connectionsClient.sendPayload(device.endpointId, payload)
            }
        }

        private fun performSendMessage(
            message: String
        ) {
            device?.let { device ->
                val bytes = message.toByteArray(Charsets.UTF_8)
                val payload = Payload.fromBytes(bytes)
                connectionsClient.sendPayload(device.endpointId, payload)
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

        fun readPayloadTransferUpdate(
            payloadId: Long,
            bytesTransferred: Long,
            totalBytes: Long,
            status: Int
        ) {
            val actions = engine.readPayloadTransferUpdate(payloadId, bytesTransferred, totalBytes, status)
            perform(actions)
        }
    }

    protected val fileTransfer = FileTransfer()
}