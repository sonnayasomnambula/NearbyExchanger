import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import org.sonnayasomnambula.nearby.exchanger.ConnectionState
import org.sonnayasomnambula.nearby.exchanger.MainScreenViewModel

import org.sonnayasomnambula.nearby.exchanger.R
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
    val state by viewModel.state.collectAsState()

    when (orientation) {
        ScreenOrientation.PORTRAIT -> {
            MainScreenPortrait(
                state = state,
                onEvent = viewModel::onEvent
            )
        }
        ScreenOrientation.LANDSCAPE -> {
            MainScreenLandscape(
                state = state,
                onEvent = viewModel::onEvent
            )
        }
    }
}
