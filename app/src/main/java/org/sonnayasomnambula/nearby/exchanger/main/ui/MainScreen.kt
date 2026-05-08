import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import org.sonnayasomnambula.nearby.exchanger.main.model.MainViewModel
import org.sonnayasomnambula.nearby.exchanger.common.ui.screen.ScreenOrientation

import org.sonnayasomnambula.nearby.exchanger.main.ui.MainLandscape
import org.sonnayasomnambula.nearby.exchanger.main.ui.MainPortrait

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
