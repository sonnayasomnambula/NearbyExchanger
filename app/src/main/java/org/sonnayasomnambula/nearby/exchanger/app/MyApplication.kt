package org.sonnayasomnambula.nearby.exchanger.app

import android.app.Application
import org.sonnayasomnambula.nearby.exchanger.model.AndroidDirectoryProvider
import org.sonnayasomnambula.nearby.exchanger.model.DirectoryProvider

class MyApplication : Application() {
    val directoryProvider: DirectoryProvider by lazy {
        AndroidDirectoryProvider()
    }

    val storage: Storage by lazy {
        DataStoreStorage(applicationContext)
    }
}