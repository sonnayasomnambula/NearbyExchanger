package org.sonnayasomnambula.nearby.exchanger.model

import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore

interface LocationProvider {
    fun getDefaultLocation(): SaveLocation?
}

class AndroidLocationProvider : LocationProvider {
    override fun getDefaultLocation(): SaveLocation? {
        val downloadsUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Для Android 10+ используем MediaStore
            MediaStore.Downloads.EXTERNAL_CONTENT_URI
        } else {
            // Для старых версий
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                ?.let { Uri.fromFile(it) }
        }
        return downloadsUri?.let { uri ->
            SaveLocation("Downloads", uri)
        }
    }
}