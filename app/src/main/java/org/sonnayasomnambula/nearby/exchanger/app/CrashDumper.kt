package org.sonnayasomnambula.nearby.exchanger.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import org.sonnayasomnambula.nearby.exchanger.LOG_TRACE
import org.sonnayasomnambula.nearby.exchanger.MainActivity
import org.sonnayasomnambula.nearby.exchanger.R
import org.sonnayasomnambula.nearby.exchanger.nearby.NotificationChannels
import org.sonnayasomnambula.nearby.exchanger.nearby.NotificationIds
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Notificator(
    private val context: Context,
    private val channelId: String,
    private val channelName: String,
    private val notificationId: Int
) {
    private val notificationManager = NotificationManagerCompat.from(context)

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun showNotification(throwable: Throwable, file: File?) {
        val intent = file?.let {
            Intent(Intent.ACTION_VIEW).apply {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", it)
                setDataAndType(uri,"text/plain")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } ?: Intent(context, MainActivity::class.java)

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle(throwable::class.simpleName)
            .setContentText(context.getString(R.string.tap_to_see_stacktrace))
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    companion object {
        fun hasPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true // До Android 13 разрешение не требуется
            }
        }
    }
}

class CrashDumper(
    private val context: Context,
) {
    private var notificator: Notificator? = null

    fun save(throwable: Throwable, tag: String = "Application crash") {
        val timestamp = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(Date())
        val fileName = "crash-$timestamp.txt"

        val crashesDir = File(context.getExternalFilesDir(null), "crashes")

        if (!crashesDir.exists()) {
            crashesDir.mkdirs()
        }

        val crashLog = StringWriter().apply {
            PrintWriter(this).use { printWriter ->
                printWriter.println("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())}")
                printWriter.println("Thread: ${Thread.currentThread().name}")
                printWriter.println(tag)
                printWriter.println()
                throwable.printStackTrace(printWriter)
            }
        }.toString()

        try {
            val file = File(crashesDir, fileName)
            file.writeText(crashLog)
            showNotification(throwable, file)
        } catch (e: Exception) {
            Log.e(LOG_TRACE, "Crashed while saving crash dump :(", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun showNotification(throwable: Throwable, file: File?) {
        if (Notificator.hasPermission(context)) {
            if (notificator == null) {
                notificator = Notificator(
                    context = context,
                    channelId = NotificationChannels.CRASH_REPORT,
                    channelName = context.getString(R.string.crash_reports),
                    notificationId = NotificationIds.CRASH_REPORT
                )
            }
            notificator?.showNotification(throwable, file)
        } else {
            Toaster.show(throwable, context)
        }
    }
}