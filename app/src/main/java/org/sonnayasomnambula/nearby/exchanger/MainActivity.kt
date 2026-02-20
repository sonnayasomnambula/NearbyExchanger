package org.sonnayasomnambula.nearby.exchanger

import MainScreen
import android.Manifest
import android.content.ComponentName
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
import org.sonnayasomnambula.nearby.exchanger.app.MyApplication
import org.sonnayasomnambula.nearby.exchanger.model.MainScreenEffect
import org.sonnayasomnambula.nearby.exchanger.model.MainScreenEvent
import org.sonnayasomnambula.nearby.exchanger.model.MainScreenViewModel
import org.sonnayasomnambula.nearby.exchanger.model.MainScreenViewModelFactory
import org.sonnayasomnambula.nearby.exchanger.model.Role
import org.sonnayasomnambula.nearby.exchanger.service.AdvertisingService
import org.sonnayasomnambula.nearby.exchanger.service.ExchangeService


import org.sonnayasomnambula.nearby.exchanger.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    private val LOG_TAG = "org.sonnayasomnambula.nearby.exchanger.MainActivity"
    private val LOG_TRACE = "org.sonnayasomnambula.trace"

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

                viewModel.addSaveDir(
                    uri = uri,
                    name = uri.lastPathSegment ?: "Folder"
                )
            }
        }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions /* :Map<String, Boolean> */ ->
        val allGranted = permissions.values.all { it }

        Log.d(LOG_TRACE, "Permissions result: $permissions")
        viewModel.onEvent(MainScreenEvent.PermissionsResult(allGranted))

        if (!allGranted) {
            Toast.makeText(
                this@MainActivity,
                getString(R.string.operation_not_allowed),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private var serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(LOG_TRACE, "activity: service connected")
            val binder = service as? AdvertisingService.LocalBinder
            val advertisingService = binder?.getService()
            viewModel.subscribeToService(advertisingService as ExchangeService)
            viewModel.onEvent(MainScreenEvent.ServiceStarted(Role.ADVERTISER))
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(LOG_TRACE, "activity: service disconnected")
            viewModel.onEvent(MainScreenEvent.ServiceStopped)

            bindToService()
        }
    }

    private fun bindToService() {
        val intent = Intent(this, AdvertisingService::class.java)
        bindService(intent, serviceConnection, 0)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(LOG_TRACE, "activity: created")

        super.onCreate(savedInstanceState)

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

                        is MainScreenEffect.CheckDirectoryAccess -> {
                            checkDirectoryAccess(effect.uri)
                        }

                        is MainScreenEffect.RequestPermissions -> {
                            checkPermissions(effect.permissions)
                        }

                        is MainScreenEffect.StartForegroundService -> {
                            AdvertisingService.start(this@MainActivity)
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

    override fun onStart() {
        super.onStart()
        bindToService()
    }

    override fun onStop() {
        super.onStop()
        unbindService(serviceConnection)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(LOG_TRACE, "activity: destroyed")
    }

    private fun checkDirectoryAccess(uri: Uri) {
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

        viewModel.onEvent(MainScreenEvent.DirectoryAccessChecked(uri, hasAccess))
    }

    private fun checkPermissions(permissions: List<String>) {
        Log.d(LOG_TRACE, "check $permissions")
        if (permissions.isEmpty()) {
            viewModel.onEvent(MainScreenEvent.PermissionsResult(true))
        } else {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }
    }
}
