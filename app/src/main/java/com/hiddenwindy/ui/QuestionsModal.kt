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

/** Sub-categories shown after tapping Matching. */
private enum class MatchCategory(val displayName: String) {
    TRANSIT("Transit"),
    DIVISIONS("Divisions"),
    NATURAL("Natural"),
    POI("POI"),
    UTILITIES("Utilities"),
}

/**
 * A single Matching question.
 *
 * Each question is answered by shading the play area by "which named feature is nearest", so every
 * question needs its own precomputed dataset. [dataset] names the `<dataset>_regions.geojson` asset
 * produced by the offline tools (see tools/build_line_regions.py); a null dataset means the data has
 * not been built yet, so the question renders a "coming soon" placeholder. Add a new question by
 * adding an entry here — no other UI changes are needed to list it.
 */
private enum class MatchQuestion(
    val category: MatchCategory,
    val displayName: String,
    val dataset: String? = null,
) {
    // Transit
    AIRPORT(MatchCategory.TRANSIT, "Airport", dataset = "transit_airport"),
    TRANSIT_LINE(MatchCategory.TRANSIT, "Transit Line", dataset = "line_regions"),
    STATION_NAME_LENGTH(MatchCategory.TRANSIT, "Station's Name Length", dataset = "station_name_length"),
    STREET_OR_PATH(MatchCategory.TRANSIT, "Street or Path"),

    // Divisions
    MUNICIPALITY(MatchCategory.DIVISIONS, "Municipality"),

    // Natural
    PARK(MatchCategory.NATURAL, "Park", dataset = "natural_park"),

    // POI
    AMUSEMENT_PARK(MatchCategory.POI, "Amusement Park", dataset = "poi-amusementparks"),
    ZOO(MatchCategory.POI, "Zoo", dataset = "poi-zoo"),
    AQUARIUM(MatchCategory.POI, "Aquarium", dataset = "poi-aquarium"),
    GOLF_COURSE(MatchCategory.POI, "Golf Course"),
    MUSEUM(MatchCategory.POI, "Museum"),
    MOVIE_THEATER(MatchCategory.POI, "Movie Theater"),

    // Utilities
    HOSPITAL(MatchCategory.UTILITIES, "Hospital"),
    LIBRARY(MatchCategory.UTILITIES, "Library"),
    FOREIGN_CONSULATE(MatchCategory.UTILITIES, "Foreign Consulate"),
}

/** Sub-categories shown after tapping Measuring. */
private enum class MeasureCategory(val displayName: String) {
    TRANSIT("Transit"),
    BORDERS("Borders"),
    NATURAL("Natural"),
    POI("POI"),
    UTILITIES("Utilities"),
}

/**
 * A single Measuring question. Like [MatchQuestion], each is backed by a precomputed dataset
 * (see tools/build_line_regions.py); a null [dataset] renders a "coming soon" placeholder. Add a
 * question by adding an entry here — no other UI changes are needed to list it.
 */
private enum class MeasureQuestion(
    val category: MeasureCategory,
    val displayName: String,
    val dataset: String? = null,
) {
    // Transit
    M_AIRPORT(MeasureCategory.TRANSIT, "Airport"),
    HIGH_SPEED_TRAIN_LINE(MeasureCategory.TRANSIT, "High Speed Train Line"),
    RAIL_STATION(MeasureCategory.TRANSIT, "Rail Station"),

    // Borders
    INTERNATIONAL(MeasureCategory.BORDERS, "International"),
    STATE(MeasureCategory.BORDERS, "State"),
    COUNTY(MeasureCategory.BORDERS, "County"),

    // Natural
    BODY_OF_WATER(MeasureCategory.NATURAL, "Body of Water"),
    COASTLINE(MeasureCategory.NATURAL, "Coastline"),
    MOUNTAIN(MeasureCategory.NATURAL, "Mountain"),
    M_PARK(MeasureCategory.NATURAL, "Park"),

    // POI
    M_AMUSEMENT_PARK(MeasureCategory.POI, "Amusement Park"),
    M_ZOO(MeasureCategory.POI, "Zoo"),
    M_AQUARIUM(MeasureCategory.POI, "Aquarium"),
    M_GOLF_COURSE(MeasureCategory.POI, "Golf Course"),
    M_MUSEUM(MeasureCategory.POI, "Museum"),
    M_MOVIE_THEATER(MeasureCategory.POI, "Movie Theater"),

    // Utilities
    M_HOSPITAL(MeasureCategory.UTILITIES, "Hospital"),
    M_LIBRARY(MeasureCategory.UTILITIES, "Library"),
    M_FOREIGN_CONSULATE(MeasureCategory.UTILITIES, "Foreign Consulate"),
}

@Composable
fun QuestionsModal(
    onDismiss: () -> Unit,
    startOnRadar: Boolean = false,
    onRadarRadiusSelected: (label: String, radiusMiles: Double) -> Unit = { _, _ -> },
    nearestFeatureName: (dataset: String) -> String? = { null },
    matchingEnabled: Boolean = false,
    onMatchingConfirm: (dataset: String, featureLabel: String, seekerNearestName: String) -> Unit = { _, _, _ -> },
    onThermometerSelected: (label: String, distanceMiles: Double) -> Unit = { _, _ -> },
    askedCategories: Set<String> = emptySet(),
    askedRadarLabels: Set<String> = emptySet(),
    askedThermometerLabels: Set<String> = emptySet(),
    askedMatchingDetails: Set<String> = emptySet(),
) {
    var screen by remember { mutableStateOf(if (startOnRadar) QuestionsScreen.RADAR else QuestionsScreen.HOME) }
    var matchCategory by remember { mutableStateOf<MatchCategory?>(null) }
    var matchQuestion by remember { mutableStateOf<MatchQuestion?>(null) }
    var measureCategory by remember { mutableStateOf<MeasureCategory?>(null) }
    var measureQuestion by remember { mutableStateOf<MeasureQuestion?>(null) }

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
                                screen == QuestionsScreen.MEASURING && measureQuestion != null -> measureQuestion = null
                                screen == QuestionsScreen.MEASURING && measureCategory != null -> measureCategory = null
                                else -> {
                                    matchCategory = null
                                    matchQuestion = null
                                    measureCategory = null
                                    measureQuestion = null
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
                            screen == QuestionsScreen.MATCHING ->
                                matchQuestion?.displayName ?: matchCategory?.displayName ?: screen.displayName
                            screen == QuestionsScreen.MEASURING ->
                                measureQuestion?.displayName ?: measureCategory?.displayName ?: screen.displayName
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
                          askedThermometerLabels = askedThermometerLabels,
                          askedMatchingDetails = askedMatchingDetails,
                          onSelect = { screen = it },
                      )
                } else if (screen == QuestionsScreen.MATCHING) {
                    MatchingNav(
                        category = matchCategory,
                        question = matchQuestion,
                        nearestFeatureName = nearestFeatureName,
                        matchingEnabled = matchingEnabled,
                        askedMatchingDetails = askedMatchingDetails,
                        onSelectCategory = { matchCategory = it },
                        onSelectQuestion = { matchQuestion = it },
                        onMatchingConfirm = onMatchingConfirm,
                    )
                } else if (screen == QuestionsScreen.MEASURING) {
                    MeasuringNav(
                        category = measureCategory,
                        question = measureQuestion,
                        onSelectCategory = { measureCategory = it },
                        onSelectQuestion = { measureQuestion = it },
                    )
                } else {
                    QuestionsSubmenu(
                        screen = screen,
                        onRadarRadiusSelected = onRadarRadiusSelected,
                        onThermometerSelected = onThermometerSelected,
                        askedRadarLabels = askedRadarLabels,
                        askedThermometerLabels = askedThermometerLabels,
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
    askedThermometerLabels: Set<String>,
    askedMatchingDetails: Set<String>,
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
                        QuestionsScreen.RADAR ->
                            SELECTABLE_RADAR_LABELS.any { it !in askedRadarLabels }
                        QuestionsScreen.THERMOMETER ->
                            THERMOMETER_OPTIONS.any { (label, _) -> label !in askedThermometerLabels }
                        QuestionsScreen.MATCHING ->
                            MatchQuestion.entries.any { q -> q.dataset != null && q.displayName !in askedMatchingDetails }
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
    onThermometerSelected: (label: String, distanceMiles: Double) -> Unit,
    askedRadarLabels: Set<String>,
    askedThermometerLabels: Set<String>,
) {
    when (screen) {
        QuestionsScreen.RADAR -> RadarSubmenu(
            onRadiusSelected = onRadarRadiusSelected,
            askedRadarLabels = askedRadarLabels,
        )
        QuestionsScreen.THERMOMETER -> ThermometerSubmenu(
            onSelected = onThermometerSelected,
            askedThermometerLabels = askedThermometerLabels,
        )
        else -> ComingSoon(screen.displayName)
    }
}

/** Matching navigation: sub-category list -> question list -> the answer UI. */
@Composable
private fun MatchingNav(
    category: MatchCategory?,
    question: MatchQuestion?,
    nearestFeatureName: (dataset: String) -> String?,
    matchingEnabled: Boolean,
    askedMatchingDetails: Set<String>,
    onSelectCategory: (MatchCategory) -> Unit,
    onSelectQuestion: (MatchQuestion) -> Unit,
    onMatchingConfirm: (dataset: String, featureLabel: String, seekerNearestName: String) -> Unit,
) {
    when {
        // Any question with a built dataset uses the generic feature-matching submenu.
        question != null && question.dataset != null -> FeatureMatchingSubmenu(
            featureLabel = question.displayName,
            nearestFeatureName = nearestFeatureName(question.dataset),
            enabled = matchingEnabled,
            onConfirm = { nearestName -> onMatchingConfirm(question.dataset, question.displayName, nearestName) },
        )
        question != null -> ComingSoon(question.displayName)
        category != null -> {
            val questions = MatchQuestion.entries.filter { it.category == category }
            if (questions.isEmpty()) {
                ComingSoon(category.displayName)
            } else {
                val disabledQuestions = questions.mapIndexedNotNull { index, q ->
                    if (q.dataset != null && q.displayName in askedMatchingDetails) index else null
                }.toSet()
                ButtonColumn(
                    labels = questions.map { it.displayName },
                    disabledIndices = disabledQuestions,
                    onSelect = { index -> onSelectQuestion(questions[index]) },
                )
            }
        }
        else -> {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text(text = "COST: Draw 3, Pick 1", style = MaterialTheme.typography.labelLarge)
                        Text(text = "TIME: 5 Minutes", style = MaterialTheme.typography.labelLarge)
                    }
                }

                HorizontalDivider()

                Text(
                    text = "Is your nearest ___ the same as my nearest ___?",
                    style = MaterialTheme.typography.bodyMedium,
                )

                val disabledCategories = MatchCategory.entries.mapIndexedNotNull { index, cat ->
                    val answerable = MatchQuestion.entries.filter { it.category == cat && it.dataset != null }
                    if (answerable.isNotEmpty() && answerable.all { it.displayName in askedMatchingDetails }) index else null
                }.toSet()
                ButtonColumn(
                    labels = MatchCategory.entries.map { it.displayName },
                    disabledIndices = disabledCategories,
                    onSelect = { index -> onSelectCategory(MatchCategory.entries[index]) },
                )
            }
        }
    }
}

/** Measuring navigation: sub-category list -> question list -> the answer UI. */
@Composable
private fun MeasuringNav(
    category: MeasureCategory?,
    question: MeasureQuestion?,
    onSelectCategory: (MeasureCategory) -> Unit,
    onSelectQuestion: (MeasureQuestion) -> Unit,
) {
    when {
        // No Measuring question has a dataset yet, so every leaf is a placeholder for now.
        question != null -> ComingSoon(question.displayName)
        category != null -> {
            val questions = MeasureQuestion.entries.filter { it.category == category }
            if (questions.isEmpty()) {
                ComingSoon(category.displayName)
            } else {
                ButtonColumn(
                    labels = questions.map { it.displayName },
                    onSelect = { index -> onSelectQuestion(questions[index]) },
                )
            }
        }
        else -> ButtonColumn(
            labels = MeasureCategory.entries.map { it.displayName },
            onSelect = { index -> onSelectCategory(MeasureCategory.entries[index]) },
        )
    }
}

/** A vertical list of full-width buttons; reports the tapped item's index. */
@Composable
private fun ButtonColumn(
    labels: List<String>,
    onSelect: (Int) -> Unit,
    disabledIndices: Set<Int> = emptySet(),
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { index, label ->
            Button(
                onClick = { onSelect(index) },
                enabled = index !in disabledIndices,
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
private fun FeatureMatchingSubmenu(
    featureLabel: String,
    nearestFeatureName: String?,
    enabled: Boolean,
    onConfirm: (nearestName: String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Seeker's nearest feature
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(
                    text = "Your nearest $featureLabel",
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = nearestFeatureName ?: if (enabled) "None nearby" else "Locating…",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }

        Button(
            onClick = { nearestFeatureName?.let { onConfirm(it) } },
            enabled = enabled && nearestFeatureName != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Ask")
        }
    }
}

private val RADAR_RADII = listOf("1/4", "1/2", "1", "3", "5", "10", "25", "???")

/** Thermometer distance options and their mile values. */
private val THERMOMETER_OPTIONS = listOf("1/2 Mile" to 0.5, "3 Miles" to 3.0)

@Composable
private fun ThermometerSubmenu(
    onSelected: (label: String, distanceMiles: Double) -> Unit,
    askedThermometerLabels: Set<String> = emptySet(),
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

        Text(
            text = "I've just traveled at least _____. Am I hotter or colder?",
            style = MaterialTheme.typography.bodyMedium,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            THERMOMETER_OPTIONS.forEach { (label, distanceMiles) ->
                Button(
                    onClick = { onSelected(label, distanceMiles) },
                    enabled = label !in askedThermometerLabels,
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

        Text(
            text = "Are you within ___ of me?",
            style = MaterialTheme.typography.bodyMedium,
        )

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
