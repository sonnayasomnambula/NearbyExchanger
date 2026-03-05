package org.sonnayasomnambula.nearby.exchanger.app

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CrashLogger (
    private val context: Context,
    private val notificationManager: NotificationManagerCompat
) {
    companion object {
        fun save(throwable: Throwable, context: Context, tag: String = "Application crash") {
            val timestamp = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(Date())
            val fileName = "crash-$timestamp.txt"

            val crashLog = StringWriter().apply {
                PrintWriter(this).use { printWriter ->
                    printWriter.println("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())}")
                    printWriter.println("Thread: ${Thread.currentThread().name}")
                    printWriter.println(tag)
                    printWriter.println()
                    throwable.printStackTrace(printWriter)
                }
            }.toString()

            val crashesDir = File(context.getExternalFilesDir(null), "crashes")

            if (!crashesDir.exists()) {
                crashesDir.mkdirs()
            }

            try {
                val file = File(crashesDir, fileName)
                file.writeText(crashLog)
            } catch (e: Exception) {
                // ignore
            }
        }
    }
}