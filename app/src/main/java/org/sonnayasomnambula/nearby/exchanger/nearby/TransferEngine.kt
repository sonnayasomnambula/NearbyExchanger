package org.sonnayasomnambula.nearby.exchanger.nearby

class TransferEngine {
    interface File {
        val path: String
        val size: Long
        fun toFileEntry(): JsonSerializer.FileEntry
        fun save(destination: File)
        fun createChild(entry: JsonSerializer.FileEntry) : File
    }

    abstract class FileImpl : File {
        override fun toFileEntry() = JsonSerializer.FileEntry(path, size)
    }
    data class Session(
        var requestId: Int = 0,
        var endpointId: String = "",
        var files: List<File> = emptyList(),
        var fileIndex: Int = -1
    )

    sealed interface Action {
        sealed interface Network : Action {
            data class SendFile(val file: File) : Network
            data class SendMessage(val message: String) : Network
        }
        sealed interface Local : Action {
            data class Save(val source: File, val destination: File) : Local
            data class Notify(val message: String) : Local
            data class Warning(val message: String) : Local
        }
    }

    var saveDir: File? = null

    private var outgoing = Session()
    private var incoming = Session()

    private val serializer = JsonSerializer()

    fun send(files: List<File>) : List<Action> {
        if (outgoing.fileIndex != -1) {
            return listOf(Action.Local.Warning("previous transfer is not completed"))
        }

        outgoing.files = files
        ++outgoing.requestId

        val entryList = files.map { it.toFileEntry() }
        val request = serializer.encodeList(outgoing.requestId, entryList)

        return listOf(
            Action.Network.SendMessage(request)
        )
    }

    fun readFile(file: File) : List<Action> {
        val destination = incoming.files[incoming.fileIndex]

        val response = serializer.encodeResponse(incoming.requestId, JsonSerializer.DONE)

        return listOf(
            Action.Local.Save(file, destination),
            Action.Network.SendMessage(response))
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
            outgoing.fileIndex = -1 // skip
            return listOf(Action.Local.Warning("wrong response: expected ${outgoing.requestId}, got ${response.requestId}"))
        }

        if (response.status == JsonSerializer.READY) {
            if (outgoing.fileIndex == -1) {
                val request = serializer.encodeIndex(++outgoing.requestId, ++outgoing.fileIndex)
                return listOf(
                    Action.Network.SendMessage(request)
                )
            } else {
                require(outgoing.fileIndex < outgoing.files.size)
                return listOf(
                    Action.Network.SendFile(outgoing.files[outgoing.fileIndex])
                )
            }
        } else if (response.status == JsonSerializer.DONE) {
            if (++outgoing.fileIndex < outgoing.files.size) {
                val request = serializer.encodeIndex(++outgoing.requestId, outgoing.fileIndex)
                return listOf(
                    Action.Network.SendMessage(request)
                )
            } else {
                outgoing.files = emptyList()
                outgoing.fileIndex = -1
                return emptyList()
            }
        } else {
            outgoing.fileIndex = -1
            return emptyList()
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

        incoming.fileIndex = -1
        incoming.files = request.files.map { entry ->
            saveDir!!.createChild(entry)
        }

        val response = serializer.encodeResponse(request.requestId, JsonSerializer.READY)
        return listOf(Action.Network.SendMessage(response))
    }

    private fun handleIndexRequest(request: JsonSerializer.IndexRequest) : List<Action> {
        require(request.index < incoming.files.size) {
            "invalid index request: ${request.index}/${incoming.files.size}"
        }

        incoming.requestId = request.requestId
        incoming.fileIndex = request.index

        val response = serializer.encodeResponse(request.requestId, JsonSerializer.READY)
        return listOf(
            Action.Network.SendMessage(response)
        )
    }
}