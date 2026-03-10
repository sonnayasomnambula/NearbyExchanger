import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import org.sonnayasomnambula.nearby.exchanger.MainActivity
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
                MainScreenPortrait(
                    state,
                    viewModel::onScreenEvent
                )
            }
            ScreenOrientation.LANDSCAPE -> {
                MainScreenLandscape(
                    state,
                    viewModel::onScreenEvent
                )
            }
        }
    }
}
