package org.sonnayasomnambula.nearby.exchanger.nearby

import com.google.android.gms.nearby.connection.PayloadTransferUpdate

class TransferEngine {
    enum class Direction { In, Out }
    interface File {
        val path: String
        val size: Long
        val mime: String

        fun toFileEntry(): JsonSerializer.FileEntry
        fun save(directory: File, path: String, mime: String)
        fun delete()
        fun createFrom(entry: JsonSerializer.FileEntry) : File
    }

    abstract class FileImpl : File {
        override fun toFileEntry() = JsonSerializer.FileEntry(path, size, mime)
    }

    data class TransferStatistics(
        val queue: List<String> = emptyList(),
        val current: String = "",
        val totalSize: Long = 0,
        val totalProgress: Long = 0,
    )

    data class Session(
        var requestId: Int = 0,
        var files: List<File> = emptyList(),
        var fileIndex: Int = -1,
        var payloads: MutableMap<Long, File> = mutableMapOf()
    ) {
        fun reset() {
            fileIndex = -1
            files = emptyList()
        }
        fun statistics(): TransferStatistics {
            return if (fileIndex in files.indices) {
                TransferStatistics(
                    queue = files.drop(fileIndex + 1).map { it.path },
                    current = files[fileIndex].path,
                    totalSize = files.sumOf { it.size },
                    totalProgress = files.take(fileIndex).sumOf { it.size },
                )
            } else {
                TransferStatistics(
                    queue = files.map { it.path },
                    current = "",
                    totalSize = files.sumOf { it.size },
                    totalProgress = if (fileIndex >= files.size) files.sumOf { it.size } else 0,
                )
            }
        }

        fun currentFile() : File? {
            return if (fileIndex in files.indices)
                files[fileIndex]
            else null
        }

        fun currentFileSize(): Long {
            return currentFile()?.size ?: 0
        }
    }

    sealed interface Action {
        sealed interface Network : Action {
            data class SendFile(val file: File) : Network
            data class SendMessage(val message: String) : Network
            data class CancelPayloads(val payloads: List<Long>) : Network
        }
        sealed interface Local : Action {
            data class Save(val source: File, val directory: File, val path: String, val mime: String) : Local

            data class Delete(val files: List<File>) : Local
            data class Notify(val message: String) : Local
            data class Warning(val message: String) : Local
            data class Progress(val direction: Direction, val size: Long, val progress: Long) : Local
            data class Statistics(val direction: Direction, val statistics: TransferStatistics) : Local
        }
    }

    var saveDir: File? = null

    private var outgoing = Session()
    private var incoming = Session()

    private val serializer = JsonSerializer()

    fun send(files: List<File>) : List<Action> {
        if (outgoing.fileIndex != -1) {
            return listOf(
                Action.Local.Warning("previous transfer is not completed")
            )
        }

        outgoing.files = files
        ++outgoing.requestId

        val entryList = files.map { it.toFileEntry() }
        val request = serializer.encodeList(outgoing.requestId, entryList)

        return listOf(
            Action.Network.SendMessage(request),
            Action.Local.Progress(Direction.Out, outgoing.currentFileSize(),0),
            Action.Local.Statistics(Direction.Out, outgoing.statistics())
        )
    }

    fun readFile(file: File, payloadId: Long) : List<Action> {
        savePayloadId(Direction.In, payloadId, file)
        return emptyList()
    }

    fun readMessage(message: String) : List<Action>  {
        val response = serializer.decodeResponse(message)
        if (response != null) {
            return handleResponse(response)
        }

        val listRequest = serializer.decodeList(message)
        if (listRequest != null) {
            return handleListRequest(listRequest)
        }

        val indexRequest = serializer.decodeIndex(message)
        if (indexRequest != null) {
            return handleIndexRequest(indexRequest)
        }

        return emptyList()
    }

    private fun handleResponse(response: JsonSerializer.Response) : List<Action> {
        if (response.requestId != outgoing.requestId) {
            // skip
            outgoing.reset()
            return listOf(
                Action.Local.Warning("wrong response: expected ${outgoing.requestId}, got ${response.requestId}"),
                Action.Local.Progress(Direction.Out, 0, 0),
                Action.Local.Statistics(Direction.Out, outgoing.statistics())
            )
        }

        if (response.status == JsonSerializer.READY) {
            if (outgoing.fileIndex == -1) {
                // go to the first file
                val request = serializer.encodeIndex(++outgoing.requestId, ++outgoing.fileIndex)
                return listOf(
                    Action.Network.SendMessage(request),
                    Action.Local.Progress(Direction.Out, outgoing.currentFileSize(),0),
                    Action.Local.Statistics(Direction.Out, outgoing.statistics())
                )
            } else {
                // send file
                require(outgoing.fileIndex < outgoing.files.size)
                return listOf(
                    Action.Network.SendFile(outgoing.files[outgoing.fileIndex])
                )
            }
        } else if (response.status == JsonSerializer.DONE) {
            if (++outgoing.fileIndex < outgoing.files.size) {
                // go to the next file
                val request = serializer.encodeIndex(++outgoing.requestId, outgoing.fileIndex)
                return listOf(
                    Action.Network.SendMessage(request),
                    Action.Local.Progress(Direction.Out, outgoing.currentFileSize(), 0),
                    Action.Local.Statistics(Direction.Out, outgoing.statistics())
                )
            } else {
                // done
                outgoing.reset()
                return listOf(
                    Action.Local.Progress(Direction.Out, 0, 0),
                    Action.Local.Statistics(Direction.Out, outgoing.statistics())
                )
            }
        } else {
            outgoing.reset()
            return listOf(
                Action.Local.Warning("Unknown response status '${response.status}'"),
                Action.Local.Progress(Direction.Out, 0, 0),
                Action.Local.Statistics(Direction.Out, TransferStatistics())
            )
        }
    }

    private fun handleListRequest(request: JsonSerializer.FileListRequest) : List<Action> {
        if (saveDir == null) {
            val response = serializer.encodeResponse(request.requestId, JsonSerializer.NO_DIR)
            return listOf(
                Action.Local.Notify(JsonSerializer.NO_DIR),
                Action.Network.SendMessage(response)
            )
        }

        // TODO check available space

        incoming.reset()
        incoming.files = request.files.map { entry ->
            saveDir!!.createFrom(entry)
        }

        val response = serializer.encodeResponse(request.requestId, JsonSerializer.READY)
        return listOf(
            Action.Network.SendMessage(response),
            Action.Local.Progress(Direction.In, incoming.currentFileSize(), 0),
            Action.Local.Statistics(Direction.In, incoming.statistics())
        )
    }

    private fun handleIndexRequest(request: JsonSerializer.IndexRequest) : List<Action> {
        require(request.index < incoming.files.size) {
            "invalid index request: ${request.index}/${incoming.files.size}"
        }

        incoming.requestId = request.requestId
        incoming.fileIndex = request.index

        val response = serializer.encodeResponse(request.requestId, JsonSerializer.READY)
        return listOf(
            Action.Network.SendMessage(response),
            Action.Local.Progress(Direction.In, incoming.currentFileSize(),0),
            Action.Local.Statistics(Direction.In, incoming.statistics())
        )
    }

    fun readPayloadTransferUpdate(
        payloadId: Long,
        bytesTransferred: Long,
        totalBytes: Long,
        status: Int
    ) : List<Action> {
        return buildList {
            val direction = when {
                incoming.payloads.containsKey(payloadId) -> Direction.In
                outgoing.payloads.containsKey(payloadId) -> Direction.Out
                else -> null
            }

            if (direction != null) {
                add(Action.Local.Progress(direction, totalBytes, bytesTransferred))

                if (direction == Direction.In && status == PayloadTransferUpdate.Status.SUCCESS) {
                    val temporaryFile = requireNotNull(incoming.payloads.remove(payloadId))
                    val fileEntry = requireNotNull(incoming.currentFile())

                    saveDir?.let { saveDir ->
                        add(Action.Local.Save(temporaryFile, saveDir, fileEntry.path, fileEntry.mime))
                    }

                    val response = serializer.encodeResponse(incoming.requestId, JsonSerializer.DONE)
                    add(Action.Network.SendMessage(response))

                    if (incoming.fileIndex == incoming.files.size - 1) {
                        incoming.reset()
                        add(Action.Local.Statistics(direction, incoming.statistics()))
                        add(Action.Local.Progress(direction, 0, 0))
                    }
                }
            }
        }
    }

    fun savePayloadId(
        direction: Direction,
        payloadId: Long,
        file: File
    ) {
        when (direction) {
            Direction.In -> incoming.payloads[payloadId] = file
            Direction.Out -> outgoing.payloads[payloadId] = file
        }
    }

    fun stopTransfers() : List<Action> {

        val stopRequest = serializer.encodeResponse(0, JsonSerializer.STOP)
        val temporaryFiles = incoming.files

        incoming.reset()
        outgoing.reset()
        incoming.payloads.clear()
        outgoing.payloads.clear()

        return listOf(
            Action.Network.CancelPayloads((incoming.payloads.keys + outgoing.payloads.keys).toList()),
            Action.Network.SendMessage(stopRequest),
            Action.Local.Delete(temporaryFiles),
            Action.Local.Progress(Direction.In, 0, 0),
            Action.Local.Progress(Direction.Out, 0, 0),
            Action.Local.Statistics(Direction.In, incoming.statistics()),
            Action.Local.Statistics(Direction.Out, incoming.statistics())
        )
    }
}