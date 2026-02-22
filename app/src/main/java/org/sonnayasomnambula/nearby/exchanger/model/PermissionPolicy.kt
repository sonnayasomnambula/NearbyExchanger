package org.sonnayasomnambula.nearby.exchanger.model

import android.Manifest
import android.os.Build

interface PermissionPolicy {
    fun permissionsFor(role: Role): List<String>
}

class AndroidPermissionPolicy : PermissionPolicy {
    override fun permissionsFor(role: Role): List<String> {
        val list = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list += Manifest.permission.NEARBY_WIFI_DEVICES
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            list += Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            list += Manifest.permission.ACCESS_FINE_LOCATION
        } else {
            list += Manifest.permission.BLUETOOTH_ADVERTISE
            list += Manifest.permission.BLUETOOTH_CONNECT
            list += Manifest.permission.BLUETOOTH_SCAN
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            list += Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list += Manifest.permission.POST_NOTIFICATIONS
        }

        return list
    }
}
