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
import com.hiddenwindy.game.RadarConfirmed

/**
 * Two-panel overlay shown after a Radar cast is confirmed.
 *
 * Top card  — dismissable copy panel with the question text.
 * Bottom card — live countdown + Start / Resolve button.
 */
@Composable
fun RadarConfirmPanel(
    confirmed: RadarConfirmed,
    onStartTimer: () -> Unit,
    onResolve: (hiderIsInside: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboardManager = LocalClipboardManager.current

    // Reset to visible whenever a brand-new confirmation is started.
    var copyPanelVisible by remember(confirmed.confirmedAt) { mutableStateOf(true) }
    var showResolveDialog by remember { mutableStateOf(false) }

    val lat = confirmed.center.latitude()
    val lon = confirmed.center.longitude()
    val copyText =
        "Are you within ${confirmed.label} miles of ${"%.5f".format(lat)}, ${"%.5f".format(lon)}?"

    if (showResolveDialog) {
        AlertDialog(
            onDismissRequest = { /* require explicit choice */ },
            title = { Text("Resolve Radar") },
            text = { Text("Are they within ${confirmed.label} miles?") },
            confirmButton = {
                Button(onClick = { onResolve(true); showResolveDialog = false }) {
                    Text("Yes")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { onResolve(false); showResolveDialog = false }) {
                    Text("No")
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
                val minutes = confirmed.remainingSeconds / 60
                val seconds = confirmed.remainingSeconds % 60
                Text(
                    text = "%d:%02d".format(minutes, seconds),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Button(
                    onClick = {
                        if (!confirmed.timerStarted) onStartTimer()
                        else showResolveDialog = true
                    },
                ) {
                    Text(if (confirmed.timerStarted) "Resolve" else "Start")
                }
            }
        }
    }
}
