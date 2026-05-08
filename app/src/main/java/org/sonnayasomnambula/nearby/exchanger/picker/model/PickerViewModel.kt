package org.sonnayasomnambula.nearby.exchanger.picker.model

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable.isActive
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.sonnayasomnambula.nearby.exchanger.common.LOG_TRACE
import kotlin.coroutines.coroutineContext

enum class SelectionState {
    NotSelected,
    Selected,
    PartiallySelected
}

data class FileItem(
    val file: Picker.File,
    val selectionState: SelectionState,
    val count: Int = 0,
    val size: Long = -1
)

sealed class PickerScreenState {
    abstract val totalCount: Int
    abstract val totalSize: Long
    data class Volumes(
        val items: List<Picker.Volume>,
        override val totalCount: Int,
        override val totalSize: Long
    ) : PickerScreenState()
    data class Files(
        val items: List<List<FileItem>>,
        val parent: Picker.File?,
        override val totalCount: Int,
        override val totalSize: Long
    ) : PickerScreenState()
}

sealed interface PickerScreenEvent {
    data object Up: PickerScreenEvent
    data object Back: PickerScreenEvent
    data class VolumeClicked(val volume: Picker.Volume): PickerScreenEvent
    data class FileClicked(val file: Picker.File): PickerScreenEvent
    data class FileSelected(val file: Picker.File, val isSelected: Boolean): PickerScreenEvent
}

sealed interface PickerScreenEffect {
    data object Cancel: PickerScreenEffect
    data class Open(val file: Picker.File): PickerScreenEffect
}

class FileNode(
    val file: Picker.File,
    val parent: FileNode? = null,
    @Volatile var children: MutableMap<String, FileNode>? = null,
    @Volatile var selectionState: SelectionState = SelectionState.NotSelected,
    @Volatile var totalCount: Int = 0,
    @Volatile var totalSize: Long = -1L
) {
    private val childrenLock = Any()
    
    fun ensureChildren(): Map<String, FileNode> {
        if (!file.isDirectory) return emptyMap()
        children?.let { return it }
        synchronized(childrenLock) {
            if (children == null) {
                children = file.children()
                    .associate { child ->
                        child.path to FileNode(
                            file = child,
                            parent = this,
                            selectionState = selectionState
                        )
                    }
                    .toMutableMap()
            }
            return children!!
        }
    }

    fun clearChildren() {
        synchronized(childrenLock) {
            children = null
        }
    }

    override fun toString(): String =
        "FileNode($file, state=$selectionState)"
}

class VolumeNode(
    val volume: Picker.Volume,
    val node: FileNode = FileNode(file = volume.file())
)

class PickerViewModel(
    fileManager: LegacyFileManager
) : ViewModel() {

    init {
        Log.d(LOG_TRACE, "picker: model created")
    }

    private val _event = MutableSharedFlow<PickerScreenEvent>()
    val event = _event.asSharedFlow()

    private val _activityEffects = Channel<PickerScreenEffect>(Channel.BUFFERED)
    val activityEffects = _activityEffects.receiveAsFlow()

    private val _navigationStack = MutableStateFlow<List<String>>(listOf(""))
    private val _treeVersion = MutableStateFlow(0)

    private val volumeRoots: Map<String, VolumeNode> =
        fileManager.volumes.associate { volume ->
            volume.path to VolumeNode(volume)
        }

    val currentScreen: StateFlow<PickerScreenState> = _navigationStack
        .combine(_treeVersion) { pathStack, _ -> pathStack }
        .map { pathStack ->
            val currentPath = pathStack.lastOrNull() ?: ""
            val currentNode = getNodeByPath(currentPath)

            if (currentNode == null) {
                PickerScreenState.Volumes(
                    items = volumeRoots.values.map { it.volume },
                    totalSize = volumeRoots.values.sumOf { it.node.totalSize },
                    totalCount = volumeRoots.values.sumOf { it.node.totalCount }
                )
            } else {
                val parentNode = currentNode.parent
                val currentItems = buildFileItems(currentNode)
                val parentItems = parentNode?.let { buildFileItems(it) }
                val items = listOfNotNull(parentItems, currentItems)
                PickerScreenState.Files(
                    items = items,
                    parent = currentNode.file,
                    totalSize = volumeRoots.values.sumOf { it.node.totalSize },
                    totalCount = volumeRoots.values.sumOf { it.node.totalCount }
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, PickerScreenState.Volumes(emptyList(), 0, -1L))

    private var calculationJob: Job? = null

    private fun buildFileItems(node: FileNode): List<FileItem> {
        return node.ensureChildren().values.map { child ->
            FileItem(
                file = child.file,
                selectionState = child.selectionState,
                count = child.totalCount,
                size = child.totalSize
            )
        }
    }

    private fun getNodeByPath(path: String?): FileNode? {
        if (path.isNullOrEmpty()) return null
        for ((volumePath, volumeNode) in volumeRoots) {
            if (path == volumePath) return volumeNode.node
            if (path.startsWith(volumePath)) {
                return findDescendant(volumeNode.node, path)
            }
        }
        return null
    }

    private fun findDescendant(node: FileNode, targetPath: String): FileNode? {
        val children = node.ensureChildren()
        for (child in children.values) {
            if (child.file.path == targetPath) return child
            if (targetPath.startsWith(child.file.path + "/")) {
                return findDescendant(child, targetPath)
            }
        }
        return null
    }

    private fun cancel() {
        viewModelScope.launch {
            _activityEffects.send(PickerScreenEffect.Cancel)
        }
    }

    fun go(file: Picker.File) {
        Log.d(LOG_TRACE, "go $file")

        if (!file.isDirectory) {
            viewModelScope.launch {
                _activityEffects.send(PickerScreenEffect.Open(file))
            }
            return
        }

        _navigationStack.update { it + file.path }
    }

    fun goRoot() {
        _navigationStack.update { it + "" }
    }

    fun canGoBack(): Boolean = _navigationStack.value.size > 1

    fun goBack() {
        if (canGoBack()) {
            _navigationStack.update { stack -> stack.dropLast(1) }
        } else {
            cancel()
        }
    }

    private fun goUp() {
        val currentPath = _navigationStack.value.lastOrNull()
            ?: return cancel()

        val currentNode = getNodeByPath(currentPath)
            ?: return cancel()

        val parent = currentNode.parent
            ?: return goRoot()

        go(parent.file)
    }

    fun check(file: Picker.File) {
        calculationJob?.cancel()

        val node = findNodeForPath(file.path) ?: return

        node.clearChildren()

        node.selectionState = if (node.selectionState == SelectionState.NotSelected) {
            SelectionState.Selected
        } else {
            SelectionState.NotSelected
        }

        updateParents(node.parent)

        _treeVersion.update { it + 1 }

        calculationJob = startCalculation()
    }

    private fun findNodeForPath(path: String): FileNode? {
        val currentPath = _navigationStack.value.lastOrNull() ?: return null
        var currentNode = getNodeByPath(currentPath)

        while (currentNode != null) {
            currentNode.ensureChildren()[path]?.let { return it }
            currentNode = currentNode.parent
        }

        return null
    }

    private fun checkedFiles(node: FileNode): List<Picker.File> {
        return when (node.selectionState) {
            SelectionState.NotSelected -> emptyList()
            SelectionState.Selected -> {
                if (node.file.isDirectory) {
                    node.ensureChildren().values.flatMap { checkedFiles(it) }
                } else {
                    listOf(node.file)
                }
            }
            SelectionState.PartiallySelected -> {
                node.ensureChildren().values.flatMap { checkedFiles(it) }
            }
        }
    }

    fun checkedFiles(): List<Picker.File> {
        return volumeRoots.values.flatMap { checkedFiles(it.node) }
    }

    private fun updateParents(node: FileNode?) {
        var current = node
        while (current != null) {
            val children = current.children?.values
            if (children.isNullOrEmpty()) {
                current = current.parent
                continue
            }

            val allSelected = children.all { it.selectionState == SelectionState.Selected }
            val allNotSelected = children.all { it.selectionState == SelectionState.NotSelected }

            current.selectionState = when {
                allSelected -> SelectionState.Selected
                allNotSelected -> SelectionState.NotSelected
                else -> SelectionState.PartiallySelected
            }

            current = current.parent
        }
    }

    private fun startCalculation(): Job =  viewModelScope.launch(Dispatchers.IO) {
        for ((_, volumeNode) in volumeRoots) {
            recalculateNode(volumeNode.node)
        }
        _treeVersion.update { it + 1 }
    }

    private suspend fun recalculateNode(node: FileNode) {
        if (!coroutineContext.isActive) return

        when (node.selectionState) {
            SelectionState.NotSelected -> {
                node.totalCount = 0
                node.totalSize = -1
            }
            SelectionState.Selected, SelectionState.PartiallySelected -> {
                if (node.file.isDirectory) {
                    val children = node.ensureChildren()
                    var count = 0
                    var size = 0L
                    for (child in children.values) {
                        if (!coroutineContext.isActive) return
                        recalculateNode(child)
                        count += child.totalCount
                        size += child.totalSize
                    }
                    node.totalCount = count
                    node.totalSize = size
                } else {
                    node.totalCount = 1
                    node.totalSize = node.file.length()
                }
            }
        }
    }

    fun onScreenEvent(event: PickerScreenEvent) {
        Log.d(LOG_TRACE, "picker: $event")
        when (event) {
            is PickerScreenEvent.Up -> goUp()
            is PickerScreenEvent.Back -> goBack()
            is PickerScreenEvent.VolumeClicked -> go(event.volume.file())
            is PickerScreenEvent.FileClicked -> go(event.file)
            is PickerScreenEvent.FileSelected -> check(event.file)
        }
    }
}