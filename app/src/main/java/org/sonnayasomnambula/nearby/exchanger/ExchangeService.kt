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
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.sonnayasomnambula.nearby.exchanger.model.Role
import org.sonnayasomnambula.nearby.exchanger.nearby.Advertiser
import org.sonnayasomnambula.nearby.exchanger.nearby.Discoverer
import org.sonnayasomnambula.nearby.exchanger.nearby.ExchangeState
import org.sonnayasomnambula.nearby.exchanger.nearby.Exchanger
import org.sonnayasomnambula.nearby.exchanger.nearby.NotificationChannels
import org.sonnayasomnambula.nearby.exchanger.nearby.NotificationIds
import org.sonnayasomnambula.nearby.exchanger.nearby.SearchingMode
import org.sonnayasomnambula.nearby.exchanger.nearby.SessionState
import org.sonnayasomnambula.nearby.exchanger.nearby.TransferState

fun SearchingMode.getDisplayText(context: Context): String {
    return when (this) {
        is SearchingMode.Running -> when (role) {
            Role.ADVERTISER -> context.getString(R.string.connection_state_advertising)
            Role.DISCOVERER -> context.getString(R.string.connection_state_discovering)
        }
        SearchingMode.Starting -> context.getString(R.string.connection_state_starting)
        SearchingMode.Stopped -> context.getString(R.string.connection_state_not_connected)
        is SearchingMode.Failed -> context.getString(R.string.connection_state_error)
    }
}

fun ExchangeState.getDisplayText(context: Context): String {
    return when (val searching = searching) {
        SearchingMode.Stopped -> when (session) {
            is SessionState.Connected -> context.getString(R.string.connection_state_connected)
            is SessionState.None -> context.getString(R.string.connection_state_not_connected)
        }
        else -> searching.getDisplayText(context)
    }
}

class ExchangeService : Service() {
    private var exchanger: Exchanger? = null
    private var onExchangerReadyListener: ((Exchanger) -> Unit)? = null
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun setOnExchangerReadyListener(listener: (Exchanger) -> Unit) {
            Log.d(LOG_TRACE, __func__())
            onExchangerReadyListener = listener
            exchanger?.let {
                listener(it)
                observeExchanger(it)
            }
        }
    }

    private val job = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    companion object {
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
        notification.createChannel()
        startFore()
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
                    observeExchanger(exchanger)
                    exchanger.start()
                } catch (e: Exception) {
                    Log.e(LOG_TRACE, "Exception during start")
                }
            }

            ACTION_STOP -> {
                exchanger?.stop()
                exchanger = null
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d(LOG_TRACE, __func__())
        return binder
    }

    override fun onDestroy() {
        job.cancel()
        stopFore()
        notification.remove()
        super.onDestroy()
        Log.d(LOG_TRACE, "service: destroyed")
    }

    fun startFore() {
        val defaultNotification = NotificationState.create(ExchangeState(), this@ExchangeService)
        notification.notificationState = defaultNotification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NotificationIds.SERVICE,
                    notification.create(defaultNotification),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NotificationIds.SERVICE, notification.create(defaultNotification))
            }
        } else {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NotificationIds.SERVICE, notification.create(defaultNotification))
        }
    }

    fun stopFore() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(NotificationIds.SERVICE)
        }
    }

    private fun getMainActivityPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun observeExchanger(exchanger: Exchanger) {
        serviceScope.launch {
            exchanger.state.collect { state ->
                notification.update(state)
            }
        }
    }

    data class NotificationState(
        val contentText: String,
        val progressMax: Int,
        val progressCurrent: Int,
    ) {
        private data class ProgressState(
            val current: Int,
            val max: Int
        ) {
            constructor(state: TransferState): this (
                current = if (state.statistics.totalSize > 0)
                    (state.transferred() * 100 / state.statistics.totalSize).toInt() else 0,
                max = if (state.statistics.totalSize > 0)
                    100 else 0
            )
        }

        companion object {
            fun create(state: ExchangeState, context: Context): NotificationState {
                val incoming = ProgressState(state.incoming)
                val outgoing = ProgressState(state.outgoing)

                return NotificationState(
                    contentText = state.getDisplayText(context),
                    progressMax = if (incoming.max > 0) incoming.max else outgoing.max,
                    progressCurrent = if (incoming.current > 0) incoming.current else outgoing.current
                )
            }
        }
    }

    inner class NotificationUpdater {
        var notificationState: NotificationState? = null

        private val notificationScope = CoroutineScope(job + Dispatchers.Main)

        private val notificationUpdateIntervalMs = 1000L
        private var lastNotificationUpdateTime = 0L
        private var scheduledNotificationUpdate: Job? = null

        fun createChannel() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    NotificationChannels.SERVICE,
                    getString(R.string.app_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    setSound(null, null)
                }

                val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
            }
        }

        fun create(state: NotificationState): Notification {
            val disconnectIntent = Intent(this@ExchangeService, ExchangeService::class.java)
            disconnectIntent.action = ACTION_STOP
            val disconnectPendingIntent = PendingIntent.getService(
                this@ExchangeService,
                1,
                disconnectIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            Log.d(LOG_TRACE, "update notification: ${state.contentText} | ${state.progressCurrent} / ${state.progressMax}")

            return NotificationCompat.Builder(this@ExchangeService, NotificationChannels.SERVICE)
                .setContentTitle(state.contentText)
                .setSmallIcon(R.mipmap.ic_service)
                .setContentIntent(getMainActivityPendingIntent())
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    getString(R.string.disconnect_label),
                    disconnectPendingIntent
                )
                .setProgress(state.progressMax, state.progressCurrent, false)
                .build()
        }

        fun update(state: ExchangeState) {
            val notificationState = NotificationState.create(state, this@ExchangeService)
            if (this.notificationState != notificationState) {
                this.notificationState = notificationState

                val now = SystemClock.elapsedRealtime()
                val timeSinceLastUpdate = now - lastNotificationUpdateTime

                if (timeSinceLastUpdate >= notificationUpdateIntervalMs) {
                    scheduledNotificationUpdate?.cancel()
                    scheduledNotificationUpdate = null
                    apply()
                    return
                }

                if (scheduledNotificationUpdate == null) {
                    val delayTime = notificationUpdateIntervalMs - timeSinceLastUpdate

                    scheduledNotificationUpdate = notificationScope.launch {
                        delay(delayTime)
                        scheduledNotificationUpdate = null
                        apply()
                    }
                }
            }
        }

        private fun apply() {
            val state = notificationState ?: return

            val notification = create(state)
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NotificationIds.SERVICE, notification)

            lastNotificationUpdateTime = SystemClock.elapsedRealtime()
        }

        fun remove() {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(NotificationIds.SERVICE)
            notificationState = null
        }
    }

    val notification = NotificationUpdater()
}