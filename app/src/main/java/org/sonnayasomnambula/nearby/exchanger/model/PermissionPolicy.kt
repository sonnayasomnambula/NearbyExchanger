package org.sonnayasomnambula.nearby.exchanger.model

import android.Manifest
import android.os.Build

interface PermissionPolicy {
    fun permissionsFor(role: Role): List<String>
}

class AndroidPermissionPolicy : PermissionPolicy {
    override fun permissionsFor(role: Role): List<String> = buildSet {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            add(Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC)
        }

        when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> {
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
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toList()
}
