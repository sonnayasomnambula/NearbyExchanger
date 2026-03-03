package org.sonnayasomnambula.nearby.exchanger.nearby

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

@Serializable
data class FileManifest(
    val files: List<FileEntry>
) {
    @Serializable
    data class FileEntry(
        val path: String,
        val size: Long,
    )
}

@Serializable
sealed class ProtocolMessage {
    abstract val messageId: Int
}

@Serializable
@SerialName("Manifest")
data class ManifestMessage(
    override val messageId: Int,
    val files: List<FileManifest.FileEntry>
) : ProtocolMessage()

@Serializable
@SerialName("ManifestAck")
data class ManifestAckMessage(
    override val messageId: Int,
    val ok: Boolean,
    val errorMessage: String? = null
) : ProtocolMessage()

@Serializable
@SerialName("FileRequest")
data class FileRequestMessage(
    override val messageId: Int,
    val fileIndex: Int,      // индекс файла в манифесте
    val relativePath: String // для надёжности дублируем путь
) : ProtocolMessage()

@Serializable
@SerialName("FileAck")
data class FileAckMessage(
    override val messageId: Int,
    val fileIndex: Int,
    val ok: Boolean
) : ProtocolMessage()

class FileManifestSerializer {

    companion object {
        private val json = Json {
            prettyPrint = true
            prettyPrintIndent = "  "
        }
    }

    fun createFromDirectory(uri: Uri, context: Context): FileManifest {
        val rootDoc = DocumentFile.fromTreeUri(context, uri)
            ?: throw IllegalArgumentException("Invalid directory URI")

        val entries = mutableListOf<FileManifest.FileEntry>()
        walkDirectory(rootDoc, "", entries)
        entries.sortBy { it.path }

        return FileManifest(entries)
    }

    fun createFromJson(jsonString: String): FileManifest {
        val message = json.decodeFromString<ProtocolMessage>(jsonString)
        return when (message) {
            is ManifestMessage -> FileManifest(files = message.files)
            else -> throw IllegalArgumentException("Expected ManifestMessage")
        }
    }

    fun parseJson(jsonString: String): ManifestMessage {
        return json.decodeFromString<ManifestMessage>(jsonString)
    }

    fun toJson(manifest: FileManifest, messageId: Int): String {
        val message = ManifestMessage(
            messageId = messageId,
            files = manifest.files
        )
        return json.encodeToString<ProtocolMessage>(message)
    }

    private fun walkDirectory(
        doc: DocumentFile,
        currentPath: String,
        entries: MutableList<FileManifest.FileEntry>
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
                    FileManifest.FileEntry(
                        path = currentPath,
                        size = doc.length(),
                    )
                )
            }
        }
    }
}
