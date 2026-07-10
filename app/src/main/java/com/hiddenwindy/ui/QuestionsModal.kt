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

/** Sub-categories shown after tapping Matching. Only [TRANSIT] has questions so far. */
private enum class MatchCategory(val displayName: String) {
    TRANSIT("Transit"),
    MUNICIPALITY("Municipality"),
    NATURAL("Natural"),
    POI("POI"),
    UTILITIES("Utilities"),
}

/** Individual Matching questions within a sub-category. */
private enum class MatchQuestion(val displayName: String) {
    TRAIN_LINE("Train Line"),
}

@Composable
fun QuestionsModal(
    onDismiss: () -> Unit,
    startOnRadar: Boolean = false,
    onRadarRadiusSelected: (label: String, radiusMiles: Double) -> Unit = { _, _ -> },
    nearestLineName: String? = null,
    matchingEnabled: Boolean = false,
    onMatchingAnswer: (same: Boolean) -> Unit = {},
    askedCategories: Set<String> = emptySet(),
    askedRadarLabels: Set<String> = emptySet(),
) {
    var screen by remember { mutableStateOf(if (startOnRadar) QuestionsScreen.RADAR else QuestionsScreen.HOME) }
    var matchCategory by remember { mutableStateOf<MatchCategory?>(null) }
    var matchQuestion by remember { mutableStateOf<MatchQuestion?>(null) }

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
                        IconButton(onClick = {
                            when {
                                screen == QuestionsScreen.MATCHING && matchQuestion != null -> matchQuestion = null
                                screen == QuestionsScreen.MATCHING && matchCategory != null -> matchCategory = null
                                else -> {
                                    matchCategory = null
                                    matchQuestion = null
                                    screen = QuestionsScreen.HOME
                                }
                            }
                        }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    }
                    Text(
                        text = when {
                            screen != QuestionsScreen.MATCHING -> screen.displayName
                            matchQuestion != null -> matchQuestion!!.displayName
                            matchCategory != null -> matchCategory!!.displayName
                            else -> screen.displayName
                        },
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(Modifier.height(16.dp))

                if (screen == QuestionsScreen.HOME) {
                      QuestionsGrid(
                          askedCategories = askedCategories,
                          askedRadarLabels = askedRadarLabels,
                          onSelect = { screen = it },
                      )
                } else if (screen == QuestionsScreen.MATCHING) {
                    MatchingNav(
                        category = matchCategory,
                        question = matchQuestion,
                        nearestLineName = nearestLineName,
                        matchingEnabled = matchingEnabled,
                        onSelectCategory = { matchCategory = it },
                        onSelectQuestion = { matchQuestion = it },
                        onMatchingAnswer = onMatchingAnswer,
                    )
                } else {
                    QuestionsSubmenu(
                        screen = screen,
                        onRadarRadiusSelected = onRadarRadiusSelected,
                        askedRadarLabels = askedRadarLabels,
                    )
                }
            }
        }
    }
}

@Composable
private fun QuestionsGrid(
    askedCategories: Set<String>,
    askedRadarLabels: Set<String>,
    onSelect: (QuestionsScreen) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TOP_LEVEL_SCREENS.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowItems.forEach { option ->
                    val enabled = when (option) {
                        // Radar stays available until every distance has been used.
                        QuestionsScreen.RADAR ->
                            SELECTABLE_RADAR_LABELS.any { it !in askedRadarLabels }
                        else -> option.name !in askedCategories
                    }
                    Button(
                        onClick = { onSelect(option) },
                        enabled = enabled,
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
private fun QuestionsSubmenu(
    screen: QuestionsScreen,
    onRadarRadiusSelected: (label: String, radiusMiles: Double) -> Unit,
    askedRadarLabels: Set<String>,
) {
    when (screen) {
        QuestionsScreen.RADAR -> RadarSubmenu(
            onRadiusSelected = onRadarRadiusSelected,
            askedRadarLabels = askedRadarLabels,
        )
        else -> ComingSoon(screen.displayName)
    }
}

/** Matching navigation: sub-category list -> question list -> the answer UI. */
@Composable
private fun MatchingNav(
    category: MatchCategory?,
    question: MatchQuestion?,
    nearestLineName: String?,
    matchingEnabled: Boolean,
    onSelectCategory: (MatchCategory) -> Unit,
    onSelectQuestion: (MatchQuestion) -> Unit,
    onMatchingAnswer: (same: Boolean) -> Unit,
) {
    when {
        question == MatchQuestion.TRAIN_LINE -> MatchingSubmenu(
            nearestLineName = nearestLineName,
            enabled = matchingEnabled,
            onAnswer = onMatchingAnswer,
        )
        category == MatchCategory.TRANSIT -> ButtonColumn(
            labels = MatchQuestion.entries.map { it.displayName },
            onSelect = { index -> onSelectQuestion(MatchQuestion.entries[index]) },
        )
        category != null -> ComingSoon(category.displayName)
        else -> ButtonColumn(
            labels = MatchCategory.entries.map { it.displayName },
            onSelect = { index -> onSelectCategory(MatchCategory.entries[index]) },
        )
    }
}

/** A vertical list of full-width buttons; reports the tapped item's index. */
@Composable
private fun ButtonColumn(labels: List<String>, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { index, label ->
            Button(
                onClick = { onSelect(index) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(label)
            }
        }
    }
}

@Composable
private fun ComingSoon(name: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "$name options coming soon.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun MatchingSubmenu(
    nearestLineName: String?,
    enabled: Boolean,
    onAnswer: (same: Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(
                    text = "Your nearest line",
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = nearestLineName ?: if (enabled) "None nearby" else "Locating…",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }

        HorizontalDivider()

        Text(
            text = "Is the hider's nearest line the same as yours?",
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { onAnswer(true) },
                enabled = enabled && nearestLineName != null,
                modifier = Modifier.weight(1f),
            ) {
                Text("Same")
            }
            Button(
                onClick = { onAnswer(false) },
                enabled = enabled && nearestLineName != null,
                modifier = Modifier.weight(1f),
            ) {
                Text("Different")
            }
        }
    }
}

private val RADAR_RADII = listOf("1/4", "1/2", "1", "3", "5", "10", "25", "???")

private val RADAR_RADIUS_VALUES: Map<String, Double?> = mapOf(
    "1/4" to 0.25,
    "1/2" to 0.5,
    "1" to 1.0,
    "3" to 3.0,
    "5" to 5.0,
    "10" to 10.0,
    "25" to 25.0,
    "???" to null,
)

/** Radar distances that can actually be cast (excludes placeholder "???"). */
private val SELECTABLE_RADAR_LABELS: Set<String> =
    RADAR_RADIUS_VALUES.filterValues { it != null }.keys

@Composable
private fun RadarSubmenu(
    onRadiusSelected: (label: String, radiusMiles: Double) -> Unit,
    askedRadarLabels: Set<String>,
) {
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
                        val radiusValue = RADAR_RADIUS_VALUES[label]
                        Button(
                            onClick = {
                                if (radiusValue != null) onRadiusSelected(label, radiusValue)
                            },
                            enabled = radiusValue != null && label !in askedRadarLabels,
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
