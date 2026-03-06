package org.sonnayasomnambula.nearby.exchanger

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

import org.sonnayasomnambula.nearby.exchanger.nearby.JsonSerializer

class SerializerTest {

    private lateinit var serializer: JsonSerializer

    @Before
    fun setUp() {
        serializer = JsonSerializer(prettyPrint = true)
    }

    @Test
    fun `serialize and deserialize manifest should preserve data`() {

        val files = listOf(
            JsonSerializer.FileEntry("dir/doc.txt", 1024, "text/plain"),
            JsonSerializer.FileEntry("dir/subdir/photo.jpg", 2048, "image/jpeg"),
            JsonSerializer.FileEntry("dir/a/b/c/deep.txt", 512, "text/plain")
        )

        val requestId = 1
        val jsonString = serializer.encodeList(requestId, files)

        val deserialized = requireNotNull(serializer.decodeList(jsonString))
        assertEquals(requestId, deserialized.requestId)
        assertEquals(files.size, deserialized.files?.size)

        // Проверяем каждый файл
        files.zip(deserialized.files).forEachIndexed { index, (original, deserialized) ->
            assertEquals("File $index: path mismatch", original.path, deserialized.path)
            assertEquals("File $index: size mismatch", original.size, deserialized.size)
        }
    }

    @Test
    fun `manifest with empty file list should serialize correctly`() {
        val requestId = 2
        val noFiles = emptyList<JsonSerializer.FileEntry>()

        val emptyJson = serializer.encodeList(requestId, noFiles)
        val deserialized = requireNotNull(serializer.decodeList(emptyJson))

        assertTrue(deserialized.files.isEmpty())
        assertEquals(requestId, deserialized.requestId)
    }
}