package org.sonnayasomnambula.nearby.exchanger

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.sonnayasomnambula.nearby.exchanger.nearby.FileEntry

import org.sonnayasomnambula.nearby.exchanger.nearby.FileListSerializer

class FileManifestSerializerTest {

    private lateinit var serializer: FileListSerializer

    @Before
    fun setUp() {
        serializer = FileListSerializer()
    }

    @Test
    fun `serialize and deserialize manifest should preserve data`() {

        val files = listOf(
            FileEntry("dir/doc.txt", 1024),
            FileEntry("dir/subdir/photo.jpg", 2048),
            FileEntry("dir/a/b/c/deep.txt", 512)
        )

        val messageId = 1
        val jsonString = serializer.encode(messageId, files)
        println("Generated JSON: $jsonString")

        val deserialized = serializer.decode(jsonString)
        assertEquals(messageId, deserialized.request)
        assertEquals(files.size, deserialized.files.size)

        // Проверяем каждый файл
        files.zip(deserialized.files).forEachIndexed { index, (original, deserialized) ->
            assertEquals("File $index: path mismatch", original.path, deserialized.path)
            assertEquals("File $index: size mismatch", original.size, deserialized.size)
        }
    }

    @Test
    fun `manifest with empty file list should serialize correctly`() {
        val messageId = 2
        val noFiles = emptyList<FileEntry>()

        val emptyJson = serializer.encode(messageId, noFiles)
        val deserialized = serializer.decode(emptyJson)

        assertTrue(deserialized.files.isEmpty())
        assertEquals(messageId, deserialized.request)
    }
}