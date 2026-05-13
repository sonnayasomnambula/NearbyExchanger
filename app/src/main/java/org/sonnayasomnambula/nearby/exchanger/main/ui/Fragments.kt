package org.sonnayasomnambula.nearby.exchanger.main.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sonnayasomnambula.nearby.exchanger.main.model.ConnectionState
import org.sonnayasomnambula.nearby.exchanger.main.model.MainScreenEvent
import org.sonnayasomnambula.nearby.exchanger.main.model.MainScreenState
import org.sonnayasomnambula.nearby.exchanger.R
import org.sonnayasomnambula.nearby.exchanger.main.model.RemoteDevice
import org.sonnayasomnambula.nearby.exchanger.main.model.Role
import org.sonnayasomnambula.nearby.exchanger.nearby.TransferEngine
import org.sonnayasomnambula.nearby.exchanger.common.ui.screen.FontSize
import org.sonnayasomnambula.nearby.exchanger.common.ui.screen.Padding
import org.sonnayasomnambula.nearby.exchanger.common.ui.screen.Spacing

@Composable
fun ConnectionState.getDisplayText(role: Role?): String {
    return when (this) {
        ConnectionState.DISCONNECTED -> stringResource(R.string.connection_state_not_connected)
        ConnectionState.STARTING -> stringResource(R.string.connection_state_starting)
        ConnectionState.SEARCHING -> when (role) {
            Role.ADVERTISER -> stringResource(R.string.connection_state_advertising)
            Role.DISCOVERER -> stringResource(R.string.connection_state_discovering)
            null -> ""
        }
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
fun List<TransferEngine.File>.getDisplayText(): String {
    if (isEmpty()) return ""

    val resources = LocalContext.current.resources

    return if (size < 10) {
        val names = joinToString(" ") { it.path }
        if (names.length < 100) {
            resources.getQuantityString(
                R.plurals.pending_files_list,
                size,
                names
            )
        } else {
            resources.getQuantityString(
                R.plurals.pending_files_count,
                size,
                size
            )
        }
    } else {
        resources.getQuantityString(
            R.plurals.pending_files_count,
            size,
            size
        )
    }
}

@Composable
fun ConnectionStateText(connectionState: ConnectionState, role: Role?) {
    Text(
        text = connectionState.getDisplayText(role),
        fontSize = FontSize.headerText,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
fun RoleSelectorRow(
    role: Role,
    state: MainScreenState,
    onEvent: (MainScreenEvent) -> Unit
) {
    Row(
        modifier = Modifier
            .clickable(enabled = state.connectionState == ConnectionState.DISCONNECTED) {
                onEvent(MainScreenEvent.RoleSelected(role))
            },
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val textResourceId = if (role == Role.ADVERTISER) R.string.advertiser else R.string.discoverer

        RadioButton(
            enabled = state.connectionState == ConnectionState.DISCONNECTED || state.connectionState == ConnectionState.ERROR,
            selected = (state.connectionState == ConnectionState.STARTING || state.connectionState == ConnectionState.SEARCHING) && state.currentRole == role,
            onClick = {
                onEvent(MainScreenEvent.RoleSelected(role))
            },
            modifier = Modifier.size(20.dp), // removes extra padding https://stackoverflow.com/a/71850751
        )

        Text(
            text = stringResource(textResourceId),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
fun ActionButton(
    text: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier
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
fun ActionButton(
    icon: ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .width(60.dp)
            .height(56.dp),
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
fun StopButton(
    connectionState: ConnectionState,
    onEvent: (MainScreenEvent) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    ActionButton(
        text = stringResource(
            if (connectionState == ConnectionState.CONNECTED)
                R.string.disconnect
            else
                R.string.stop),
        enabled = connectionState == ConnectionState.SEARCHING ||
                connectionState == ConnectionState.CONNECTED,
        modifier = modifier
    ) {
        onEvent(MainScreenEvent.DisconnectClicked)
    }
}

@Composable
fun MenuRow(
    state: MainScreenState,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ConnectionStateText(
            state.connectionState,
            state.currentRole,
        )

        MenuButton()
    }
}

@Composable
fun SendRow(
    state: MainScreenState,
    onEvent: (MainScreenEvent) -> Unit,
    modifier: Modifier,
    horizontalArrangement: Arrangement.Horizontal
) {
    Row(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement
    ) {
        ActionButton(
            Icons.AutoMirrored.Filled.InsertDriveFile,
            state.connectionState == ConnectionState.CONNECTED && !state.hasActiveTransfer
        ) {
            onEvent(MainScreenEvent.SendFileClicked)
        }
        Spacer(modifier = Modifier.width(Padding.medium))
        ActionButton(
            Icons.Filled.Folder,
            state.connectionState == ConnectionState.CONNECTED && !state.hasActiveTransfer
        ) {
            onEvent(MainScreenEvent.SendFolderClicked)
        }
        Spacer(modifier = Modifier.width(16.dp))
        ActionButton(
            Icons.Filled.AddCircle,
            state.connectionState == ConnectionState.CONNECTED && !state.hasActiveTransfer
        ) {
            onEvent(MainScreenEvent.SendCustomClicked)
        }
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
            StaticText(
                text = stringResource(R.string.starting_hint),
                modifier = modifier
            )
//            TransferPanel(
//                incoming = TransferState(
//                    TransferStatistics(
//                        queue = listOf("music/02.mp3", "music/03.mp3"),
//                        current = "music/01.mp3",
//                        totalSize = 54,
//                        totalProgress = 4
//                    ),
//                    TransferProgress(12, 4)
//
//                ),
//                outgoing = state.outgoing,
//                onStop = { onEvent(MainScreenEvent.StopTransfers) },
//                modifier = modifier
//            )
        }
        ConnectionState.STARTING ->
            StaticText(
                text = stringResource(R.string.connection_state_starting),
                modifier = modifier
            )
        ConnectionState.SEARCHING-> {
            DevicesList(
                state.devices,
                { device->
                    onEvent(MainScreenEvent.DeviceClicked(device))
                },
                modifier)
        }
        ConnectionState.CONNECTED -> {
            if (state.hasActiveTransfer) {
                TransferPanel(
                    incoming = state.incoming,
                    outgoing = state.outgoing,
                    onStop = { onEvent(MainScreenEvent.StopTransfers) },
                    modifier = modifier
                )
            } else {
                DirectoryList(state.saveDirs, state.currentDir, onEvent, modifier)
            }
        }
        ConnectionState.ERROR -> {
            StaticText(state.errorText ?: "", modifier)
        }
    }
}

@Composable
private fun StaticText(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        fontSize = 16.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier
            .padding(Padding.medium)
            .wrapContentHeight(align = Alignment.CenterVertically)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val packageInfo = remember { context.packageManager.getPackageInfo(context.packageName, 0) }
    val uriHandler = LocalUriHandler.current

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .wrapContentSize()
            .padding(Padding.medium)
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .padding(Padding.large)
                    .wrapContentSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val sourcesUrl = "https://github.com/sonnayasomnambula/NearbyExchanger"

                val iconUrl = "https://www.flaticon.com/free-icon/transfer_876784"
                val iconUrlText = stringResource(R.string.icon_by_becris)

                Text(
                    text = "${stringResource(R.string.app_name)} ${packageInfo.versionName}",
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = Padding.medium)
                )

                Text(
                    text = sourcesUrl,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = FontSize.small,
                        textDecoration = TextDecoration.Underline,
                        color = MaterialTheme.colorScheme.primary
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clickable { uriHandler.openUri(sourcesUrl) }
                        .padding(top = Padding.small)
                )

                Text(
                    text = iconUrlText,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = FontSize.small,
                        textDecoration = TextDecoration.Underline,
                        color = MaterialTheme.colorScheme.primary
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clickable { uriHandler.openUri(iconUrl) }
                        .padding(top = Padding.small)
                )
            }
        }
    }
}

@Composable
fun StatusText(text: String, modifier: Modifier = Modifier.fillMaxWidth()) {
    Text(
        text = text,
        fontSize = FontSize.medium,
        fontWeight = FontWeight.Normal,
        modifier = modifier
    )
}

@Composable
fun PendingShareRow(
    pendingShare: List<TransferEngine.File>,
    onEvent: (MainScreenEvent) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    Row(modifier = modifier) {
        StatusText(
            pendingShare.getDisplayText(),
            Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = stringResource(R.string.clear),
            modifier = Modifier
                .clickable { onEvent(MainScreenEvent.ClearPendingShare) }
        )
    }
}

@Composable
fun MenuButton() {
    var expanded by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }

    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier
                .size(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.menu)
            )
        }

        if (showAbout) {
            AboutDialog(onDismiss = { showAbout = false })
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            val itemStyle = TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = FontSize.menuText,
            )

            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.about),
                        style = itemStyle
                    )
                       },
                onClick = {
                    expanded = false
                    showAbout = true
                }
            )
        }
    }
}