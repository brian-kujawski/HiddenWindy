package com.hiddenwindy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
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
import com.hiddenwindy.game.TentaclesConfirmed

/**
 * Two-panel overlay shown after a Tentacles question is cast and awaiting the hider's reply.
 *
 * Top card  — dismissable copy panel with the question text and a list of nearby POI names.
 * Bottom card — live countdown + Start Timer / Resolve button.
 *
 * Resolving opens an inline list of nearby POI names; tapping one records the hider's answer.
 */
@Composable
fun TentaclesConfirmPanel(
    confirmed: TentaclesConfirmed,
    onStartTimer: () -> Unit,
    onResolve: (chosenPOIName: String) -> Unit,
    onResolveOutside: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboardManager = LocalClipboardManager.current

    var copyPanelVisible by remember(confirmed.confirmedAt) { mutableStateOf(true) }
    var showResolveList by remember { mutableStateOf(false) }

    val n = confirmed.nearbyPOIs.size
    val pluralLabel = pluralizeFeatureLabel(confirmed.featureLabel)
    val lat = confirmed.seekerLocation.latitude()
    val lon = confirmed.seekerLocation.longitude()
    val copyText =
        "Of the $n $pluralLabel within 1 mile of me, which one are you closest to? " +
        "(You must also be within 1 mile of me.) " +
        "I am at ${"%.5f".format(lat)}, ${"%.5f".format(lon)}."

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

                    // Reference list of nearby POI names
                    if (confirmed.nearbyPOIs.isNotEmpty()) {
                        HorizontalDivider()
                        Text(
                            text = "Nearby ${pluralLabel}:",
                            style = MaterialTheme.typography.labelMedium,
                        )
                        confirmed.nearbyPOIs.forEach { poi ->
                            Text(
                                text = "\u2022 ${poi.name}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
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

        // ── Timer / Resolve panel ───────────────────────────────────────────
        Card {
            Column(modifier = Modifier.fillMaxWidth()) {
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (confirmed.timerStarted) {
                            OutlinedButton(onClick = onDismiss) {
                                Text("Dismiss")
                            }
                        }
                        Button(
                            onClick = {
                                if (!confirmed.timerStarted) onStartTimer()
                                else showResolveList = !showResolveList
                            },
                        ) {
                            Text(
                                when {
                                    !confirmed.timerStarted -> "Start Timer"
                                    showResolveList -> "Cancel"
                                    else -> "Resolve"
                                }
                            )
                        }
                    }
                }

                // Resolve list — shown inline below the timer row
                if (showResolveList) {
                    HorizontalDivider()
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "Which ${confirmed.featureLabel} is the hider closest to?",
                            style = MaterialTheme.typography.labelMedium,
                        )
                        confirmed.nearbyPOIs.forEach { poi ->
                            Button(
                                onClick = {
                                    showResolveList = false
                                    onResolve(poi.name)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(poi.name)
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                showResolveList = false
                                onResolveOutside()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("I am not within 1 mile")
                        }
                    }
                }
            }
        }
    }
}

/** Simple pluralization for the four supported Tentacles POI types. */
private fun pluralizeFeatureLabel(featureLabel: String): String = when (featureLabel) {
    "Library" -> "Libraries"
    else -> "${featureLabel}s"
}
