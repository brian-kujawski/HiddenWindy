package com.hiddenwindy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hiddenwindy.game.Direction
import com.hiddenwindy.game.GameState
import com.hiddenwindy.game.GameViewModel
import com.hiddenwindy.map.MapViewContainer

/** Main screen: full-screen map with an info card on top and the game control panel at the bottom. */
@Composable
fun MapScreen(
    viewModel: GameViewModel,
    onUseMyLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    Box(modifier = modifier.fillMaxSize()) {
        MapViewContainer(viewModel = viewModel, modifier = Modifier.fillMaxSize())

        InfoCard(
            state = state,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(12.dp),
        )

        ControlPanel(
            state = state,
            onToggleHidingZone = viewModel::toggleHidingZone,
            onEliminate = viewModel::addDirectionalEliminator,
            onUndo = viewModel::undoLastEliminator,
            onReset = viewModel::resetEliminators,
            onUseMyLocation = onUseMyLocation,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(12.dp),
        )
    }
}

@Composable
private fun InfoCard(state: GameState, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            val station = state.selectedStation
            if (station == null) {
                Text("Tap a station to select it.", fontWeight = FontWeight.Medium)
                Text("Long-press the map to set your reference point.")
            } else {
                Text(station.name, fontWeight = FontWeight.Bold)
                Text("Lines: ${station.lines.joinToString(", ").ifEmpty { "—" }}")
                Text("ADA accessible: ${if (station.ada) "Yes" else "No"}")
            }
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
    onEliminate: (Direction) -> Unit,
    onUndo: () -> Unit,
    onReset: () -> Unit,
    onUseMyLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasOrigin = state.activeOrigin != null
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
                    enabled = state.selectedStation != null,
                    modifier = Modifier.width(180.dp),
                ) {
                    Text(if (state.hidingZoneVisible) "Hide zone" else "Show hiding zone")
                }
                OutlinedButton(onClick = onUseMyLocation) {
                    Text("My location")
                }
            }

            Text("Eliminate the side that is OUT of play:", fontWeight = FontWeight.Medium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Direction.entries.forEach { direction ->
                    FilledTonalButton(
                        onClick = { onEliminate(direction) },
                        enabled = hasOrigin,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(direction.label.take(1))
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onUndo,
                    enabled = state.eliminators.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Undo")
                }
                OutlinedButton(
                    onClick = onReset,
                    enabled = state.eliminators.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Reset")
                }
            }
        }
    }
}
