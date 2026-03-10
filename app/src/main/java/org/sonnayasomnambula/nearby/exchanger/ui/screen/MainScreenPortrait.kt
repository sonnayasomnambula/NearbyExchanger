package org.sonnayasomnambula.nearby.exchanger.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreenPortrait(
    state: MainScreenState,
    onEvent: (MainScreenEvent) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Box(
                modifier = Modifier
                    .padding(8.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ConnectionStateText(
                        state.connectionState,
                        state.currentRole,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        RoleSelectorRow(Role.ADVERTISER, state, onEvent)
                        RoleSelectorRow(Role.DISCOVERER, state, onEvent)
                    }
                    SendRow(state, onEvent)
                }
            }
        }

        BigPanel(
            state,
            onEvent,
            Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Box(
                modifier = Modifier
                    .padding(8.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ActionButton(
                        text = stringResource(R.string.disconnect),
                        enabled = state.connectionState == ConnectionState.SEARCHING ||
                                  state.connectionState == ConnectionState.CONNECTED,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        onEvent(MainScreenEvent.DisconnectClicked)
                    }

                    if (state.statusText.isNotEmpty()) {
                        StatusText(state.statusText)
                    }
                }
            }
        }
    }
}
