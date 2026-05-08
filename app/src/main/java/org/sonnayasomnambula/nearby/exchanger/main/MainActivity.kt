package org.sonnayasomnambula.nearby.exchanger.main

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
import android.os.Environment
import android.os.IBinder
import android.provider.Settings
import android.util.Log

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
import org.sonnayasomnambula.nearby.exchanger.nearby.ExchangeService
import org.sonnayasomnambula.nearby.exchanger.picker.PickerActivity
import org.sonnayasomnambula.nearby.exchanger.R
import org.sonnayasomnambula.nearby.exchanger.common.CrashDumper
import org.sonnayasomnambula.nearby.exchanger.app.NearbyApplication
import org.sonnayasomnambula.nearby.exchanger.common.LOG_TRACE
import org.sonnayasomnambula.nearby.exchanger.common.Toaster
import org.sonnayasomnambula.nearby.exchanger.main.model.AndroidDeviceEnvironment
import org.sonnayasomnambula.nearby.exchanger.main.model.HardwareCapability
import org.sonnayasomnambula.nearby.exchanger.main.model.MainScreenEffect
import org.sonnayasomnambula.nearby.exchanger.main.model.MainScreenEvent
import org.sonnayasomnambula.nearby.exchanger.main.model.MainViewModel
import org.sonnayasomnambula.nearby.exchanger.main.model.MainViewModelFactory
import org.sonnayasomnambula.nearby.exchanger.nearby.NearbyExchanger
import org.sonnayasomnambula.nearby.exchanger.nearby.TransferEngine
import org.sonnayasomnambula.nearby.exchanger.common.ui.screen.ScreenOrientation

import org.sonnayasomnambula.nearby.exchanger.common.ui.theme.AppTheme
import androidx.core.net.toUri
import kotlin.collections.emptyList

class MainActivity : ComponentActivity() {
    private val LOG_TAG = "org.sonnayasomnambula.nearby.exchanger.main.MainActivity"

    private val crashDumper = CrashDumper(this)

    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(application as NearbyApplication)
    }

    private inner class DocumentPicker {
        private val LOG_TAG = "DocumentPicker"
        private var currentReadOnly: Boolean = false

        private val directoryPicker =
            registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
                uri?.let { uri ->
                    try {
                        requestPermissions(uri)
                        viewModel.directoryPicked(
                            uri = uri,
                            name = uri.lastPathSegment ?: "Folder"
                        )
                    } catch (tr: Exception) {
                        Log.e(LOG_TRACE, "Exception on pick directory", tr)
                        crashDumper.save(tr, "Action: pick directory")
                    }
                }
            }

        private val filePicker =
            registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                uri?.let { uri ->
                    try {
                        requestPermissions(uri)
                        viewModel.filePicked(uri)
                    } catch (throwable: Exception) {
                        crashDumper.save(throwable, "Action: pick file")
                    }
                }
            }

        private val customPicker =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    val paths = result.data?.getStringArrayExtra(PickerActivity.Companion.FILES) ?: return@registerForActivityResult
                    val files = NearbyExchanger.TransferableFile.fromPath(paths, this@MainActivity)
                    viewModel.picked(files);
                }
            }

        fun pickDirectory(readOnly: Boolean) {
            currentReadOnly = readOnly

            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            if (intent.resolveActivity(packageManager) != null) {
                directoryPicker.launch(null)
            } else {
                val text = getString(R.string.not_supported_install_file_manager, intent.action)
                Toaster.show(text, this@MainActivity)
            }
        }

        fun pickFile(readOnly: Boolean) {
            currentReadOnly = readOnly

            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }

            if (intent.resolveActivity(packageManager) != null) {
                filePicker.launch(arrayOf("*/*"))
            } else {
                val text = getString(R.string.not_supported_install_file_manager, intent.action)
                Toaster.show(text, this@MainActivity)
            }
        }

        fun pickCustom() {
            currentReadOnly = true

            val intent = Intent(this@MainActivity, PickerActivity::class.java)
            customPicker.launch(intent)
        }

        private fun requestPermissions(uri: Uri) {
            val flags = if (currentReadOnly) {
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            } else {
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            }

            try {
                contentResolver.takePersistableUriPermission(uri, flags)
            } catch (se: SecurityException) {
                Log.w(LOG_TAG, "Cannot take persistable permission", se)

                try {
                    grantUriPermission(packageName, uri, flags)
                } catch (e: Exception) {
                    Log.e(LOG_TAG, "Failed to grant permission", e)
                }
            }
        }
    }

    private val picker = DocumentPicker()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions /* :Map<String, Boolean> */ ->
        val allGranted = permissions.values.all { it }

        Log.d(LOG_TRACE, "Permissions result: $permissions")
        viewModel.onScreenEvent(MainScreenEvent.PermissionsResult(allGranted))

        if (!allGranted) {
            Toaster.show(getString(R.string.operation_not_allowed), this@MainActivity)
        }
    }

    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val isGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            false
        }

        Log.d(LOG_TRACE, "Manage storage result: $isGranted")
        viewModel.onScreenEvent(MainScreenEvent.PermissionsResult(isGranted))

        if (!isGranted) {
            Toaster.show(getString(R.string.operation_not_allowed), this@MainActivity)
        }
    }

    private var serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(LOG_TRACE, "activity: service connected")
            val binder = service as? ExchangeService.LocalBinder
            binder?.setOnExchangerReadyListener { exchanger ->
                runOnUiThread {
                    viewModel.subscribeToExchanger(exchanger)
                    viewModel.onScreenEvent(MainScreenEvent.ServiceStarted(exchanger.role()))
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(LOG_TRACE, "activity: service disconnected")
            viewModel.onScreenEvent(MainScreenEvent.ServiceStopped)

            bindToService()
        }
    }

    private fun bindToService() {
        val intent = Intent(this, ExchangeService::class.java)
        if (!bindService(intent, serviceConnection, 0)) {
            Log.e(LOG_TRACE, "Unable to bind service ${intent.component?.className}")
        }
    }

    fun Set<HardwareCapability>.toErrorMessage(context: Context): String {
        return when {
            contains(HardwareCapability.Location) &&
                    (contains(HardwareCapability.WiFi) || contains(HardwareCapability.Bluetooth)) ->
                context.getString(R.string.missing_location) + "\n" +
                        context.getString(R.string.missing_wifi_or_bluetooth)

            contains(HardwareCapability.Location) ->
                context.getString(R.string.missing_location)

            else -> context.getString(R.string.missing_wifi_or_bluetooth)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(LOG_TRACE, "activity: created")

        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.activityEffects.collect { effect ->
                    Log.d(LOG_TRACE, "activity: effect ${effect.toString()}")
                    try {
                        when (effect) {
                            is MainScreenEffect.ShowDisconnectedAlert -> {
                                val text =
                                    getString(R.string.device_disconnected, effect.device.name)
                                Toaster.show(text, this@MainActivity)
                            }

                            is MainScreenEffect.ShowMissingCapabilities -> {
                                val context = this@MainActivity
                                val text = effect.missing.toErrorMessage(context)
                                Toaster.show(text, context)
                            }

                            is MainScreenEffect.CheckDirectoryAccess -> {
                                checkDirectoryAccess(effect.uri)
                            }

                            MainScreenEffect.CheckHardwareCapabilities -> {
                                checkHardwareCapabilities()
                            }

                            is MainScreenEffect.RequestPermissions -> {
                                checkPermissions(effect.permissions)
                            }

                            is MainScreenEffect.StartForegroundService -> {
                                ExchangeService.Companion.start(effect.role, this@MainActivity)
                            }

                            is MainScreenEffect.StopForegroundService -> {
                                ExchangeService.Companion.stop(this@MainActivity)
                            }

                            is MainScreenEffect.PickFile -> {
                                picker.pickFile(effect.readOnly)
                            }

                            is MainScreenEffect.PickDirectory -> {
                                picker.pickDirectory(effect.readOnly)
                            }

                            is MainScreenEffect.PickCustom -> {
                                picker.pickCustom()
                            }

                            MainScreenEffect.RequestFileAccess -> {
                                requestFileAccess()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(LOG_TRACE, "Exception on effect: ${effect::class.simpleName}", e)
                        crashDumper.save(e, "Effect: ${effect::class.simpleName}")
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

        viewModel.onScreenEvent(MainScreenEvent.ActivityStarted)

        when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                }

                if (uri == null) {
                    Toaster.show(getString(R.string.cannot_open_shared_content), this)
                } else {
                    viewModel.onScreenEvent(MainScreenEvent.Shared( listOf(NearbyExchanger.TransferableFile.fromUri(uri, this))))
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                }
                if (uris == null) {
                    Toaster.show(getString(R.string.cannot_open_shared_content), this)
                } else {
                    viewModel.onScreenEvent(
                        MainScreenEvent.Shared(
                        files = uris.map { uri ->
                            NearbyExchanger.TransferableFile.fromUri(uri, this)
                        }
                    ))
                }
            }
        }

        // clear the intent so that it is not delivered again
        // (for example, when the screen is rotated)
        intent = Intent(Intent.ACTION_MAIN)
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

        viewModel.onScreenEvent(MainScreenEvent.DirectoryAccessChecked(uri, hasAccess))
    }

    fun checkHardwareCapabilities() {
        val environment = AndroidDeviceEnvironment(this)
        val capabilities = mapOf(
            HardwareCapability.WiFi to environment.isWifiEnabled,
            HardwareCapability.Bluetooth to environment.isBluetoothEnabled,
            HardwareCapability.Location to environment.isLocationEnabled
        )
        viewModel.onScreenEvent(MainScreenEvent.HardwareCapabilitiesChecked(capabilities))
    }

    private fun checkPermissions(permissions: List<String>) {
        Log.d(LOG_TRACE, "check $permissions")
        if (permissions.isEmpty()) {
            viewModel.onScreenEvent(MainScreenEvent.PermissionsResult(true))
        } else {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun requestFileAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                viewModel.onScreenEvent(MainScreenEvent.PermissionsResult(true))
            } else {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = "package:$packageName".toUri()
                manageStorageLauncher.launch(intent)
            }
        }
    }
}
