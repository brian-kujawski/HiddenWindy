package com.hiddenwindy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * A single point dataset that can be toggled on the map.
 *
 * To add a new dataset: append a new [POILayerOption] entry here. Also update
 * [com.hiddenwindy.game.GameViewModel.datasetPoints] to return its points, and add the
 * corresponding [MatchQuestion] / [MeasureQuestion] entry in QuestionsModal if applicable.
 */
data class POILayerOption(
    val dataset: String,
    val displayName: String,
    val category: String,
)

/** All point datasets available in the Layers picker, grouped by category. */
val ALL_POI_LAYER_OPTIONS: List<POILayerOption> = listOf(
    // Transit
    POILayerOption("transit_airport", "Airport", "Transit"),
    // Natural
    POILayerOption("natural_park", "Park", "Natural"),
    // POI
    POILayerOption("poi-amusementparks", "Amusement Park", "POI"),
    POILayerOption("poi-zoo", "Zoo", "POI"),
    POILayerOption("poi-aquarium", "Aquarium", "POI"),
    POILayerOption("poi-golfcourse", "Golf Course", "POI"),
    POILayerOption("poi-museum", "Museum", "POI"),
    POILayerOption("poi-movietheater", "Movie Theater", "POI"),
    // Utilities
    POILayerOption("utilities-hospital", "Hospital", "Utilities"),
    POILayerOption("utilities-library", "Library", "Utilities"),
    POILayerOption("utilities-consulates", "Foreign Consulate", "Utilities"),
)

/**
 * Dialog that lists all point datasets grouped by category.
 *
 * Tapping a dataset calls [onSelect]; the caller is responsible for toggling (clear if same,
 * replace if different). The currently active dataset is identified by [activeDataset] and
 * shown with a checkmark.
 */
@Composable
fun LayersModal(
    activeDataset: String?,
    onSelect: (POILayerOption) -> Unit,
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
                        text = "Map Layers",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Show point markers for a dataset. Tap again to hide.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))

                // Grouped list, scrollable in case the list grows
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val groups = ALL_POI_LAYER_OPTIONS.groupBy { it.category }
                    groups.entries.forEachIndexed { groupIndex, (category, options) ->
                        if (groupIndex > 0) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        }
                        Text(
                            text = category.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                        options.forEach { option ->
                            val isActive = option.dataset == activeDataset
                            OutlinedButton(
                                onClick = { onSelect(option) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = option.displayName,
                                    modifier = Modifier.weight(1f),
                                )
                                if (isActive) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Active",
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
