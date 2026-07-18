package com.hiddenwindy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hiddenwindy.game.ThermometerActive

/**
 * Two-panel overlay shown after a Thermometer journey is finished.
 *
 * Top card  — dismissable copy panel with the finish-location message to send to the hider.
 * Bottom card — live countdown + Start Timer / Resolve button.
 */
@Composable
fun ThermometerConfirmPanel(
    active: ThermometerActive,
    onStartTimer: () -> Unit,
    onResolve: (hotter: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboardManager = LocalClipboardManager.current

    var copyPanelVisible by remember(active.finishedAt) { mutableStateOf(true) }
    var showResolveDialog by remember { mutableStateOf(false) }

    val finish = active.finish ?: return
    val miles = active.finishedMiles ?: 0.0
    val lat = finish.latitude()
    val lon = finish.longitude()
    val copyText =
        "I've gone ${formatDistanceMiles(miles)} - am I hotter or colder at my new location, " +
        "${"%.5f".format(lat)}, ${"%.5f".format(lon)}?"

    if (showResolveDialog) {
        AlertDialog(
            onDismissRequest = { /* require explicit choice */ },
            title = { Text("Resolve Thermometer") },
            text = { Text("Am I hotter or colder than when I started?") },
            confirmButton = {
                Button(onClick = { onResolve(true); showResolveDialog = false }) {
                    Text("Hotter")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { onResolve(false); showResolveDialog = false }) {
                    Text("Colder")
                }
            },
        )
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // ── Copy panel ──────────────────────────────────────────────────────
        if (copyPanelVisible) {
            Card {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            text = copyText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { copyPanelVisible = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss")
                        }
                    }
                    Button(
                        onClick = { clipboardManager.setText(AnnotatedString(copyText)) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Copy to Clipboard")
                    }
                }
            }
        }

        // ── Timer panel ─────────────────────────────────────────────────────
        Card {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val minutes = active.remainingSeconds / 60
                val seconds = active.remainingSeconds % 60
                Text(
                    text = "%d:%02d".format(minutes, seconds),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Button(
                    onClick = {
                        if (!active.timerStarted) onStartTimer()
                        else showResolveDialog = true
                    },
                ) {
                    Text(if (active.timerStarted) "Resolve" else "Start Timer")
                }
            }
        }
    }
}
