package org.sonnayasomnambula.nearby.exchanger

import android.app.Application

class MyApplication : Application() {
    val locationProvider: LocationProvider by lazy {
        AndroidLocationProvider()
    }

    val storage: Storage by lazy {
        DataStoreStorage(applicationContext)
    }
}