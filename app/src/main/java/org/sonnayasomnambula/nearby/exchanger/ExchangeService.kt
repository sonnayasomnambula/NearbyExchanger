package org.sonnayasomnambula.nearby.exchanger

import android.R
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
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.sonnayasomnambula.nearby.exchanger.model.Role
import org.sonnayasomnambula.nearby.exchanger.nearby.Advertiser
import org.sonnayasomnambula.nearby.exchanger.nearby.Discoverer
import org.sonnayasomnambula.nearby.exchanger.nearby.Exchanger

class ExchangeService : Service() {

    private var exchanger: Exchanger? = null
    private var onExchangerReadyListener: ((Exchanger) -> Unit)? = null
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): ExchangeService = this@ExchangeService
        fun setOnExchangerReadyListener(listener: (Exchanger) -> Unit) {
            Log.d(LOG_TRACE, __func__())
            onExchangerReadyListener = listener
            // Если exchanger уже готов, вызываем сразу
            exchanger?.let { listener(it) }
        }
    }

    private val job = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "advertising_service_channel"

        private const val ACTION_START = "action_start"
        private const val ACTION_STOP = "action_stop"

        fun start(role: Role, context: Context) {
            Log.d(LOG_TRACE, "call ExchangeService.start")
            val intent = Intent(context, ExchangeService::class.java)
            intent.action = ACTION_START
            intent.putExtra("role", role)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(intent)
            else
                context.startService(intent)
        }
        fun stop(context: Context) {
            val intent = Intent(context, ExchangeService::class.java)
            intent.action = ACTION_STOP
            context.startService(intent)
        }
    }

    override fun onCreate() {
        Log.d(LOG_TRACE, "service: created")

        super.onCreate()
        createNotificationChannel()
        startFore()
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
            .setSmallIcon(R.drawable.ic_dialog_info) // Замените на свою иконку
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

            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startFore() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    createNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, createNotification())
            }
        } /*else { // TODO
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(ONGOING_NOTIFICATION_ID, notification)
        }*/
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        Log.d(LOG_TRACE, __func__())

        val action = intent?.action

        when (action) {
            ACTION_START -> {
                val role = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getSerializableExtra("role", Role::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getSerializableExtra("role") as Role
                }

                val exchanger = when (role) {
                    Role.ADVERTISER -> Advertiser(serviceScope, applicationContext)
                    Role.DISCOVERER -> Discoverer(serviceScope, applicationContext)
                    else -> throw IllegalArgumentException("Role must be provided")
                }

                try {
                    this.exchanger = exchanger
                    onExchangerReadyListener?.invoke(exchanger)
                    exchanger.start()
                } catch (e: Exception) {
                    Log.e(LOG_TRACE, "Exception during start")
                }
            }

            ACTION_STOP -> {
                exchanger?.stop()
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        Log.d(LOG_TRACE, __func__())
        return binder
    }

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
        Log.d(LOG_TRACE, "service: destroyed")
    }
}