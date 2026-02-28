package org.sonnayasomnambula.nearby.exchanger.nearby

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

class JsonSerializer {

    companion object {
        const val READY = "ready"
        const val DONE = "done"
        const val NO_DIR = "no dir"
        const val NO_SPACE = "no space"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    @Serializable
    data class FileEntry(
        val path: String,
        val size: Long
    )

    @Serializable
    data class FileListRequest(
        val requestId: Int,
        val files: List<FileEntry>
    )

    @Serializable
    data class IndexRequest(
        val requestId: Int,
        val index: Int
    )

    @Serializable
    data class Response(
        val requestId: Int,
        val status: String
    )

    fun decodeList(message: String): FileListRequest? {
        return try {
            json.decodeFromString<FileListRequest>(message)
        } catch (_: Exception) {
            null
        }
    }

    fun decodeIndex(message: String): IndexRequest? {
        return try {
            json.decodeFromString<IndexRequest>(message)
        } catch (_: Exception) {
            null
        }
    }

    fun decodeResponse(message: String): Response? {
        return try {
            json.decodeFromString<Response>(message)
        } catch (_: Exception) {
            null
        }
    }

    fun encodeList(requestId: Int, files: List<FileEntry>): String {
        val request = FileListRequest(requestId, files)
        return json.encodeToString(request)
    }

    fun encodeIndex(requestId: Int, index: Int): String {
        val request = IndexRequest(
            requestId,
            index)
        return json.encodeToString(request)
    }

    fun encodeResponse(requestId: Int, status: String): String {
        val response = Response(requestId, status)
        return json.encodeToString(response)
    }
}

