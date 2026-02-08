package org.sonnayasomnambula.nearby.exchanger

import MainScreen
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.widget.Toast

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch


import org.sonnayasomnambula.nearby.exchanger.ui.theme.AppTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainScreenViewModel by viewModels()

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.effects.collect { effect ->
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
                MainScreen(viewModel, orientation)
            }
        }

        viewModel.onEvent(MainScreenEvent.ActivityStarted)
    }
}
