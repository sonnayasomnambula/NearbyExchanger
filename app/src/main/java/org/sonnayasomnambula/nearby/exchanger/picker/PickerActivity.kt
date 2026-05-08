package org.sonnayasomnambula.nearby.exchanger.picker

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import org.sonnayasomnambula.nearby.exchanger.app.NearbyApplication
import org.sonnayasomnambula.nearby.exchanger.common.CrashDumper
import org.sonnayasomnambula.nearby.exchanger.common.LOG_TRACE
import org.sonnayasomnambula.nearby.exchanger.nearby.TransferEngine
import org.sonnayasomnambula.nearby.exchanger.common.ui.screen.ScreenOrientation
import org.sonnayasomnambula.nearby.exchanger.picker.ui.PickerScreen
import org.sonnayasomnambula.nearby.exchanger.common.ui.theme.AppTheme
import org.sonnayasomnambula.nearby.exchanger.nearby.NearbyExchanger
import org.sonnayasomnambula.nearby.exchanger.picker.model.LegacyFileManager
import org.sonnayasomnambula.nearby.exchanger.picker.model.Picker
import org.sonnayasomnambula.nearby.exchanger.picker.model.PickerScreenEffect
import org.sonnayasomnambula.nearby.exchanger.picker.model.PickerViewModel
import org.sonnayasomnambula.nearby.exchanger.picker.model.PickerViewModelFactory
import kotlin.getValue

class PickerActivity : ComponentActivity(), Picker {
    companion object {
        const val FILES = "files"
    }

    private val crashDumper = CrashDumper(this)

    private val viewModel: PickerViewModel by viewModels {
        PickerViewModelFactory(application as NearbyApplication)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(LOG_TRACE, "picker activity: created")
        super.onCreate(savedInstanceState)

        onBackPressedDispatcher.addCallback(this) {
            viewModel.goBack()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.activityEffects.collect { effect ->
                    Log.d(LOG_TRACE, "picker: effect $effect")
                    try {
                        when (effect) {
                            PickerScreenEffect.Cancel -> close()
                            is PickerScreenEffect.Open -> openFile(effect.file)
                        }
                    } catch (e: Exception) {
                        Log.e(LOG_TRACE, "Exception on effect: ${effect::class.simpleName}", e)
                        crashDumper.save(e, "Pick Effect: ${effect::class.simpleName}")
                    }
                }
            }
        }

        val orientation = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            ScreenOrientation.LANDSCAPE
        } else {
            ScreenOrientation.PORTRAIT
        }

        enableEdgeToEdge()
        setContent {
            AppTheme {
                PickerScreen(this@PickerActivity, viewModel, orientation)
            }
        }
    }

    override fun accept() {
        val pathList = viewModel.checkedFiles().map { it.path }
        setResult(RESULT_OK, Intent().apply {
            putExtra(FILES, pathList.toTypedArray())
        })
        finish()
    }

    override fun close() {
        setResult(RESULT_CANCELED)
        finish()
    }

    private fun openFile(file: Picker.File) {
        val javaFile = (file as? LegacyFileManager.File)?.file ?: return
        val uri = FileProvider.getUriForFile(
            this,
            "${packageName}.provider",
            javaFile
        )
        val intent = Intent(Intent.ACTION_VIEW, uri)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(intent)
    }
}