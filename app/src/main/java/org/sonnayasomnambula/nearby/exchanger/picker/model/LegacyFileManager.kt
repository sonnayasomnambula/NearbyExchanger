package org.sonnayasomnambula.nearby.exchanger.picker.model

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.util.Log
import androidx.core.content.getSystemService
import org.sonnayasomnambula.nearby.exchanger.common.LOG_TRACE

val LOG_TAG = "LegacyFileManager"

class LegacyFileManager(private val context: Context) {

    class File(
        val file: java.io.File,
        override val name: String = file.name,
        override val path: String = file.absolutePath,
        override val isDirectory: Boolean = file.isDirectory
    ) : Picker.File {
        override fun length() = file.length()
        override fun children(): List<Picker.File> {
            if (!isDirectory)
                return emptyList()
            val files = file.listFiles()
            if (files == null) {
                Log.d(LOG_TAG, "$name: no access or empty dir")
                return emptyList()
            }

            return files.map { f ->
                File(f)
            }.sortedWith(compareBy<Picker.File> { !it.isDirectory }.thenBy { it.name.lowercase() })
        }

        override fun equals(other: Any?) =
            other is File && file == other.file
        override fun hashCode() = file.hashCode()
        override fun toString() = "File($file)"
    }

    class Volume(
        val file: java.io.File,
        override val name: String,
        override val path: String = file.absolutePath,
        override val isRemovable: Boolean
    ): Picker.Volume {
        override fun file(): Picker.File {
            return File(file)
        }
        override fun toString(): String = "Volume($file)"
    }

    val volumes = getAllVolumes()

    private fun getAllVolumes() : List<Volume> {
        val volumesList = mutableListOf<Volume>()

        Log.d(LOG_TRACE, "=== All volumes on device ===")

        Log.d(LOG_TRACE, "Method 1: with StorageManager (API 24+)")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val storageManager = context.getSystemService<StorageManager>()// context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
            if (storageManager != null) {
                val storageVolumes = storageManager.storageVolumes
                for (storageVolume in storageVolumes) {
                    val directory = storageVolume.directory
                    if (directory != null && directory.exists()) {
                        val volume = Volume(
                            file = directory,
                            name = storageVolume.getDescription(context),
                            isRemovable = storageVolume.isRemovable
                        )
                        Log.d(
                            LOG_TRACE, """
                        Volume: ${volume.path}
                        - Name: ${volume.name}
                        - Removable: ${volume.isRemovable}
                        - Emulated: ${storageVolume.isEmulated}
                        - Primary: ${storageVolume.isPrimary}
                    """.trimIndent())
                        volumesList.add(volume)
                    }
                }
            }
        }

        if (volumesList.isNotEmpty()) {
            return volumesList.toList()
        }

        Log.d(LOG_TRACE, "Method 2: Environment")
        val externalStorage = Environment.getExternalStorageDirectory()
        val externalStorageVolume = Volume(
            file = externalStorage,
            name = "External storage",
            isRemovable = false
        )
        Log.d(LOG_TRACE, "External Storage: ${externalStorage.absolutePath}")

        volumesList.add(externalStorageVolume)

        Log.d(LOG_TRACE, "Method 3: getenv")
        val secondaryStorage = System.getenv("SECONDARY_STORAGE")
        if (!secondaryStorage.isNullOrEmpty()) {
            Log.d(LOG_TRACE, "Secondary Storage (SD Card): $secondaryStorage")
            val sdCard = Volume(
                file = java.io.File(secondaryStorage),
                name = "Secondary storage",
                isRemovable = true
            )
            if (sdCard.file.exists()) {
                volumesList.add(sdCard)
            }
        }

        if (volumesList.isNotEmpty()) {
            return volumesList.toList()
        }

        return volumesList.distinct()
    }
}