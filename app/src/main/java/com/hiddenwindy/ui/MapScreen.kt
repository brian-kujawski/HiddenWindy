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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay
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
    var debugEnabled by remember { mutableStateOf(false) }
    var showDebugToast by remember { mutableStateOf(false) }
    var showQuestionsModal by remember { mutableStateOf(false) }
    var questionsStartOnRadar by remember { mutableStateOf(false) }
    var showHistoryModal by remember { mutableStateOf(false) }

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
                debugEnabled = debugEnabled,
                onDebugUnlocked = {
                    debugEnabled = true
                    showDebugToast = true
                },
                isDebugLocationActive = state.debugLocation != null,
                onToggleDebugLocation = viewModel::toggleBeanLocation,
                isSpoofLocationActive = state.spoofLocationActive,
                onToggleSpoofLocation = viewModel::toggleSpoofLocation,
                onOpenQuestionsMenu = {
                    questionsStartOnRadar = false
                    showQuestionsModal = true
                },
                onOpenHistory = { showHistoryModal = true },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp),
            )
        }

        if (showQuestionsModal) {
            QuestionsModal(
                startOnRadar = questionsStartOnRadar,
                onRadarRadiusSelected = { label, radius ->
                    viewModel.setRadarPending(label, radius)
                    showQuestionsModal = false
                    questionsStartOnRadar = false
                },
                nearestFeatureName = { dataset ->
                    when (dataset) {
                        "line_regions" -> viewModel.nearestLineToMe()
                        "transit_airport" -> viewModel.nearestAirportToMe()
                        else -> null
                    }
                },
                matchingEnabled = state.effectiveLocation != null,
                askedCategories = state.askedQuestions.map { it.category }.toSet(),
                askedRadarLabels = state.askedQuestions
                    .filter { it.category == com.hiddenwindy.game.QuestionCategory.RADAR }
                    .map { it.detail.removeSuffix(" mi") }
                    .toSet(),
                askedThermometerLabels = state.askedQuestions
                    .filter { it.category == com.hiddenwindy.game.QuestionCategory.THERMOMETER }
                    .map { it.detail.substringBefore(", ") }
                    .toSet(),
                askedMatchingDetails = state.askedQuestions
                    .filter { it.category == com.hiddenwindy.game.QuestionCategory.MATCHING }
                    .map { it.detail }
                    .toSet(),
                onMatchingAnswer = { dataset, same ->
                    when (dataset) {
                        "line_regions" -> viewModel.askMatching(same)
                        "transit_airport" -> viewModel.askAirportMatching(same)
                    }
                    showQuestionsModal = false
                    questionsStartOnRadar = false
                },
                onThermometerSelected = { label, distance ->
                    viewModel.setThermometerPending(label, distance)
                    showQuestionsModal = false
                    questionsStartOnRadar = false
                },
                onDismiss = {
                    showQuestionsModal = false
                    questionsStartOnRadar = false
                },
            )
        }

        if (showHistoryModal) {
            HistoryModal(
                questions = state.askedQuestions,
                onDismiss = { showHistoryModal = false },
            )
        }

        state.radarPending?.let { pending ->
            RadarPanel(
                label = pending.label,
                confirmEnabled = state.effectiveLocation != null,
                onConfirm = { viewModel.confirmRadar() },
                onAdjust = { /* TODO */ },
                onCancel = {
                    viewModel.clearRadarPending()
                    questionsStartOnRadar = true
                    showQuestionsModal = true
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(12.dp),
            )
        }

        state.radarConfirmed?.let { confirmed ->
            RadarConfirmPanel(
                confirmed = confirmed,
                onStartTimer = viewModel::startRadarTimer,
                onResolve = viewModel::resolveRadar,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(12.dp),
            )
        }

        state.thermometerPending?.let { pending ->
            ThermometerStartPanel(
                label = pending.label,
                startEnabled = state.effectiveLocation != null,
                onStart = { viewModel.startThermometer() },
                onAdjust = { /* TODO */ },
                onCancel = {
                    viewModel.clearThermometerPending()
                    showQuestionsModal = true
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(12.dp),
            )
        }

        state.thermometerActive?.let { active ->
            if (active.finish == null) {
                ThermometerTravelPanel(
                    label = active.label,
                    traveledMiles = state.thermometerTraveledMiles ?: 0.0,
                    thresholdReached = state.thermometerThresholdReached,
                    onFinish = { viewModel.finishThermometer() },
                    onCancel = { viewModel.cancelThermometer() },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(12.dp),
                )
            } else {
                ThermometerResolvePanel(
                    onResolve = { hotter -> viewModel.resolveThermometer(hotter) },
                    onCancel = { viewModel.cancelThermometer() },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(12.dp),
                )
            }
        }

        if (showDebugToast) {
            LaunchedEffect(Unit) {
                delay(2_500)
                showDebugToast = false
            }
            Surface(
                shape = androidx.compose.material3.MaterialTheme.shapes.medium,
                color = Color(0xFF212121),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
            ) {
                Text(
                    text = "Debug Enabled!",
                    color = Color.White,
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
            }
        }

        if (state.radarConfirmed == null && state.thermometerPending == null && state.thermometerActive == null) {
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
}

@Composable
private fun HamburgerMenu(
    onNewGame: () -> Unit,
    debugEnabled: Boolean,
    onDebugUnlocked: () -> Unit,
    isDebugLocationActive: Boolean,
    onToggleDebugLocation: () -> Unit,
    isSpoofLocationActive: Boolean,
    onToggleSpoofLocation: () -> Unit,
    onOpenQuestionsMenu: () -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showNewGameDialog by remember { mutableStateOf(false) }
    var showDebugModal by remember { mutableStateOf(false) }
    var tapCount by remember { mutableIntStateOf(0) }
    var lastTapTime by remember { mutableStateOf(0L) }

    if (showDebugModal) {
        DebugModal(
            isDebugLocationActive = isDebugLocationActive,
            onToggleDebugLocation = onToggleDebugLocation,
            isSpoofLocationActive = isSpoofLocationActive,
            onToggleSpoofLocation = onToggleSpoofLocation,
            onDismiss = { showDebugModal = false },
        )
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
            onClick = {
                val now = System.currentTimeMillis()
                if (now - lastTapTime > 1_500L) tapCount = 0
                tapCount++
                lastTapTime = now
                if (!debugEnabled && tapCount >= 10) {
                    tapCount = 0
                    onDebugUnlocked()
                } else {
                    menuExpanded = true
                }
            },
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
                onClick = { menuExpanded = false; onOpenQuestionsMenu() },
            )
            DropdownMenuItem(
                text = { Text("Rules") },
                onClick = { menuExpanded = false },
            )
            DropdownMenuItem(
                text = { Text("History") },
                onClick = { menuExpanded = false; onOpenHistory() },
            )
            DropdownMenuItem(
                text = { Text("New Game") },
                onClick = { menuExpanded = false; showNewGameDialog = true },
            )
            if (debugEnabled) {
                DropdownMenuItem(
                    text = { Text("Debug") },
                    onClick = { menuExpanded = false; showDebugModal = true },
                )
            }
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

@Composable
private fun RadarPanel(
    label: String,
    confirmEnabled: Boolean,
    onConfirm: () -> Unit,
    onAdjust: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Radar: $label mile radius",
                fontWeight = FontWeight.Medium,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onConfirm,
                    enabled = confirmEnabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Confirm")
                }
                OutlinedButton(
                    onClick = onAdjust,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Adjust")
                }
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun ThermometerStartPanel(
    label: String,
    startEnabled: Boolean,
    onStart: () -> Unit,
    onAdjust: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Thermometer: $label",
                fontWeight = FontWeight.Medium,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onStart,
                    enabled = startEnabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Start")
                }
                OutlinedButton(
                    onClick = onAdjust,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Adjust")
                }
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun ThermometerTravelPanel(
    label: String,
    traveledMiles: Double,
    thresholdReached: Boolean,
    onFinish: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Thermometer: $label",
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "%.1f miles / %s elapsed".format(traveledMiles, label),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onFinish,
                    enabled = thresholdReached,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Finish")
                }
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun ThermometerResolvePanel(
    onResolve: (hotter: Boolean) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Is the hider hotter or colder than when you started?",
                fontWeight = FontWeight.Medium,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { onResolve(true) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Hotter")
                }
                Button(
                    onClick = { onResolve(false) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Colder")
                }
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}
