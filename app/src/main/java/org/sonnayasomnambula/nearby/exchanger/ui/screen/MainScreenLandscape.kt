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
import org.sonnayasomnambula.nearby.exchanger.model.MainScreenEvent
import org.sonnayasomnambula.nearby.exchanger.model.MainScreenState
import org.sonnayasomnambula.nearby.exchanger.model.Role

@Composable
fun MainScreenLandscape(
    state: MainScreenState,
    onEvent: (MainScreenEvent) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
    ){
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.medium),
                modifier = Modifier
                    .padding(Padding.medium)
                    .width(IntrinsicSize.Max)
            ) {
                MenuRow(state)
                RoleSelectorRow(Role.ADVERTISER, state, onEvent)
                RoleSelectorRow(Role.DISCOVERER, state, onEvent)
                Spacer(Modifier.weight(1f))

                SendRow(state, onEvent, Modifier.fillMaxWidth(), Arrangement.SpaceEvenly)
                StopButton(state.connectionState, onEvent)
            }
        }

        Column(
            Modifier.fillMaxSize()
        ) {
            BigPanel(
                state,
                onEvent,
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )

            if (state.pendingShare.isNotEmpty()) {
                PendingShareRow(
                    state.pendingShare,
                    onEvent,
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Padding.medium)
                )
            }
        }
    }
}
