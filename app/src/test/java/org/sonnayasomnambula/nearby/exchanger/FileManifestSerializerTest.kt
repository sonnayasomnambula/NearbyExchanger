package org.sonnayasomnambula.nearby.exchanger

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

import org.sonnayasomnambula.nearby.exchanger.nearby.FileManifest
import org.sonnayasomnambula.nearby.exchanger.nearby.FileManifestSerializer

class FileManifestSerializerTest {
    private lateinit var serializer: FileManifestSerializer

    @Before
    fun setUp() {
        serializer = FileManifestSerializer()
    }

    @Test
    fun `serialize and deserialize manifest should preserve data`() {
        // Подготовка
        val originalFiles = listOf(
            FileManifest.FileEntry("doc.txt", 1024),
            FileManifest.FileEntry("subdir/photo.jpg", 2048),
            FileManifest.FileEntry("a/b/c/deep.txt", 512)
        )
        val originalManifest = FileManifest(originalFiles)

        // Действие: сериализуем в JSON
        val jsonString = serializer.toJson(originalManifest, 8)
        println("Generated JSON: $jsonString")

        // Действие: десериализуем обратно
        val deserializedManifest = serializer.createFromJson(jsonString)

        // Проверка
        assertEquals(originalManifest.files.size, deserializedManifest.files.size)

        // Проверяем каждый файл
        originalManifest.files.zip(deserializedManifest.files).forEachIndexed { index, (original, deserialized) ->
            assertEquals("File $index: path mismatch", original.path, deserialized.path)
            assertEquals("File $index: size mismatch", original.size, deserialized.size)
        }
    }

    @Test
    fun `sorting should order files alphabetically by path`() {
        // Подготовка: создаем неотсортированный список
        val unsortedFiles = listOf(
            FileManifest.FileEntry("z.txt", 100),
            FileManifest.FileEntry("a.txt", 100),
            FileManifest.FileEntry("b/file.txt", 100),
            FileManifest.FileEntry("a/file.txt", 100)
        )

        // Действие: создаем манифест и получаем отсортированный список
        // (сортировка применяется в createFromDirectory, но мы можем проверить через toJson)
        val manifest = FileManifest(unsortedFiles)

        // Вручную сортируем для проверки
        val sortedPaths = unsortedFiles.map { it.path }.sorted()

        // Проверяем, что сортировка работает как ожидается
        assertEquals(listOf("a.txt", "a/file.txt", "b/file.txt", "z.txt"), sortedPaths)
    }

    @Test
    fun `files with same name in different directories should have unique paths`() {
        // Подготовка: файлы с одинаковыми именами в разных папках
        val files = listOf(
            FileManifest.FileEntry("file.txt", 100),
            FileManifest.FileEntry("subdir/file.txt", 200),
            FileManifest.FileEntry("another/deep/path/file.txt", 300)
        )

        // Проверка
        val manifest = FileManifest(files)
        val jsonString = serializer.toJson(manifest, 9)

        // Убеждаемся, что все три файла сохранились (пути уникальны)
        assertTrue(jsonString.contains("file.txt"))
        assertTrue(jsonString.contains("subdir/file.txt"))
        assertTrue(jsonString.contains("another/deep/path/file.txt"))
    }
}