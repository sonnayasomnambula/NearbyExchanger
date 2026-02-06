package org.sonnayasomnambula.nearby.exchanger.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            // статус соединения

            Text(
                text = "Not connected",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth()
            )

            // выбор роли

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // ADVERTISER
                Row(
                    modifier = Modifier
                        .weight(1f)
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
                        .weight(1f)
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

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    // Заголовок с кнопкой добавления
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.save_to),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Кнопка "плюсик" для добавления
                        IconButton(
                            onClick = {
                                onEvent(MainScreenEvent.AddLocationRequested)
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = stringResource(R.string.add_location),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Список мест сохранения
                    if (state.saveLocations.isEmpty()) {
                        // Пустой список
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.add_location_proposal),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    } else {
                        // Список выбранных папок с radio buttons
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            state.saveLocations.forEach { location ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onEvent(MainScreenEvent.LocationSelected(location.id))
                                        },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = location.isSelected,
                                        onClick = {
                                            onEvent(MainScreenEvent.LocationSelected(location.id))
                                        }
                                    )

                                    Column(
                                        modifier = Modifier.padding(start = 8.dp)
                                    ) {
                                        Text(
                                            text = location.name,
                                            fontSize = 14.sp,
                                            fontWeight = if (location.isSelected) FontWeight.Medium
                                            else FontWeight.Normal
                                        )
                                        Text(
                                            text = location.uri.toString(),
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Text(
                text = state.statusText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}