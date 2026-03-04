package org.sonnayasomnambula.nearby.exchanger.nearby

import com.google.android.gms.nearby.connection.Strategy

const val SERVICE_ID = "org.sonnayasomnambula.nearby.exchanger"
val STRATEGY: Strategy = Strategy.P2P_STAR

object NotificationChannels {
    const val SERVICE = "service_channel"
    const val CRASH_REPORT = "crash_channel"
}

object NotificationIds {
    const val SERVICE = 1001
    const val CRASH_REPORT = 1002
}
