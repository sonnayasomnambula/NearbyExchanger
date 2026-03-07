package org.sonnayasomnambula.nearby.exchanger.model

import android.bluetooth.BluetoothManager
import android.content.Context
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings

interface DeviceEnvironment {
    val isLocationEnabled: Boolean
    val isBluetoothEnabled: Boolean
    val isWifiEnabled: Boolean
    val isAirplaneMode: Boolean
}

class AndroidDeviceEnvironment(
    private val context: Context
) : DeviceEnvironment {

    override val isLocationEnabled: Boolean
        get() {
            val lm = context.getSystemService(LocationManager::class.java)
            return if (Build.VERSION.SDK_INT >= 28) {
                lm.isLocationEnabled
            } else {
                Settings.Secure.getInt(
                    context.contentResolver,
                    Settings.Secure.LOCATION_MODE,
                    Settings.Secure.LOCATION_MODE_OFF
                ) != Settings.Secure.LOCATION_MODE_OFF
            }
        }

    override val isBluetoothEnabled: Boolean
        get() {
            val manager = context.getSystemService(BluetoothManager::class.java)
            return manager?.adapter?.isEnabled == true
        }

    override val isWifiEnabled: Boolean
        get() {
            val wifi = context.applicationContext
                .getSystemService(WifiManager::class.java)
            return wifi?.isWifiEnabled == true
        }

    override val isAirplaneMode: Boolean
        get() {
            return Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.AIRPLANE_MODE_ON,
                0
            ) == 1
        }
}