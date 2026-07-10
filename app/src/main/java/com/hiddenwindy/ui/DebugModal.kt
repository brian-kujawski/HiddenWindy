package com.hiddenwindy.ui

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun DebugModal(
    isDebugLocationActive: Boolean,
    onToggleDebugLocation: () -> Unit,
    isSpoofLocationActive: Boolean,
    onToggleSpoofLocation: () -> Unit,
    onDismiss: () -> Unit,
) {
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
                    Text(
                        text = "Debug",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = { onToggleSpoofLocation(); if (isSpoofLocationActive) onDismiss() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (isSpoofLocationActive) "No Spoof Location" else "Spoof Location")
                }

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = { onToggleDebugLocation(); onDismiss() },
                    enabled = !isSpoofLocationActive,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (isDebugLocationActive) "No More Bean" else "Make Me Bean")
                }
            }
        }
    }
}
