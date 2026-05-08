package org.sonnayasomnambula.nearby.exchanger.app

import android.app.Application
import android.util.Log
import org.sonnayasomnambula.nearby.exchanger.common.CrashDumper
import org.sonnayasomnambula.nearby.exchanger.common.LOG_TRACE
import org.sonnayasomnambula.nearby.exchanger.main.model.AndroidDirectoryProvider
import org.sonnayasomnambula.nearby.exchanger.main.model.AndroidPermissionPolicy
import org.sonnayasomnambula.nearby.exchanger.main.model.DirectoryProvider
import org.sonnayasomnambula.nearby.exchanger.main.model.PermissionPolicy

class NearbyApplication : Application() {
    val directoryProvider: DirectoryProvider by lazy {
        AndroidDirectoryProvider()
    }

    val permissionPolicy : PermissionPolicy by lazy {
        AndroidPermissionPolicy()
    }

    val storage: Storage by lazy {
        DataStoreStorage(applicationContext)
    }

    override fun onCreate() {
        super.onCreate()
        setupUncaughtExceptionHandler()
    }

    private fun setupUncaughtExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val crashDumper = CrashDumper(applicationContext)
                crashDumper.save(throwable)
            } catch (e: Exception) {
                Log.e(LOG_TRACE, "Crashed while saving crash dump :(", e)
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }
}