package org.sonnayasomnambula.nearby.exchanger.app

import android.app.Application
import android.util.Log
import org.sonnayasomnambula.nearby.exchanger.LOG_TRACE
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