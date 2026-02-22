import androidx.compose.material3.*
import androidx.compose.runtime.*
import org.sonnayasomnambula.nearby.exchanger.model.MainScreenViewModel

import org.sonnayasomnambula.nearby.exchanger.ui.screen.MainScreenLandscape
import org.sonnayasomnambula.nearby.exchanger.ui.screen.MainScreenPortrait

enum class ScreenOrientation {
    PORTRAIT,
    LANDSCAPE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainScreenViewModel,
    orientation: ScreenOrientation = ScreenOrientation.PORTRAIT
) {
    val state by viewModel.screenState.collectAsState()

    when (orientation) {
        ScreenOrientation.PORTRAIT -> {
            MainScreenPortrait(
                state = state,
                onEvent = viewModel::onScreenEvent
            )
        }
        ScreenOrientation.LANDSCAPE -> {
            MainScreenLandscape(
                state = state,
                onEvent = viewModel::onScreenEvent
            )
        }
    }
}
