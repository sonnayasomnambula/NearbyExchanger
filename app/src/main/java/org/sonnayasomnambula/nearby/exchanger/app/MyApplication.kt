package org.sonnayasomnambula.nearby.exchanger.app

import android.app.Application
import org.sonnayasomnambula.nearby.exchanger.model.AndroidDirectoryProvider
import org.sonnayasomnambula.nearby.exchanger.model.AndroidPermissionPolicy
import org.sonnayasomnambula.nearby.exchanger.model.DirectoryProvider
import org.sonnayasomnambula.nearby.exchanger.model.PermissionPolicy

class MyApplication : Application() {
    val directoryProvider: DirectoryProvider by lazy {
        AndroidDirectoryProvider()
    }

    val permissionPolicy : PermissionPolicy by lazy {
        AndroidPermissionPolicy()
    }

    val storage: Storage by lazy {
        DataStoreStorage(applicationContext)
    }
}