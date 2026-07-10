package com.hiddenwindy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private enum class QuestionsScreen(val displayName: String) {
    HOME("Questions"),
    MATCHING("Matching"),
    MEASURING("Measuring"),
    THERMOMETER("Thermometer"),
    RADAR("Radar"),
    TENTACLES("Tentacles"),
    PHOTOS("Photos"),
}

private val TOP_LEVEL_SCREENS = listOf(
    QuestionsScreen.MATCHING,
    QuestionsScreen.MEASURING,
    QuestionsScreen.THERMOMETER,
    QuestionsScreen.RADAR,
    QuestionsScreen.TENTACLES,
    QuestionsScreen.PHOTOS,
)

@Composable
fun QuestionsModal(onDismiss: () -> Unit) {
    var screen by remember { mutableStateOf(QuestionsScreen.HOME) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 24.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {

                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (screen != QuestionsScreen.HOME) {
                        IconButton(onClick = { screen = QuestionsScreen.HOME }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    }
                    Text(
                        text = screen.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(Modifier.height(16.dp))

                if (screen == QuestionsScreen.HOME) {
                    QuestionsGrid(onSelect = { screen = it })
                } else {
                    QuestionsSubmenu(screen = screen)
                }
            }
        }
    }
}

@Composable
private fun QuestionsGrid(onSelect: (QuestionsScreen) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TOP_LEVEL_SCREENS.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowItems.forEach { option ->
                    Button(
                        onClick = { onSelect(option) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = option.displayName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                // Pad incomplete rows
                repeat(2 - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun QuestionsSubmenu(screen: QuestionsScreen) {
    when (screen) {
        QuestionsScreen.RADAR -> RadarSubmenu()
        else -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "${screen.displayName} options coming soon.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private val RADAR_RADII = listOf("1/4", "1/2", "1", "3", "5", "10", "25", "???")

@Composable
private fun RadarSubmenu() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

        // Casting info
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(
                    text = "COST: Draw 2, Pick 1",
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = "TIME: 5 Minutes",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }

        HorizontalDivider()

        // Radius buttons — 4 columns × 2 rows
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RADAR_RADII.chunked(4).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowItems.forEach { label ->
                        Button(
                            onClick = { },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = label,
                                maxLines = 1,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}
