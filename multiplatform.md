# HiddenWindy — Multiplatform (iOS) Porting Notes

_Status snapshot: 2026-07-15. Tabled for later; this doc captures the assessment so we can resume without re-deriving it._

## Goal

Enable HiddenWindy (currently Android-only, Kotlin + Jetpack Compose) to also run on iOS,
ideally sharing the bulk of the codebase rather than maintaining two apps.

## Current stack (baseline)

- Kotlin 2.2.10, AGP 8.5.2, Compose BOM 2024.10.01, Compose plugin 2.2.10.
- minSdk 24 / targetSdk 34, JVM target 17.
- 27 Kotlin source files across packages: `ui`, `game`, `map`, `data`, plus root
  (`MainActivity`, `HiddenWindyApp`).
- Key third-party deps:
  - `org.maplibre.gl:android-sdk:13.3.1` — map rendering (Android-only API surface).
  - `org.maplibre.gl:android-sdk-turf:6.0.1` — Turf geometry helpers.
  - `org.locationtech.jts:jts-core:1.19.0` — **Java-only** polygon union/buffer/topology repair.
  - `com.google.android.gms:play-services-location:21.3.0` — **Android-only** location.
  - `androidx.lifecycle:*` — ViewModel + StateFlow.

## Recommended course of action (primary COA)

**Kotlin Multiplatform + Compose Multiplatform (CMP).** We're already Kotlin + Compose, so CMP
lets us share both UI and logic. Proposed module layout:

```
shared/
  commonMain/   → game/, data/ interfaces, GameState, Eliminator, ZoneGeometry, all Compose UI
  androidMain/  → FusedLocation, JTS PolygonUnion, MapLibre-Android MapController, assets
  iosMain/      → CoreLocation, iOS geometry (GEOS?), MapLibre-iOS MapController, NSBundle assets
androidApp/     → MainActivity (thin)
iosApp/         → SwiftUI/UIKit entry point (thin)
```

### What ports easily (~60–70% of code)

- All Compose UI: `MapScreen`, `QuestionsModal`, `RulesModal`, `HistoryModal`, `LayersModal`,
  confirm panels. Material3 + `material-icons-extended` supported in CMP. Only `MapScreen` needs
  its permission code carved out.
- Pure game logic: `GameState`, `Eliminator`, `ZoneGeometry`, `Station` (depend only on
  `org.maplibre.geojson` data models + Turf math).
- Coroutines / StateFlow — already multiplatform.

### What needs `expect/actual` (medium effort)

| Concern | Android today | iOS equivalent |
|---|---|---|
| Location | `FusedLocationProviderClient` | `CLLocationManager` (Core Location) |
| Permissions | `ActivityResultContracts` + `ContextCompat` | `Info.plist` keys + Core Location auth callbacks |
| Asset loading | `context.assets.open(...)` (5 repos) | `NSBundle` / CMP `Res.readBytes()` |
| ViewModel | `AndroidViewModel(application)` | KMP `androidx.lifecycle.ViewModel` (drop `Application`/`Context`) |
| Clipboard | `LocalClipboardManager` | CMP clipboard (largely portable) |
| BuildConfig (`MAPTILER_API_KEY`) | Gradle `buildConfigField` | shared config (e.g. BuildKonfig) |

Key refactor: `GameViewModel` extends `AndroidViewModel` and holds a `Context` for BOTH location
and asset loading. Inject platform-abstracted `LocationProvider` + `AssetReader` instead.

## Alternative COA (lighter commitment)

Extract only `game`/`data` (the rules engine) into a KMP shared module and build a **native
SwiftUI + MapLibre-iOS** front end. Rewrites the UI but keeps the rules engine. Still hits the
same JTS geometry wall (below).

## Risk items

1. **JTS polygon union — highest risk.** `map/PolygonUnion.kt` uses `org.locationtech.jts`
   (`UnaryUnionOp.union`, `GeometryFixer.fix`, metric buffering). JTS is Java-only, no KMP build.
   It's central to eliminator overlay rendering and historically the source of crashes
   (`TopologyException`, fixed via `GeometryFixer` repair + `runCatching` fallback). Options:
   - Pure-Kotlin rewrite of union/buffer/topology-repair — very high effort, easy to reintroduce
     robustness bugs.
   - `expect/actual`: keep JTS on Android; wrap native GEOS (JTS is a port of GEOS) via cinterop
     on iOS. Most pragmatic; maintains two geometry backends.
   - **Prototype this in isolation FIRST** — it's the make-or-break item. If iOS union/buffer is
     reliable, the rest is mostly mechanical.
2. **MapLibre rendering rewrite.** `map/MapViewContainer.kt` + `map/MapController.kt` are heavily
   Android-coupled: `AndroidView` embedding of `MapView`, raw `MotionEvent` touch handling
   (the Adjust crosshair drag), Android lifecycle wiring, and Android-specific style/source/layer
   classes (`GeoJsonSource`, `FillLayer`, `LineLayer`, `CircleLayer`, `SymbolLayer`). MapLibre has
   an iOS SDK but a completely different (Obj-C/Swift) API — `MapController` must be rewritten per
   platform behind a common interface; embedding becomes `UIKitView` wrapping `MLNMapView`.
3. **Turf dependency.** `org.maplibre.gl:android-sdk-turf` is an Android-packaged artifact. Lighter
   than JTS and has pure-Kotlin equivalents, but still needs swapping.
4. **MapLibre iOS SDK maturity** is generally behind the Android SDK — validate feature parity for
   the layers/expressions we use.

## Effort concentration (summary)

- Easy/shared: all UI + core game logic.
- Concentrated effort in three spots: MapLibre rendering (rewrite per platform behind an
  interface), JTS geometry (no KMP path), location/permissions (routine `expect/actual`).
- Biggest single unknown: JTS. De-risk by prototyping the iOS geometry backend before committing.

## Suggested next steps when we resume

1. Prototype iOS polygon union/buffer (GEOS cinterop vs pure-Kotlin) in a throwaway module.
2. Sketch `expect/actual` interfaces: `LocationProvider`, `AssetReader`, `GeometryOps`.
3. Spike a CMP `UIKitView` + MapLibre-iOS map with one GeoJSON source/layer to prove the render path.
4. Refactor `GameViewModel` off `AndroidViewModel`/`Context` (inject the abstractions above).
