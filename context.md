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
| Confirm panels | `RadarConfirmPanel.kt`, `ThermometerConfirmPanel.kt`, `MatchingConfirmPanel.kt` |
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
    && state.thermometerActive == null && state.matchingConfirmed == null)
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

---

## Asset Inventory

| Asset | Size | Description |
|---|---|---|
| `stations.geojson` | — | 385 CTA/Metra stations (Point, name/lines/ada/mapId) |
| `transit_lines.geojson` | — | Rail line LineStrings |
| `line_regions.geojson` | — | Voronoi by nearest transit line |
| `transit_airport_regions.geojson` | 0.5 KB | 2 features: O'Hare, Midway |
| `natural_park_regions.geojson` | 644 KB | 1787 Cook County park Voronoi regions |
| `station_name_length_regions.geojson` | 84 KB | 20 features (alphanumeric lengths 4–36) |
| `csv/transit_airport.csv` | — | 2 airports |
| `csv/natural_park.csv` | — | 1792 parks |
| `csv/station_name_length.csv` | — | 385 stations with name=length |

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
# Copy CSVs to app assets:
Copy-Item csv/natural_park.csv app/src/main/assets/csv/natural_park.csv
Copy-Item csv/station_name_length.csv app/src/main/assets/csv/station_name_length.csv
```

Dependencies: `pip install shapely scipy`

---

## Adding a New Matching Question (checklist)

1. Prepare `csv/<dataset>.csv` (name, latitude, longitude) — write a tool in `tools/` if sourced from KML/GeoJSON
2. Run `python tools/build_point_regions.py csv/<dataset>.csv`
3. Copy CSV to `app/src/main/assets/csv/<dataset>.csv`
4. Add `<x>Regions` field + `init` load block + `nearest<X>ToMe()` method to `GameViewModel.kt`
5. Add `"<dataset>" -> <x>Regions[name]` to `resolveMatching()` in `GameViewModel.kt`
6. Add `"<dataset>" -> viewModel.nearest<X>ToMe()` to `nearestFeatureName` lambda in `MapScreen.kt`
7. Add `ENTRY(MatchCategory.X, "Label", dataset = "<dataset>")` to `MatchQuestion` in `QuestionsModal.kt`

---

## Key State Classes

```kotlin
data class MatchingConfirmed(
    val dataset: String,
    val featureLabel: String,
    val seekerNearestName: String,
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
    val askedQuestions: List<AskedQuestion> = emptyList(),
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
├── RADAR      → RadarSubmenu (casting info + rules + radius buttons)
├── THERMOMETER → ThermometerSubmenu (casting info + rules + distance buttons)
└── MEASURING  → (coming soon)
```

## Pending / Coming Soon Questions
- Matching: Municipality, Street or Path, Amusement Park, Zoo, Aquarium, Golf Course, Museum, Movie Theater, Hospital, Library, Foreign Consulate
- Measuring: all questions (no datasets built yet)
- Tentacles, Photos: not started
