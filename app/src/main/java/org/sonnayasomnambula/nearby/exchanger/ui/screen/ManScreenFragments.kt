package org.sonnayasomnambula.nearby.exchanger.ui.screen

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sonnayasomnambula.nearby.exchanger.ConnectionState
import org.sonnayasomnambula.nearby.exchanger.MainScreenEvent
import org.sonnayasomnambula.nearby.exchanger.MainScreenState
import org.sonnayasomnambula.nearby.exchanger.R
import org.sonnayasomnambula.nearby.exchanger.Role
import org.sonnayasomnambula.nearby.exchanger.SaveLocation

@Composable
fun ConnectionState.getDisplayText(): String {
    return when (this) {
        ConnectionState.DISCONNECTED -> stringResource(R.string.connection_state_not_connected)
        ConnectionState.ADVERTISING -> stringResource(R.string.connection_state_advertising)
        ConnectionState.DISCOVERING -> stringResource(R.string.connection_state_discovering)
        ConnectionState.CONNECTED -> stringResource(R.string.connection_state_connected)
    }
}

@Composable
fun ConnectionStateText(connectionState: ConnectionState) {
    Text(
        text = connectionState.getDisplayText(),
        fontSize = 18.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun RoleSelectorRow(
    role: Role,
    state: MainScreenState,
    onEvent: (MainScreenEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clickable {
                onEvent(MainScreenEvent.RoleSelected(role))
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val textResourceId = if (role == Role.ADVERTISER) R.string.advertiser else R.string.discoverer

        RadioButton(
            enabled = state.connectionState != ConnectionState.CONNECTED,
            selected = state.currentRole == role,
            onClick = {
                onEvent(MainScreenEvent.RoleSelected(role))
            }
        )
        Text(
            text = stringResource(textResourceId),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun ActionButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        enabled = enabled,
        shape = MaterialTheme.shapes.medium
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun BigPanel(
    state: MainScreenState,
    onEvent: (MainScreenEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state.connectionState) {
        ConnectionState.DISCONNECTED -> {
            StartingHint(modifier)
        }
        ConnectionState.ADVERTISING,
        ConnectionState.DISCOVERING-> {
            AvailableDevicesList()
        }
        ConnectionState.CONNECTED -> {
            LocationList(state.locations, state.currentLocation, onEvent, modifier)
        }
    }
}

@Composable
private fun StartingHint(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.starting_hint),
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun AvailableDevicesList() {

}

@Composable
fun LocationList(
    saveLocations: List<SaveLocation>,
    currentSaveLocation: Uri?,
    onEvent: (MainScreenEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    var locationToDelete by remember { mutableStateOf<Uri?>(null) }

    if (locationToDelete != null) {
        AlertDialog(
            onDismissRequest = { locationToDelete = null },
            title = { Text(stringResource(R.string.delete_location_title)) },
            text = { Text(stringResource(R.string.delete_location_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onEvent(MainScreenEvent.RemoveLocationRequested(locationToDelete!!))
                        locationToDelete = null
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(
                    onClick = { locationToDelete = null }
                ) { Text("Cancel") }
            }
        )
    }

    Card(
        modifier = modifier,
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
            if (saveLocations.isEmpty()) {
                // Пустой список
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(),
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
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(saveLocations) { location ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        onEvent(MainScreenEvent.LocationSelected(location.uri))
                                    },
                                    onLongClick = {
                                        locationToDelete = location.uri
                                    },
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = location.uri == currentSaveLocation,
                                onClick = {
                                    onEvent(MainScreenEvent.LocationSelected(location.uri))
                                }
                            )

                            Column(
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                Text(
                                    text = location.name,
                                    fontSize = 14.sp,
                                    fontWeight = if (location.uri == currentSaveLocation) FontWeight.Medium
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
}

@Composable
fun SmallText(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        modifier = Modifier.fillMaxWidth()
    )
}