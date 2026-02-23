package org.sonnayasomnambula.nearby.exchanger.ui.screen

import android.net.Uri
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sonnayasomnambula.nearby.exchanger.R
import org.sonnayasomnambula.nearby.exchanger.model.MainScreenEvent
import org.sonnayasomnambula.nearby.exchanger.model.SaveDir

@Composable
fun DirectoryList(
    saveDirs: List<SaveDir>,
    currentDir: Uri?,
    onEvent: (MainScreenEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    var dirsToDelete by remember { mutableStateOf<Uri?>(null) }

    if (dirsToDelete != null) {
        AlertDialog(
            onDismissRequest = { dirsToDelete = null },
            title = { Text(stringResource(R.string.remove_directory_title)) },
            text = { Text(stringResource(R.string.remove_directory_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onEvent(MainScreenEvent.RemoveDirectoryRequested(dirsToDelete!!))
                        dirsToDelete = null
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(
                    onClick = { dirsToDelete = null }
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
                        onEvent(MainScreenEvent.AddDirectoryRequested)
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.add_directory),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Список мест сохранения
            if (saveDirs.isEmpty()) {
                // Пустой список
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.add_directory_proposal),
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
                    items(saveDirs) { dir ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        onEvent(MainScreenEvent.DirectorySelected(dir.uri))
                                    },
                                    onLongClick = {
                                        dirsToDelete = dir.uri
                                    },
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = dir.uri == currentDir,
                                onClick = {
                                    onEvent(MainScreenEvent.DirectorySelected(dir.uri))
                                }
                            )

                            Column(
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                Text(
                                    text = dir.name,
                                    fontSize = 14.sp,
                                    fontWeight = if (dir.uri == currentDir) FontWeight.Medium
                                    else FontWeight.Normal
                                )
                                Text(
                                    text = dir.uri.toString(),
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