package org.sonnayasomnambula.nearby.exchanger.common.ui.screen

import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import android.os.Build
import androidx.annotation.RequiresApi
import java.util.Locale


fun humanReadableSize(bytes: Long): String {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        measureFormatSize(bytes)
    } else {
        customFormatSize(bytes)
    }
}

@RequiresApi(Build.VERSION_CODES.N)
private fun measureFormatSize(bytes: Long): String {
    val formatter = MeasureFormat.getInstance(Locale.getDefault(), MeasureFormat.FormatWidth.SHORT)
    return when {
        bytes < 1024 -> formatter.format(Measure(bytes.toDouble(), MeasureUnit.BYTE))
        bytes < 1024 * 1024 -> formatter.format(Measure(bytes / 1024.0, MeasureUnit.KILOBYTE))
        bytes < 1024 * 1024 * 1024 -> formatter.format(Measure(bytes / (1024.0 * 1024), MeasureUnit.MEGABYTE))
        else -> formatter.format(Measure(bytes / (1024.0 * 1024 * 1024), MeasureUnit.GIGABYTE))
    }
}

private fun customFormatSize(bytes: Long): String {
    return if (bytes < 1024) {
        "$bytes B"
    } else {
        val units = arrayOf("KB", "MB", "GB")
        var value = bytes.toFloat()
        var unitIndex = -1

        while (value >= 1024 && unitIndex < units.size - 1) {
            value /= 1024
            unitIndex++
        }

        "${String.format("%.1f", value)} ${units[unitIndex]}"
    }
}