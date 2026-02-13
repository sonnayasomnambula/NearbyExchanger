package org.sonnayasomnambula.nearby.exchanger

import MainScreen
import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.Toast

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch


import org.sonnayasomnambula.nearby.exchanger.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    private val LOG_TAG = "org.sonnayasomnambula.nearby.exchanger.MainActivity"
    private val LOG_TRACE = "org.sonnayasomnambula.trace"

    private var serviceConnection: ServiceConnection? = null

    private val viewModel: MainScreenViewModel by viewModels() {
        MainScreenViewModelFactory(application as MyApplication)
    }

    private val folderPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )

                viewModel.addSaveLocation(
                    uri = uri,
                    name = uri.lastPathSegment ?: "Folder"
                )
            }
        }

    @RequiresApi(Build.VERSION_CODES.O)
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        Log.d(LOG_TRACE, if (isGranted) "permission granted" else "permission not granted")
        if (isGranted) {
            AdvertisingService.start(this@MainActivity)
            bindToService()
        } else {
            Toast.makeText(
                this@MainActivity,
                getString(R.string.operation_not_allowed),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun bindToService() {
        serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                Log.d(LOG_TRACE, "activity: service connected")
                val binder = service as? AdvertisingService.LocalBinder
                val advertisingService = binder?.getService()
                viewModel.onEvent(MainScreenEvent.ServiceStarted(Role.ADVERTISER))
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                Log.d(LOG_TRACE, "activity: service disconnected")
                viewModel.onEvent(MainScreenEvent.ServiceStopped)
                serviceConnection = null
            }
        }

        serviceConnection?.let { connection ->
            val intent = Intent(this, AdvertisingService::class.java)
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        } ?: {
            Log.e(LOG_TAG, "Unable to bind service: connection is null")
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(LOG_TRACE, "activity: created")

        super.onCreate(savedInstanceState)

        if (viewModel.state.value.currentRole != null)
            bindToService()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.effects.collect { effect ->
                    Log.d(LOG_TRACE, "activity: effect ${effect.toString()}")
                    when (effect) {
                        is MainScreenEffect.OpenFolderPicker -> {
                            folderPicker.launch(null)
                        }

                        is MainScreenEffect.ShowMessage -> {
                            Toast.makeText(
                                this@MainActivity,
                                effect.text,
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                        is MainScreenEffect.CheckLocationAccess -> {
                            checkLocationAccess(effect.uri)
                        }

                        is MainScreenEffect.StartForegroundService -> {
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                                AdvertisingService.start(this@MainActivity)
                                bindToService()
                            } else {
                                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                        is MainScreenEffect.StopForegroundService -> {
                            AdvertisingService.stop(this@MainActivity)
                            serviceConnection?.let {
                                unbindService(it)
                                serviceConnection = null
                            }
                        }
                    }
                }
            }
        }

        val orientation = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            ScreenOrientation.LANDSCAPE
        } else {
            ScreenOrientation.PORTRAIT
        }

        Log.d(LOG_TRACE, "orientation is ${orientation.name}")

        enableEdgeToEdge()
        setContent {
            AppTheme {
                MainScreen(viewModel, orientation)
            }
        }

        viewModel.onEvent(MainScreenEvent.ActivityStarted)
    }

    override fun onDestroy() {
        serviceConnection?.let {
            unbindService(it)
            serviceConnection = null
        }
        super.onDestroy()
        Log.d(LOG_TRACE, "activity: destroyed")
    }

    private fun checkLocationAccess(uri: Uri) {
        val hasAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Для Android 10+ (API 29+) используем persistedUriPermissions
            contentResolver.persistedUriPermissions.any {
                it.uri == uri && it.isReadPermission && it.isWritePermission
            }
        } else {
            // Для Android ниже 10 проверяем разрешения в манифесте
            val writePermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED

            val readPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Для API 23+ также нужно READ_EXTERNAL_STORAGE
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                // До API 23 разрешения даются при установке
                true
            }

            writePermission && readPermission
        }

        viewModel.onEvent(MainScreenEvent.LocationAccessChecked(uri, hasAccess))
    }
}
