package org.sonnayasomnambula.nearby.exchanger.picker.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.motionEventSpy
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.sonnayasomnambula.nearby.exchanger.R
import org.sonnayasomnambula.nearby.exchanger.common.ui.screen.FontSize
import org.sonnayasomnambula.nearby.exchanger.common.ui.screen.Padding
import org.sonnayasomnambula.nearby.exchanger.common.ui.screen.ScreenOrientation
import org.sonnayasomnambula.nearby.exchanger.common.ui.screen.Spacing
import org.sonnayasomnambula.nearby.exchanger.common.ui.screen.humanReadableSize
import org.sonnayasomnambula.nearby.exchanger.common.ui.theme.AppTheme
import org.sonnayasomnambula.nearby.exchanger.picker.model.FileItem
import org.sonnayasomnambula.nearby.exchanger.picker.model.Picker
import org.sonnayasomnambula.nearby.exchanger.picker.model.PickerScreenEvent
import org.sonnayasomnambula.nearby.exchanger.picker.model.PickerScreenState
import org.sonnayasomnambula.nearby.exchanger.picker.model.PickerViewModel
import org.sonnayasomnambula.nearby.exchanger.picker.model.SelectionState
import org.sonnayasomnambula.nearby.exchanger.picker.ui.DummyPicker.DummyDir
import org.sonnayasomnambula.nearby.exchanger.picker.ui.DummyPicker.DummyFile
import kotlin.random.Random



fun PickerScreenState.parentName(): String? = when (this) {
    is PickerScreenState.Volumes -> null
    is PickerScreenState.Files -> parent?.name
}

fun PickerScreenState.titleOr(defaultTitle: String): String {
    val rootName = parentName() ?: return defaultTitle
    return if (rootName.length > 20)
        rootName.take(12) + "\u2026" else rootName
}

fun SelectionState.toToggleableState() = when (this) {
    SelectionState.Selected -> ToggleableState.On
    SelectionState.NotSelected -> ToggleableState.Off
    SelectionState.PartiallySelected -> ToggleableState.Indeterminate
}

@Composable
fun PickerScreen(
    picker: Picker,
    viewModel: PickerViewModel,
    orientation: ScreenOrientation
) {
    val state by viewModel.currentScreen.collectAsState()

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(
                WindowInsets.systemBars
            ),
        color = MaterialTheme.colorScheme.background
    ) {
        when (orientation) {
            ScreenOrientation.PORTRAIT -> { PickerPortrait(picker, state, viewModel::onScreenEvent) }
            ScreenOrientation.LANDSCAPE -> { PickerLandscape(picker, state, viewModel::onScreenEvent) }
        }
    }
}

class DummyPicker : Picker {
    override fun accept() {}
    override fun close() {}

    private data class DummyFileImpl(
        override val path: String,
        override val isDirectory: Boolean
    ) : Picker.File {
        override val name: String = path.substringAfterLast("/")
        override fun length() = 0L
        override fun children(): List<Picker.File> = emptyList()
        override fun equals(other: Any?) = other is DummyFile && other.path == this.path
        override fun hashCode() = 31 * isDirectory.hashCode() + path.hashCode()
    }

    class DummyFile(path: String) : Picker.File by DummyFileImpl(path, false)
    class DummyDir(path: String) : Picker.File by DummyFileImpl(path, true)

    class DummyVolume(
        override val name: String,
        override val path: String,
        override val isRemovable: Boolean
    ) : Picker.Volume {
        override fun file(): Picker.File {
            return DummyDir(name)
        }
    }

    private val primaryVolume = DummyVolume(
        name = "Primary storage",
        path = "/storage/emulated/0",
        isRemovable = false
    )

    private val sdCard = DummyVolume(
        name = "SD Card",
        path = "/storage/emulated/sdcard",
        isRemovable = true
    )

    val volumes = listOf(primaryVolume, sdCard)
    val folders = listOf(
        DummyDir("/DCIM"),
        DummyDir("/Documents"),
        DummyDir("/Downlads"),
        DummyDir("/Music"),
        DummyFile("/nearby.log"),
        DummyFile("/TODO.txt")
    )
    val files = listOf(
        DummyDir("/Music/Paul Mauriat orchestra -  I WON'T LAST A DAY WITHOUT YOU (1974)"),
        DummyDir("/Music/Niels Henning-Orsted Pederson trio - To A Brother (1993)"),
        DummyFile("/Music/Marco Parisi plays Prince_s _Purple Rain_ on the Seaboard RISE at NAMM 2017.720p60.mp4"),
    )
    val parent = folders[3]
}

fun List<Picker.File>.mapToFileItems() : List<FileItem> =
    this.map { file ->
        FileItem(
            file = file,
            selectionState = SelectionState.NotSelected,
            count = if (file.isDirectory) Random.nextInt(5, 100) else 1,
            size = Random.nextLong(1, 2_147_483_648)
        )
    }

@Preview(
    name = "Tablet",
    widthDp = 720,
    heightDp = 420,
    showBackground = true,
//    uiMode = UI_MODE_NIGHT_YES
)
@Composable
fun FileListPreview() {
    AppTheme {
        val dummyPicker = DummyPicker()
        val dummyState = PickerScreenState.Files(
            parent = dummyPicker.parent,
            items = listOf(dummyPicker.folders.mapToFileItems()
                .mapIndexed { i,  item -> if (i == 3) item.copy(selectionState = SelectionState.PartiallySelected) else item },
                           dummyPicker.files.mapToFileItems()
                               .mapIndexed { i, item -> if (i > 0) item.copy(selectionState = SelectionState.Selected) else item }
            ),
            totalCount = 2,
            totalSize = 12_000_000
        )
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.systemBars
                ),
        ) {
            PickerLandscape(dummyPicker, dummyState, {})
        }
    }
}

@Preview(
    name = "Tablet",
    widthDp = 720,
    heightDp = 420,
    showBackground = true,
//    uiMode = UI_MODE_NIGHT_YES
)
@Composable
fun VolumeListPreview() {
    AppTheme {
        val dummyPicker = DummyPicker()
        val dummyState = PickerScreenState.Volumes(
            items = dummyPicker.volumes,
            totalSize = -1L,
            totalCount = 0
        )
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.systemBars
                ),
        ) {
            PickerLandscape(dummyPicker, dummyState, {})
        }
    }
}

@Composable
fun PickerPortrait(
    picker: Picker,
    state: PickerScreenState,
    onEvent: (PickerScreenEvent) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
//            tonalElevation = 4.dp,
//            shadowElevation = 4.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier.padding(Padding.medium),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Padding.medium)
            ){
                IconButton(
                    onClick = { onEvent(PickerScreenEvent.Up) },
                    modifier = Modifier.size(22.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null
                    )
                }

                Text(
                    text = state.titleOr(stringResource(R.string.files)),
                    fontSize = FontSize.headerText,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        when (state) {
            is PickerScreenState.Volumes -> VolumeList(
                volumes = state.items,
                onEvent = onEvent,
                modifier = Modifier.weight(1f)
            )
            is PickerScreenState.Files -> state.items.lastOrNull()?.let { files ->
                FileList(
                    files = files,
                    onEvent = onEvent,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Padding.small),
                horizontalArrangement = Arrangement.spacedBy(Padding.medium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.totalCount > 0) {
                    Text(
                        text = "${humanReadableSize(state.totalSize)} (${state.totalCount})",
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = FontSize.small,
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Button(onClick = { picker.accept() }) {
                    Text(text = stringResource(R.string.pick))
                }

                Button(onClick = { picker.close() }) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        }
    }
}

@Composable
fun PickerLandscape(
    picker: Picker,
    state: PickerScreenState,
    onEvent: (PickerScreenEvent) -> Unit
) {
    Row {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
                .padding(Padding.medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.small)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.small)
            ) {
                IconButton(
                    onClick = { onEvent(PickerScreenEvent.Up) },
                    modifier = Modifier
                        .size(22.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null
                    )
                }

                Text(
                    text = stringResource(R.string.files),
                    fontSize = FontSize.headerText,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            if (state.totalCount > 0) {
                Column(modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(
                        text = humanReadableSize(state.totalSize),
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = FontSize.small,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                    )

                    val resources = LocalContext.current.resources

                    Text(
                        text = resources.getQuantityString(
                            R.plurals.files,
                            state.totalCount,
                            state.totalCount
                        ),
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = FontSize.small,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                    )
                }

            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { picker.accept() },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(text = stringResource(R.string.pick))
            }

            Button(
                onClick = { picker.close() },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(text = stringResource(R.string.cancel))
            }
        }

        when (state) {
            is PickerScreenState.Volumes -> VolumeList(
                volumes = state.items,
                onEvent = onEvent,
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
            )

            is PickerScreenState.Files -> Row {
                Row {
                    state.items.forEachIndexed { index, files ->
                        val isLast = index == state.items.lastIndex
                        FileList(
                            files = files,
                            selection = state.parent,
                            onEvent = onEvent,
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(if (isLast) 3f else 2f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VolumeList(
    volumes: List<Picker.Volume>,
    onEvent: (PickerScreenEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
    ) {
        items(volumes) { volume ->
            Column(
                modifier = Modifier.clickable {
                    onEvent(PickerScreenEvent.VolumeClicked(volume))
                }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Padding.small, horizontal = Padding.medium),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (volume.isRemovable) {
                        Icon(
                            Icons.Filled.SdStorage,
                            contentDescription = null // TODO
                        )
                    } else {
                        Icon(
                            Icons.Filled.Storage,
                            contentDescription = null // TODO
                        )
                    }

                    Spacer(modifier = Modifier.width(Padding.medium))

                    Column {
                        Text(
                            text = volume.name,
                            fontSize = FontSize.menuText
                        )
                        Text(
                            text = volume.path,
                            fontSize = FontSize.small,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = Padding.medium),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                thickness = 0.5.dp
            )
        }
    }
}

@Composable
fun FileList(
    files: List<FileItem>,
    selection: Picker.File? = null,
    onEvent: (PickerScreenEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(files, selection) {
        val selectedIndex = files.indexOfFirst { it.file == selection }
        if (selectedIndex != -1) {
            listState.animateScrollToItem(selectedIndex)
        }
    }

    if (files.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
            )
        }
    } else {

        LazyColumn(
            state = listState,
            modifier = modifier
                .background(MaterialTheme.colorScheme.background)
        ) {
            items(files) { item ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(0.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Padding.medium)
                            .background(
                                if (selection == item.file)
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                                else
                                    Color.Transparent
                            )
                            .clickable {
                                onEvent(PickerScreenEvent.FileClicked(item.file))
                            }
                    ) {
                        if (item.file.isDirectory) {
                            Icon(
                                Icons.Filled.Folder,
                                contentDescription = null
                            )
                        } else {
                            Icon(
                                Icons.AutoMirrored.Filled.InsertDriveFile,
                                contentDescription = null
                            )
                        }

                        Spacer(modifier = Modifier.width(Padding.medium))

                        Text(
                            text = item.file.name,
                            modifier = Modifier.weight(1f),
                            color = if (selection == item.file)
                                MaterialTheme.colorScheme.onSecondary
                            else
                                Color.Unspecified
                        )

                        if (item.selectionState != SelectionState.NotSelected && item.size >= 0) {
                            Text(
                                text = humanReadableSize(item.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        TriStateCheckbox(
                            state = item.selectionState.toToggleableState(),
                            onClick = {
                                onEvent(
                                    PickerScreenEvent.FileSelected(
                                        item.file,
                                        isSelected = true
                                    )
                                )
                            }
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = Padding.medium),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        thickness = 0.5.dp
                    )
                }
            }
        }
    }
}