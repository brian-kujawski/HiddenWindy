# HiddenWindy — Session Context

## Project Overview
Android hide-and-seek game set in Chicago transit system. Players are a Seeker and a Hider. The Seeker asks questions to eliminate map regions. Built with Kotlin + Jetpack Compose, MapLibre, Turf.js. Workspace root: `c:\Users\Intre\HiddenWindy`.

---

## Architecture Summary

| Layer | Key Files |
|---|---|
| Game state | `app/src/main/java/com/hiddenwindy/game/GameState.kt` |
| Game logic | `app/src/main/java/com/hiddenwindy/game/GameViewModel.kt` |
| Eliminators | `app/src/main/java/com/hiddenwindy/game/Eliminator.kt` |
| Map rendering | `app/src/main/java/com/hiddenwindy/ui/MapScreen.kt`, `MapViewContainer.kt`, `MapController.kt` |
| Questions UI | `app/src/main/java/com/hiddenwindy/ui/QuestionsModal.kt` |
| Confirm panels | `RadarConfirmPanel.kt`, `ThermometerConfirmPanel.kt`, `MatchingConfirmPanel.kt`, `MeasuringPOIConfirmPanel.kt` |
| Layers picker | `app/src/main/java/com/hiddenwindy/ui/LayersModal.kt` |
| Data repos | `app/src/main/java/com/hiddenwindy/data/` |
| Offline tools | `tools/` |
| GeoJSON assets | `app/src/main/assets/` |

---

## Established Design Patterns

### Question Confirm Flow (copy + timer + resolve)
All question types use a bottom-overlay panel pattern:
1. **Copy card** — dismissable, shows text to send to hider, "Copy to Clipboard" button
2. **Timer card** — "Start Timer" → 5-minute countdown → "Resolve" button → AlertDialog with answer choices

Implemented for: **Radar** (`RadarConfirmPanel.kt`), **Thermometer** (`ThermometerConfirmPanel.kt`), **Matching** (`MatchingConfirmPanel.kt`).

### ControlPanel visibility guard
```kotlin
if (state.radarConfirmed == null && state.thermometerPending == null 
    && state.thermometerActive == null && state.matchingConfirmed == null
    && state.measuringPOIConfirmed == null)
```

### Per-question blocking
Each category tracks its own asked set (`askedRadarLabels`, `askedThermometerLabels`, `askedMatchingDetails`). Individual options are disabled; a category button is only disabled when ALL its answerable options are exhausted.

### Matching Questions pipeline (offline → runtime)
1. **Offline tool** generates `csv/<dataset>.csv` (columns: `name,latitude,longitude`)
2. `tools/build_point_regions.py csv/<dataset>.csv` → `app/src/main/assets/<dataset>_regions.geojson` (Voronoi polygons, one feature per unique `name`)
3. CSV also copied to `app/src/main/assets/csv/<dataset>.csv` for runtime nearest-feature lookup
4. `PointRegionsRepository` loads both; provides `loadRegions(dataset)`, `loadNamedPoints(dataset)`, `nearestName(loc, points)`
5. ViewModel holds `<x>Regions: Map<String, List<Polygon>>` + optional `<x>Points: List<NamedPoint>`
6. `resolveMatching(same)` dispatches by `confirmed.dataset` to the right regions map
7. `MapScreen.nearestFeatureName` lambda dispatches by dataset key to the right ViewModel method
8. `QuestionsModal.MatchQuestion` entry gets `dataset = "<key>"`

> **Bug fixed**: `build_point_regions.py` previously iterated over all input rows for output (duplicating features). Now correctly iterates over `cells_by_name.items()` — one feature per unique name.

---

## Implemented Features (this session)

### 1. Spoof Location (Debug)
- Long-press on map sets a spoofed location overriding real GPS
- "Spoof Location" / "No Spoof Location" toggle in Debug menu
- Makes "Make Me Bean" OBE when active
- Files: `GameState.kt`, `GameViewModel.kt`, `DebugModal.kt`, `MapScreen.kt`, `MapViewContainer.kt`

### 2. Per-question category blocking fix
- Previously asking one question blocked the entire category
- Now each individual option is tracked independently
- Files: `QuestionsModal.kt`, `MapScreen.kt`

### 3. Radar rules text
- Added "Are you within ___ of me?" to `RadarSubmenu`
- Casting info card (COST: Draw 2, Pick 1 / TIME: 5 Minutes) at top level

### 4. Thermometer redesign → copy+timer+resolve
- `ThermometerActive` gained: `finishedMiles`, `timerStarted`, `remainingSeconds`, `finishedAt`
- New `ThermometerConfirmPanel.kt` (copy + countdown + Hotter/Colder AlertDialog)
- Dialog text: "Am I hotter or colder than when I started?"
- Files: `GameState.kt`, `GameViewModel.kt`, `ThermometerConfirmPanel.kt`, `MapScreen.kt`

### 5. Matching redesign → copy+timer+resolve
- Replaced `askMatching(same)` / `askAirportMatching(same)` with confirm flow
- New `MatchingConfirmed` data class in `GameState.kt`
- New ViewModel methods: `confirmMatching()`, `startMatchingTimer()`, `resolveMatching(same)`, timer cancel in `resetGame()`
- New `MatchingConfirmPanel.kt`
- `FeatureMatchingSubmenu` now shows: casting info card (COST: Draw 3, Pick 1 / TIME: 5 Minutes) at **top-level Matching screen only** + rules text "Is your nearest ___ the same as my nearest ___?" + nearest-feature card + single **Ask** button
- Files: `GameState.kt`, `GameViewModel.kt`, `MatchingConfirmPanel.kt`, `QuestionsModal.kt`, `MapScreen.kt`

### 6. Airport Matching question
- Dataset: `transit_airport`
- Tool: `tools/build_point_regions.py csv/transit_airport.csv`
- 2 features: `Chicago O'Hare`, `Chicago Midway`
- ViewModel: `airportRegions`, `airportPoints`, `nearestAirportToMe()`
- `PointRegionsRepository` created for generic point-dataset loading
- Files: `PointRegionsRepository.kt`, `GameViewModel.kt`, `QuestionsModal.kt`, `MapScreen.kt`

### 7. Natural → Park Matching question
- Source KML: `kml/natural_parks.kml` (2170 placemarks, Cook County parks)
- KML uses `ExtendedData/Data[@name='cfname']` for park name (not `<name>` element); `Point` geometry
- Tool: `tools/kml_parks_to_csv.py` → `csv/natural_park.csv` (1792 parks, duplicates merged by centroid)
- Then: `build_point_regions.py csv/natural_park.csv` → 1787 features, 644 KB
- Dataset key: `natural_park`
- ViewModel: `parkRegions`, `parkPoints`, `nearestParkToMe()`
- Files: `tools/kml_parks_to_csv.py`, `GameViewModel.kt`, `QuestionsModal.kt`, `MapScreen.kt`

### 8. Transit → Station's Name Length Matching question
- Concept: seeker reports alphanumeric character count of their nearest station name; hider answers same/different
- Tool: `tools/build_station_name_length_csv.py` reads `stations.geojson`, writes `csv/station_name_length.csv` with `name` = alphanumeric char count (e.g. `"7"`)
- **Alphanumeric** (`.isalnum()`), NOT just alphabetic — "18th" = 4, not 2
- Then: `build_point_regions.py csv/station_name_length.csv` → 20 features (lengths 4–36)
- Dataset key: `station_name_length`
- ViewModel: `stationNameLengthRegions`, `nearestStationNameLengthToMe()` (uses existing `stations` list + haversine, no NamedPoints needed)
- Distribution peak at length 7 (66 stations: Addison, Chicago, etc.); singletons at 21, 23, 36
- Files: `tools/build_station_name_length_csv.py`, `GameViewModel.kt`, `QuestionsModal.kt`, `MapScreen.kt`

### 9. Measuring POI Questions (Zoo, Museum, Hospital, etc.)
Full pipeline for "Compared to me, are you closer to or farther from a ___?" questions.

**9 implemented datasets** (all in POI/Utilities categories):
- POI: `poi-amusementparks`, `poi-zoo`, `poi-aquarium`, `poi-golfcourse`, `poi-museum`, `poi-movietheater`
- Utilities: `utilities-hospital`, `utilities-library`, `utilities-consulates`

**Data flow:**
1. KML → CSV tool (e.g. `tools/kml_zoo_to_csv.py`) → `csv/poi-zoo.csv`
2. `build_point_regions.py csv/poi-zoo.csv` → `poi-zoo_regions.geojson` (for Matching)
3. CSV also at `app/src/main/assets/csv/poi-zoo.csv` (for runtime nearestName + distance)
4. ViewModel: `<x>Points: List<NamedPoint>`, loaded via `pointRegionsRepository.loadNamedPoints(dataset)`
5. `nearestPointDistanceMiles(dataset)` — used in QuestionsModal `FeatureMeasuringSubmenu`
6. `confirmMeasuringPOI(featureLabel, dataset)` — stores `MeasuringPOIConfirmed` in state
7. `startMeasuringPOITimer()` / `resolveMeasuringPOI(closer: Boolean)` — same copy+timer+resolve pattern

**New confirm panel:** `MeasuringPOIConfirmPanel.kt`
- Copy card text: "Compared to me, are you closer to or farther from a [featureLabel]? (I am X.X miles from [nearestName].)"
- Resolve dialog choices: **Closer** / **Farther**

**Eliminator:** `MeasuringPOIEliminator` in `Eliminator.kt`
- `closer = true` → `invertedMask(dissolve(circles))` — shade outside the union of POI circles at distance D
- `closer = false` → `circles` — shade each POI's circle directly
- **Critical:** `PolygonUnion.dissolve(circles)` BEFORE `invertedMask()` to avoid GeoJSON even-odd XOR bug where overlapping holes shade instead of cut

**Per-question tracking:** `askedMeasuringDetails: Set<String>` (tracks `featureLabel` strings), mirroring Matching's `askedMatchingDetails` pattern. Threaded through `QuestionsModal` → `QuestionsGrid` → `MeasuringNav`.

**Files created/modified:** `GameState.kt`, `Eliminator.kt`, `GameViewModel.kt`, `MeasuringPOIConfirmPanel.kt` (new), `QuestionsModal.kt`, `MapScreen.kt`

### 10. POI Layers visualization
Lets the seeker toggle point markers for any dataset on the map to visually decide which question to ask.

**State:** `POILayerState(dataset: String, displayName: String, points: List<NamedPoint>)` stored as `GameState.activePOILayer: POILayerState?`. Cleared on `resetGame()`.

**ViewModel methods:**
- `showPOILayer(dataset, displayName)` — toggle-off if same dataset, otherwise replace
- `clearPOILayer()` — explicit clear

**MapController:** New source `poi-markers-source` + amber `CircleLayer` (always visible) + `SymbolLayer` with `.withFilter(zoom >= 13)` so name labels only appear when zoomed in. Constants in `MapConstants.kt`: `POI_MARKERS_SOURCE`, `POI_MARKERS_CIRCLE_LAYER`, `POI_MARKERS_LABEL_LAYER`.

**LayersModal.kt** (new file): Contains `POILayerOption` data class + `ALL_POI_LAYER_OPTIONS` registry (11 datasets across Transit/Natural/POI/Utilities). This is the single source of truth for which datasets appear in the picker.

**MapScreen:** `FilledTonalButton` at bottom-right (above ControlPanel), only visible when no confirm panel is active. Label reads "Layers" or "Layers: Zoo" when active.

**Extensibility:** When adding a new point dataset, update THREE places:
1. `ALL_POI_LAYER_OPTIONS` in `LayersModal.kt`
2. `datasetPoints()` in `GameViewModel.kt`
3. `MatchQuestion` / `MeasureQuestion` enum in `QuestionsModal.kt`

---

## Asset Inventory

| Asset | Description |
|---|---|
| `stations.geojson` | 385 CTA/Metra stations (Point, name/lines/ada/mapId) |
| `transit_lines.geojson` | Rail line LineStrings |
| `line_regions.geojson` | Voronoi by nearest transit line |
| `transit_airport_regions.geojson` | 2 features: O'Hare, Midway |
| `natural_park_regions.geojson` | 1787 Cook County park Voronoi regions |
| `station_name_length_regions.geojson` | 20 features (alphanumeric name lengths 4–36) |
| `poi-amusementparks_regions.geojson` | Amusement park Voronoi regions |
| `poi-zoo_regions.geojson` | Zoo Voronoi regions |
| `poi-aquarium_regions.geojson` | Aquarium Voronoi regions |
| `poi-golfcourse_regions.geojson` | Golf course Voronoi regions |
| `poi-museum_regions.geojson` | Museum Voronoi regions |
| `poi-movietheater_regions.geojson` | Movie theater Voronoi regions |
| `utilities-hospital_regions.geojson` | Hospital Voronoi regions |
| `utilities-library_regions.geojson` | Library Voronoi regions |
| `utilities-consulates_regions.geojson` | Foreign consulate Voronoi regions |
| `csv/transit_airport.csv` | 2 airports |
| `csv/natural_park.csv` | 1792 Cook County parks |
| `csv/poi-amusementparks.csv` | Amusement parks |
| `csv/poi-zoo.csv` | Zoos |
| `csv/poi-aquarium.csv` | Aquariums |
| `csv/poi-golfcourse.csv` | Golf courses |
| `csv/poi-museum.csv` | Museums |
| `csv/poi-movietheater.csv` | Movie theaters |
| `csv/utilities-hospital.csv` | Hospitals |
| `csv/utilities-library.csv` | Libraries |
| `csv/utilities-consulates.csv` | Foreign consulates |

> Note: `station_name_length.csv` is NOT in assets — `nearestStationNameLengthToMe()` uses the in-memory station list directly.

---

## Offline Tool Reference

```powershell
# Regenerate all point-region GeoJSONs from scratch:
cd c:\Users\Intre\HiddenWindy
python tools/kml_parks_to_csv.py                          # kml → csv/natural_park.csv
python tools/build_station_name_length_csv.py             # stations.geojson → csv/station_name_length.csv
python tools/build_point_regions.py csv/transit_airport.csv
python tools/build_point_regions.py csv/natural_park.csv
python tools/build_point_regions.py csv/station_name_length.csv
python tools/build_point_regions.py csv/poi-amusementparks.csv
python tools/build_point_regions.py csv/poi-zoo.csv
python tools/build_point_regions.py csv/poi-aquarium.csv
python tools/build_point_regions.py csv/poi-golfcourse.csv
python tools/build_point_regions.py csv/poi-museum.csv
python tools/build_point_regions.py csv/poi-movietheater.csv
python tools/build_point_regions.py csv/utilities-hospital.csv
python tools/build_point_regions.py csv/utilities-library.csv
python tools/build_point_regions.py csv/utilities-consulates.csv
# Copy CSVs to app assets:
Copy-Item csv/natural_park.csv app/src/main/assets/csv/natural_park.csv
# (poi-* and utilities-* CSVs are already copied to assets when each dataset was set up)
```

Dependencies: `pip install shapely scipy`

---

## Adding a New Matching Question (checklist)

1. Prepare `csv/<dataset>.csv` (name, latitude, longitude) — write a tool in `tools/` if sourced from KML/GeoJSON
2. Run `python tools/build_point_regions.py csv/<dataset>.csv`
3. Copy CSV to `app/src/main/assets/csv/<dataset>.csv`
4. Add `<x>Regions: Map<String, List<Polygon>>` + `<x>Points: List<NamedPoint>` fields + `init` load block + `nearest<X>ToMe()` method to `GameViewModel.kt`
5. Add `"<dataset>" -> <x>Regions[name]` to `resolveMatching()` in `GameViewModel.kt`
6. Add `"<dataset>" -> viewModel.nearest<X>ToMe()` to `nearestFeatureName` lambda in `MapScreen.kt`
7. Add `ENTRY(MatchCategory.X, "Label", dataset = "<dataset>")` to `MatchQuestion` in `QuestionsModal.kt`
8. Add `POILayerOption("<dataset>", "Label", "Category")` to `ALL_POI_LAYER_OPTIONS` in `LayersModal.kt`
9. Add `"<dataset>" -> <x>Points` to `datasetPoints()` in `GameViewModel.kt`

## Adding a New Measuring POI Question (checklist)

Same as Matching steps 1–3, then:
4. Same as Matching step 4 (if not already done for Matching)
5. `datasetPoints()` in `GameViewModel.kt` already routes to `<x>Points` — add entry if new dataset
6. Add `nearestPointDistanceMiles` lambda in `MapScreen.kt` already delegates to `viewModel.nearestPointDistanceMiles(dataset)` — no change needed
7. Add `M_ENTRY(MeasureCategory.X, "Label", dataset = "<dataset>")` to `MeasureQuestion` in `QuestionsModal.kt`
8. Add `POILayerOption("<dataset>", "Label", "Category")` to `ALL_POI_LAYER_OPTIONS` in `LayersModal.kt`

## Adding a New Layers-only Dataset (no question, just visualization)

1. Ensure CSV + NamedPoints are loaded in `GameViewModel.kt`
2. Add `"<dataset>" -> <x>Points` to `datasetPoints()` in `GameViewModel.kt`
3. Add `POILayerOption("<dataset>", "Label", "Category")` to `ALL_POI_LAYER_OPTIONS` in `LayersModal.kt`

---

## Key State Classes

```kotlin
data class MeasuringPOIConfirmed(
    val featureLabel: String,
    val nearestName: String,
    val distanceMiles: Double,
    val poiLocations: List<Point>,   // all POIs of this type, for eliminator circles
    val timerStarted: Boolean = false,
    val remainingSeconds: Int = 5 * 60,
    val confirmedAt: Long = System.currentTimeMillis(),
)

data class POILayerState(
    val dataset: String,
    val displayName: String,
    val points: List<NamedPoint>,
)

data class MatchingConfirmed(
    val dataset: String,
    val featureLabel: String,
    val seekerNearestName: String,
    val seekerNearestStationName: String? = null,
    val timerStarted: Boolean = false,
    val remainingSeconds: Int = 5 * 60,
    val confirmedAt: Long = System.currentTimeMillis(),
)

data class ThermometerActive(
    val label: String,
    val distanceMiles: Double,
    val start: Point,
    val finish: Point? = null,
    val finishedMiles: Double? = null,
    val timerStarted: Boolean = false,
    val remainingSeconds: Int = 5 * 60,
    val finishedAt: Long = 0L,
)

data class GameState(
    // ... stations, location, eliminators, etc.
    val radarConfirmed: RadarConfirmed? = null,
    val thermometerPending: ThermometerPending? = null,
    val thermometerActive: ThermometerActive? = null,
    val matchingConfirmed: MatchingConfirmed? = null,
    val measuringPOIConfirmed: MeasuringPOIConfirmed? = null,
    val askedQuestions: List<AskedQuestion> = emptyList(),
    val activePOILayer: POILayerState? = null,
)
```

---

## Questions Modal Structure

```
HOME grid (6 buttons)
├── MATCHING
│   └── [top level: casting info + rules text]
│       └── Category list (Transit / Divisions / Natural / POI / Utilities)
│           └── Question list
│               └── FeatureMatchingSubmenu (nearest feature card + Ask button)
├── MEASURING
│   └── [top level: casting info + rules text]
│       └── Category list (Transit / Borders / Natural / POI / Utilities)
│           └── Question list
│               ├── FeatureMeasuringSubmenu (if dataset != null) → MeasuringPOIConfirmPanel
│               └── ComingSoon (if dataset == null)
├── RADAR      → RadarSubmenu (casting info + rules + radius buttons)
├── THERMOMETER → ThermometerSubmenu (casting info + rules + distance buttons)
└── TENTACLES, PHOTOS → ComingSoon
```

## File Index (new/modified files)

| File | Status | Purpose |
|---|---|---|
| `GameState.kt` | modified | Added `MeasuringPOIConfirmed`, `POILayerState`, `activePOILayer` field |
| `Eliminator.kt` | modified | Added `MeasuringPOIEliminator` (with PolygonUnion dissolve fix) |
| `GameViewModel.kt` | modified | Added measuring POI methods + `showPOILayer`/`clearPOILayer`/`datasetPoints` |
| `MapConstants.kt` | modified | Added `POI_MARKERS_SOURCE/CIRCLE_LAYER/LABEL_LAYER` |
| `MapController.kt` | modified | Added POI markers source + CircleLayer + SymbolLayer (minZoom 13) |
| `MeasuringPOIConfirmPanel.kt` | **new** | Copy + timer + Closer/Farther resolve panel |
| `LayersModal.kt` | **new** | Layers picker dialog + `ALL_POI_LAYER_OPTIONS` registry |
| `QuestionsModal.kt` | modified | `MeasuringNav`, `FeatureMeasuringSubmenu`, `askedMeasuringDetails` tracking |
| `MapScreen.kt` | modified | Layers button, `LayersModal` wiring, `measuringPOIConfirmed` panel |

## Pending / Coming Soon Questions
- Matching: Municipality (no dataset built)
- Matching/Measuring Transit: Airport ✅ (Matching + Measuring), Rail Station (no dataset)
- Matching/Measuring Natural: Park ✅ (Matching + Measuring), Body of Water, Coastline
- Matching/Measuring Borders: International, State, County (no datasets)
- Tentacles, Photos: not started

## Known Bugs Fixed
- **Even-odd GeoJSON hole overlap**: `MeasuringPOIEliminator` dissolves circles with `PolygonUnion.dissolve()` BEFORE calling `invertedMask()` — overlapping holes under even-odd fill rule would shade instead of cut without this
- **Lambda formatting**: `${"%.1f".format(x)}` not `${{...}}` (double braces create a lambda object)
- **Coarse per-category disabling**: Each question tracked individually via `askedMeasuringDetails` / `askedMatchingDetails`
