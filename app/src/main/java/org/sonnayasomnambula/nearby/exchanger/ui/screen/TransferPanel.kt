package org.sonnayasomnambula.nearby.exchanger.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import org.sonnayasomnambula.nearby.exchanger.model.MainScreenState

import org.sonnayasomnambula.nearby.exchanger.nearby.TransferState
import org.sonnayasomnambula.nearby.exchanger.nearby.TransferStatistics

val TransferStatistics.hasData: Boolean
    get() = totalSize > 0

val MainScreenState.hasTransfers: Boolean
    get() = incoming.statistics.hasData || outgoing.statistics.hasData

@Composable
private fun rememberMaxLabelWidth(): Dp {
    val maxText = "Current: 999.9 MB / 999.9 MB"
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val textStyle = MaterialTheme.typography.labelSmall

    return remember(maxText, textStyle) {
        with(density) {
            textMeasurer.measure(
                text = AnnotatedString(maxText),
                style = textStyle
            ).size.width.toDp()
        }
    }
}

@Composable
fun TransferPanel(
    incoming: TransferState,
    outgoing: TransferState,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(0.dp)
    ) {
        if (incoming.statistics.hasData) {
            TransferBlock(
                title = "Incoming",
                state = incoming,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (outgoing.statistics.hasData) {
            TransferBlock(
                title = "Outgoing",
                state = outgoing,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onStop,
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentWidth(Alignment.CenterHorizontally)
                .padding(bottom = 16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            )
        ) {
            Text("Stop")
        }
    }
}

@Composable
private fun TransferProgress(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 300)
    )

    LinearProgressIndicator(
        progress = { animatedProgress },
        modifier = modifier
                    .fillMaxWidth()
                    .height(6.dp),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.outlineVariant,
        gapSize = 0.dp,
        drawStopIndicator = { }
    )
}

@Composable
private fun TransferLabel(
    text: String,
    modifier: Modifier = Modifier
) {
    val labelWidth = rememberMaxLabelWidth()

    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier
            .width(labelWidth),
        textAlign = TextAlign.End,
        maxLines = 1
    )
}

@Composable
private fun TransferBlock(
    title: String,
    state: TransferState,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = state.statistics.current.ifEmpty { "Waiting..." },
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                textAlign = TextAlign.Center
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TransferProgress(
                    progress = if (state.progress.currentSize > 0)
                        state.progress.currentProgress.toFloat() / state.progress.currentSize
                    else 0f,
                    modifier = Modifier.weight(1f)
                )

                TransferLabel(
                    text = "${formatSize(state.progress.currentProgress)} / ${formatSize(state.progress.currentSize)}",
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TransferProgress(
                    progress = if (state.statistics.totalSize > 0)
                        state.statistics.totalProgress.toFloat() / state.statistics.totalSize
                    else 0f,
                    modifier = Modifier.weight(1f)
                )

                TransferLabel(
                    text = "${formatSize(state.statistics.totalProgress)} / ${formatSize(state.statistics.totalSize)}",
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Queue (${state.statistics.queue.size})",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(state.statistics.queue) { fileName ->
                    Text(
                        text = "• $fileName",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun formatSize(size: Long): String {
    return if (size < 1024) {
        "$size B"
    } else {
        val units = arrayOf("KB", "MB", "GB")
        var value = size.toFloat()
        var unitIndex = -1

        while (value >= 1024 && unitIndex < units.size - 1) {
            value /= 1024
            unitIndex++
        }

        "${String.format("%.1f", value)} ${units[unitIndex]}"
    }
}