package org.sonnayasomnambula.nearby.exchanger.nearby

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

@Serializable
data class FileEntry(
    val path: String,
    val size: Long
)

class FileListSerializer {

    companion object {
        const val READY = "ready"
        const val DONE = "done"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    @Serializable
    data class FileListRequest(
        val request: Int,
        val files: List<FileEntry>
    )

    @Serializable
    data class FileRequest(
        val request: Int,
        val path: String,
        val size: Long
    )

    @Serializable
    data class Response(
        val response: Int,
        val status: String
    )

    fun decodeList(jsonString: String): FileListRequest {
        return json.decodeFromString<FileListRequest>(jsonString)
    }

    fun decodeEntry(jsonString: String): FileRequest {
        return json.decodeFromString<FileRequest>(jsonString)
    }

    fun decodeResponse(jsonString: String): Response {
        return json.decodeFromString<Response>(jsonString)
    }

    fun encode(messageId: Int, files: List<FileEntry>): String {
        val request = FileListRequest(messageId, files)
        return json.encodeToString(request)
    }

    fun encode(messageId: Int, entry: FileEntry): String {
        val request = FileRequest(
            messageId,
            entry.path,
            entry.size)
        return json.encodeToString(request)
    }

    fun encode(messageId: Int, status: String): String {
        val response = Response(messageId, status)
        return json.encodeToString(response)
    }
}

