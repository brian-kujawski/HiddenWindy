package com.hiddenwindy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.hiddenwindy.game.GameState
import com.hiddenwindy.game.GameViewModel
import com.hiddenwindy.map.MapViewContainer

/** Main screen: full-screen map with an info card on top and the game control panel at the bottom. */
@Composable
fun MapScreen(
    viewModel: GameViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.onLocationPermissionGranted()
    }

    LaunchedEffect(Unit) {
        val permission = Manifest.permission.ACCESS_FINE_LOCATION
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            viewModel.onLocationPermissionGranted()
        } else {
            locationPermissionLauncher.launch(permission)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        MapViewContainer(viewModel = viewModel, modifier = Modifier.fillMaxSize())

        InfoCard(
            state = state,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(12.dp),
        )

        if (state.selectedStation == null) {
            HamburgerMenu(
                onNewGame = viewModel::resetGame,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp),
            )
        }

        ControlPanel(
            state = state,
            onToggleHidingZone = viewModel::toggleHidingZone,
            onLockZone = viewModel::lockHidingZone,
            onUnlockZone = viewModel::unlockHidingZone,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(12.dp),
        )
    }
}

@Composable
private fun HamburgerMenu(
    onNewGame: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showNewGameDialog by remember { mutableStateOf(false) }
    var showQuestionsModal by remember { mutableStateOf(false) }

    if (showQuestionsModal) {
        QuestionsModal(onDismiss = { showQuestionsModal = false })
    }

    if (showNewGameDialog) {
        AlertDialog(
            onDismissRequest = { showNewGameDialog = false },
            title = { Text("Start a New Game?") },
            text = { Text("This will clear all overlays and reset the current game.") },
            confirmButton = {
                TextButton(onClick = { onNewGame(); showNewGameDialog = false }) {
                    Text("New Game")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewGameDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    Box(modifier = modifier) {
        IconButton(
            onClick = { menuExpanded = true },
            modifier = Modifier.size(96.dp),
        ) {
            Icon(Icons.Default.Menu, contentDescription = "Menu", modifier = Modifier.size(48.dp))
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("Questions") },
                onClick = { menuExpanded = false; showQuestionsModal = true },
            )
            DropdownMenuItem(
                text = { Text("Rules") },
                onClick = { menuExpanded = false },
            )
            DropdownMenuItem(
                text = { Text("History") },
                onClick = { menuExpanded = false },
            )
            DropdownMenuItem(
                text = { Text("New Game") },
                onClick = { menuExpanded = false; showNewGameDialog = true },
            )
        }
    }
}

@Composable
private fun InfoCard(state: GameState, modifier: Modifier = Modifier) {
    val station = state.selectedStation ?: return
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(station.name, fontWeight = FontWeight.Bold)
            Text("Lines: ${station.lines.joinToString(", ").ifEmpty { "—" }}")
            val cuts = state.eliminators.size
            if (cuts > 0) {
                Text("Active cuts: $cuts", fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun ControlPanel(
    state: GameState,
    onToggleHidingZone: () -> Unit,
    onLockZone: () -> Unit,
    onUnlockZone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.selectedStation == null) return

    var showUnlockDialog by remember { mutableStateOf(false) }

    if (showUnlockDialog) {
        AlertDialog(
            onDismissRequest = { showUnlockDialog = false },
            title = { Text("Unlock Zone?") },
            text = { Text("Well, then. So much for being 'Locked In' on ${state.selectedStation.name}.") },
            confirmButton = {
                TextButton(onClick = { onUnlockZone(); showUnlockDialog = false }) {
                    Text("Unlock")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnlockDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onToggleHidingZone,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (state.hidingZoneVisible) "Hide Zone" else "Show Zone")
                }
                if (state.isSelectedStationLocked) {
                    FilledTonalButton(
                        onClick = { showUnlockDialog = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Unlock")
                    }
                } else {
                    FilledTonalButton(
                        onClick = onLockZone,
                        enabled = state.hidingZoneVisible && state.lockedZone == null,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Lock It In")
                    }
                }
            }
        }
    }
}
