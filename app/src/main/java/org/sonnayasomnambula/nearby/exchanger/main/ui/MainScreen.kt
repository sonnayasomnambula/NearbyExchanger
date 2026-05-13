import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import org.sonnayasomnambula.nearby.exchanger.main.model.MainViewModel
import org.sonnayasomnambula.nearby.exchanger.common.ui.screen.ScreenOrientation
import org.sonnayasomnambula.nearby.exchanger.common.ui.theme.AppTheme
import org.sonnayasomnambula.nearby.exchanger.main.model.ConnectionState
import org.sonnayasomnambula.nearby.exchanger.main.model.MainScreenState
import org.sonnayasomnambula.nearby.exchanger.main.model.Role

import org.sonnayasomnambula.nearby.exchanger.main.ui.MainLandscape
import org.sonnayasomnambula.nearby.exchanger.main.ui.MainPortrait
import org.sonnayasomnambula.nearby.exchanger.picker.model.PickerScreenState
import org.sonnayasomnambula.nearby.exchanger.picker.ui.DummyPicker
import org.sonnayasomnambula.nearby.exchanger.picker.ui.PickerLandscape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    orientation: ScreenOrientation = ScreenOrientation.PORTRAIT
) {
    val state by viewModel.screenState.collectAsState()

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(
                WindowInsets.systemBars
            ),
        color = MaterialTheme.colorScheme.background
    ) {
        when (orientation) {
            ScreenOrientation.PORTRAIT -> {
                MainPortrait(
                    state,
                    viewModel::onScreenEvent
                )
            }
            ScreenOrientation.LANDSCAPE -> {
                MainLandscape(
                    state,
                    viewModel::onScreenEvent
                )
            }
        }
    }
}

@Preview(
    name = "Tablet",
    widthDp = 460,
    heightDp = 820,
    showBackground = true,
//    uiMode = UI_MODE_NIGHT_YES
)
@Composable
fun MainScreenPreview() {
    val state = MainScreenState(
        connectionState = ConnectionState.DISCONNECTED,
        currentRole = Role.DISCOVERER
    )

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(
                WindowInsets.systemBars
            ),
        color = MaterialTheme.colorScheme.background
    ) {
        MainPortrait(
            state,
            {}
        )
    }
}
