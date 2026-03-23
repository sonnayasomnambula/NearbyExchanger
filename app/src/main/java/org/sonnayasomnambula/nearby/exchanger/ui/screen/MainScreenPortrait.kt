package org.sonnayasomnambula.nearby.exchanger.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.sonnayasomnambula.nearby.exchanger.model.MainScreenEvent
import org.sonnayasomnambula.nearby.exchanger.model.MainScreenState
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
        verticalArrangement = Arrangement.spacedBy(Spacing.medium)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Box(
                modifier = Modifier
                    .padding(Padding.medium)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.medium)
                ) {
                    MenuRow(state)

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
                        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
                        itemVerticalAlignment = Alignment.CenterVertically
                    ) {
                        RoleSelectorRow(Role.ADVERTISER, state, onEvent)
                        RoleSelectorRow(Role.DISCOVERER, state, onEvent)
                        SendRow(state, onEvent, Modifier.weight(1f), Arrangement.End)
                    }
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
                    .padding(Padding.medium)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.medium)
                ) {
                    StopButton(state.connectionState, onEvent)

                    if (state.pendingShare.isNotEmpty()) {
                        PendingShareRow(state.pendingShare, onEvent, Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}
