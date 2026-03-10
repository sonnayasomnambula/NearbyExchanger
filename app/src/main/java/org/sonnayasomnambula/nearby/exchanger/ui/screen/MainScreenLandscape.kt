package org.sonnayasomnambula.nearby.exchanger.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.sonnayasomnambula.nearby.exchanger.model.ConnectionState
import org.sonnayasomnambula.nearby.exchanger.model.MainScreenEvent
import org.sonnayasomnambula.nearby.exchanger.model.MainScreenState
import org.sonnayasomnambula.nearby.exchanger.R
import org.sonnayasomnambula.nearby.exchanger.model.Role

@Composable
fun MainScreenLandscape(
    state: MainScreenState,
    onEvent: (MainScreenEvent) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ){
            Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .padding(16.dp)
                        .width(IntrinsicSize.Max)
                ) {
                    ConnectionStateText(state.connectionState, state.currentRole)
                    RoleSelectorRow(Role.ADVERTISER, state, onEvent)
                    RoleSelectorRow(Role.DISCOVERER, state, onEvent)
                    Spacer(modifier = Modifier.weight(1f))

                    SendRow(state, onEvent)

                    ActionButton(
                        text = stringResource(R.string.disconnect),
                        enabled = state.connectionState == ConnectionState.SEARCHING ||
                                state.connectionState == ConnectionState.CONNECTED,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        onEvent(MainScreenEvent.DisconnectClicked)
                    }

                }
            }

            BigPanel(
                state,
                onEvent,
                Modifier
                    .fillMaxSize()
            )
        }
        if (state.statusText.isNotEmpty()) {
            StatusText(state.statusText)
        }
    }
}
