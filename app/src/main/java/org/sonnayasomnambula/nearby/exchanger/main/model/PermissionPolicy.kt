package org.sonnayasomnambula.nearby.exchanger.main.model

import android.Manifest
import android.os.Build
import android.os.Environment
import android.util.Log
import org.sonnayasomnambula.nearby.exchanger.common.LOG_TRACE

interface PermissionPolicy {
    fun permissionsFor(role: Role): List<String>
    fun hasLegacyStorageAccess(): Boolean
    fun storageAccessPermissions(): List<String>
}

class AndroidPermissionPolicy : PermissionPolicy {
    init {
        Log.d(LOG_TRACE, "current API level: ${Build.VERSION.SDK_INT}")
    }

    override fun permissionsFor(role: Role): List<String> = buildSet {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            add(Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC)
        }

        when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> {
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            else -> {
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_SCAN)
            }
        }

        if (role == Role.DISCOVERER) {
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
            add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toList()

    override fun hasLegacyStorageAccess(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    override fun storageAccessPermissions(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ - нужен MANAGE_EXTERNAL_STORAGE
            listOf(Manifest.permission.MANAGE_EXTERNAL_STORAGE)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6-10
            listOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        } else {
            // Android 5 и ниже - разрешения не нужны
            emptyList()
        }
    }
}
