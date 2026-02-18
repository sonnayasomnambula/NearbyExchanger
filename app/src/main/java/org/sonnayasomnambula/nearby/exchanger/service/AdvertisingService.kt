package org.sonnayasomnambula.nearby.exchanger.service

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
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.sonnayasomnambula.nearby.exchanger.MainActivity
import org.sonnayasomnambula.nearby.exchanger.model.Role

class AdvertisingService : Service(), ExchangeService {

    val LOG_TRACE = "org.sonnayasomnambula.trace"

    private val binder = LocalBinder()

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

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
    }

    // Состояние сервиса
    private val _state = MutableStateFlow<ServiceState>(ServiceState.Initial)
    override val state: StateFlow<ServiceState> = _state.asStateFlow()

    // События сервиса
    private val _events = MutableSharedFlow<ServiceEvent>()
    override val events: SharedFlow<ServiceEvent> = _events.asSharedFlow()

    override fun role(): Role = Role.ADVERTISER

    override fun onCreate() {
        Log.d(LOG_TRACE, "service: created")

        super.onCreate()
        createNotificationChannel()
        startFore()

        _state.value = ServiceState.Running(
            availableDevices = emptyList()
        )
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    inner class LocalBinder : Binder() {
        fun getService() : AdvertisingService = this@AdvertisingService
    }

    override fun onBind(intent: Intent?): IBinder? {
        return binder
    }

    override fun onCommand(command: ServiceCommand) {
        when (command) {
            is ServiceCommand.Stop ->  {
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
        Log.d(LOG_TRACE, "service: destroyed")
    }
}