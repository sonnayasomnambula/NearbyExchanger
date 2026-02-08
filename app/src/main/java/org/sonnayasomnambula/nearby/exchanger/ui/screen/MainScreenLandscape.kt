package org.sonnayasomnambula.nearby.exchanger.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import getDisplayText
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
                    // статус соединения
                    Text(
                        text = state.connectionState.getDisplayText(),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // выбор роли
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // ADVERTISER
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onEvent(MainScreenEvent.RoleSelected(Role.ADVERTISER))
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RadioButton(
                                selected = false,
                                onClick = {
                                    onEvent(MainScreenEvent.RoleSelected(Role.ADVERTISER))
                                }
                            )
                            Text(
                                text = stringResource(R.string.advertiser),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // DISCOVERER
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onEvent(MainScreenEvent.RoleSelected(Role.DISCOVERER))
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RadioButton(
                                selected = false,
                                onClick = {
                                    onEvent(MainScreenEvent.RoleSelected(Role.DISCOVERER))
                                }
                            )
                            Text(
                                text = stringResource(R.string.discoverer),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Кнопка отправки
                    Button(
                        onClick = {
                            onEvent(MainScreenEvent.SendClicked)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        enabled = false,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            text = stringResource(R.string.send_label),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Правая колонка: список локаций
                LocationList(
                    state.locations,
                    state.currentLocation,
                    onEvent,
                    Modifier
                        .fillMaxHeight()
                        .weight(1f))
            }

            // Текст статуса в подвале
            Text(
                text = state.statusText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}