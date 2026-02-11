package org.sonnayasomnambula.nearby.exchanger.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
fun MainScreenPortrait(
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
            ConnectionStateText(state.connectionState)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                RoleSelectorRow(Role.ADVERTISER, state, onEvent, Modifier.weight(1f))
                RoleSelectorRow(Role.DISCOVERER, state, onEvent, Modifier.weight(1f))
            }

            ActionButton(
                stringResource(R.string.send_label),
                state.connectionState == ConnectionState.CONNECTED
            ) {
                onEvent(MainScreenEvent.SendClicked)
            }

            BigPanel(
                state,
                onEvent,
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )

            ActionButton(
                stringResource(R.string.disconnect_label),
                state.connectionState == ConnectionState.CONNECTED
            ) {
                onEvent(MainScreenEvent.DisconnectClicked)
            }

            SmallText(state.statusText)
        }
    }
}