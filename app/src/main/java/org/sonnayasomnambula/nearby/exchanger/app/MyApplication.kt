package org.sonnayasomnambula.nearby.exchanger.app

import android.app.Application
import org.sonnayasomnambula.nearby.exchanger.model.AndroidLocationProvider
import org.sonnayasomnambula.nearby.exchanger.model.LocationProvider

class MyApplication : Application() {
    val locationProvider: LocationProvider by lazy {
        AndroidLocationProvider()
    }

    val storage: Storage by lazy {
        DataStoreStorage(applicationContext)
    }
}