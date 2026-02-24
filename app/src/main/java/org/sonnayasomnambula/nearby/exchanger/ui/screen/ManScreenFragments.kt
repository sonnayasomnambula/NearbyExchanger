package org.sonnayasomnambula.nearby.exchanger.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sonnayasomnambula.nearby.exchanger.model.ConnectionState
import org.sonnayasomnambula.nearby.exchanger.model.MainScreenEvent
import org.sonnayasomnambula.nearby.exchanger.model.MainScreenState
import org.sonnayasomnambula.nearby.exchanger.R
import org.sonnayasomnambula.nearby.exchanger.model.RemoteDevice
import org.sonnayasomnambula.nearby.exchanger.model.Role
import org.sonnayasomnambula.nearby.exchanger.model.correspondingRole

@Composable
fun ConnectionState.getDisplayText(): String {
    return when (this) {
        ConnectionState.DISCONNECTED -> stringResource(R.string.connection_state_not_connected)
        ConnectionState.ADVERTISING -> stringResource(R.string.connection_state_advertising)
        ConnectionState.DISCOVERING -> stringResource(R.string.connection_state_discovering)
        ConnectionState.CONNECTED -> stringResource(R.string.connection_state_connected)
        ConnectionState.ERROR -> stringResource(R.string.connection_state_error)
    }
}

@Composable
fun RemoteDevice.ConnectionState.getDisplayString(): String {
    return when (this) {
        RemoteDevice.ConnectionState.DISCONNECTED -> stringResource(R.string.device_status_disconnected)
        RemoteDevice.ConnectionState.CONNECTING -> stringResource(R.string.device_status_connecting)
        RemoteDevice.ConnectionState.AWAITING_CONFIRM -> stringResource(R.string.device_status_awaiting_confirm)
        RemoteDevice.ConnectionState.CONNECTED -> stringResource(R.string.device_status_connected)
    }
}

fun RemoteDevice.ConnectionState.getColor(): Color {
    return when (this) {
        RemoteDevice.ConnectionState.DISCONNECTED -> Color.Gray
        RemoteDevice.ConnectionState.CONNECTING -> Color(0xFFFF9800)
        RemoteDevice.ConnectionState.AWAITING_CONFIRM -> Color(0xFF9C27B0)
        RemoteDevice.ConnectionState.CONNECTED ->Color(0xFF2196F3)
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
            .clickable(enabled = state.connectionState == ConnectionState.DISCONNECTED) {
                onEvent(MainScreenEvent.RoleSelected(role))
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val textResourceId = if (role == Role.ADVERTISER) R.string.advertiser else R.string.discoverer

        RadioButton(
            enabled = state.connectionState == ConnectionState.DISCONNECTED,
            selected = state.connectionState.correspondingRole == role,
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
            StaticText(stringResource(R.string.starting_hint), modifier)
        }
        ConnectionState.ADVERTISING,
        ConnectionState.DISCOVERING-> {
            DevicesList(
                state.devices,
                { device->
                    onEvent(MainScreenEvent.DeviceClicked(device))
                },
                modifier)
        }
        ConnectionState.CONNECTED -> {
            DirectoryList(state.saveDirs, state.currentDir, onEvent, modifier)
        }
        ConnectionState.ERROR -> {
            StaticText("", modifier)
        }
    }
}

@Composable
private fun StaticText(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
        )
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