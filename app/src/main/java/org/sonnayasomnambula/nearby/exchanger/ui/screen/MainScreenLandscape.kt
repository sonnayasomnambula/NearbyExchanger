package org.sonnayasomnambula.nearby.exchanger.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.sonnayasomnambula.nearby.exchanger.ConnectionState
import org.sonnayasomnambula.nearby.exchanger.MainScreenEvent
import org.sonnayasomnambula.nearby.exchanger.MainScreenState
import org.sonnayasomnambula.nearby.exchanger.R
import org.sonnayasomnambula.nearby.exchanger.Role

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreenLandscape(
    state: MainScreenState,
    onEvent: (MainScreenEvent) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Левая колонка: статус, роли, кнопка
                Column(
                    modifier = Modifier
                        .width(IntrinsicSize.Min)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    ConnectionStateText(state.connectionState)

                    // выбор роли
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RoleSelectorRow(Role.ADVERTISER, state, onEvent, Modifier.fillMaxWidth())
                        RoleSelectorRow(Role.DISCOVERER, state, onEvent, Modifier.fillMaxWidth())
                    }

                    ActionButton(
                        stringResource(R.string.send_label),
                        state.connectionState == ConnectionState.CONNECTED
                    ) {
                        onEvent(MainScreenEvent.SendClicked)
                    }

                    ActionButton(
                        stringResource(R.string.disconnect_label),
                        state.connectionState == ConnectionState.CONNECTED
                    ) {
                        onEvent(MainScreenEvent.DisconnectClicked)
                    }
                }

                BigPanel(
                    state,
                    onEvent,
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }

            SmallText(state.statusText)
        }
    }
}