package org.sonnayasomnambula.nearby.exchanger

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.lifecycle.MutableLiveData


class AdvertisingService : Service() {

    val LOG_TRACE = "org.sonnayasomnambula.trace"

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "advertising_service_channel"

        const val ACTION_SERVICE_STARTED = "ACTION_SERVICE_STARTED"
        const val ACTION_SERVICE_STOPPED = "ACTION_SERVICE_STOPPED"

        @RequiresApi(Build.VERSION_CODES.O)
        fun start(context: Context) {
            val intent = Intent(context, AdvertisingService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, AdvertisingService::class.java)
            context.stopService(intent)
        }

        val serviceLiveData = MutableLiveData<AdvertisingService?>(null)

    }

    override fun onCreate() {
        Log.d(LOG_TRACE, "service: created")

        super.onCreate()
        createNotificationChannel()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                createNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Advertising Service")
            .setContentText("Service is running in background")
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Замените на свою иконку
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Advertising Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Service for showing advertisements"
                setSound(null, null)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Здесь ваша логика работы сервиса
        // Например, запуск рекламных кампаний

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(LOG_TRACE, "service: destroyed")
    }

    inner class LocalBinder : Binder() {
        fun getService() : AdvertisingService = this@AdvertisingService
    }

    private val binder = LocalBinder()

    override fun onBind(intent: Intent?): IBinder? {
        return binder
    }
}