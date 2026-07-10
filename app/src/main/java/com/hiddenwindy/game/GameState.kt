package com.hiddenwindy.game

import com.hiddenwindy.data.Station
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

/** The radius the player has selected for a pending Radar cast (label = button text, e.g. "1/4"). */
data class RadarPending(val label: String, val radiusMiles: Double)

/** A Radar cast that has been confirmed — tracks the cast centre, timer, and radius. */
data class RadarConfirmed(
    val label: String,
    val radiusMiles: Double,
    val center: Point,
    val timerStarted: Boolean = false,
    val remainingSeconds: Int = 5 * 60,
    /** Monotonic ID so UI can tell when a brand-new confirmation has started. */
    val confirmedAt: Long = System.currentTimeMillis(),
)

/** The permanent result of a resolved Radar cast. */
data class RadarResult(
    val center: Point,
    val radiusMiles: Double,
    /** True = hider IS inside the circle; False = hider is NOT inside. */
    val hiderIsInside: Boolean,
)

/** Stable keys for each top-level question, matching the Questions menu items. */
object QuestionCategory {
    const val MATCHING = "MATCHING"
    const val RADAR = "RADAR"
}

/** A question asked this game, shown in the History list (e.g. "Radar, 5 mi"). */
data class AskedQuestion(
    /** Stable category key (see [QuestionCategory]); each may be asked once per game. */
    val category: String,
    /** Display name, e.g. "Matching" or "Radar". */
    val label: String,
    /** Specifics, e.g. "Train Line" or "5 mi". */
    val detail: String,
)

/** Immutable snapshot of the game, observed by the UI and the map renderer. */
data class GameState(
    val stations: List<Station> = emptyList(),
    val selectedStation: Station? = null,
    val hidingZoneVisible: Boolean = false,
    val referencePoint: Point? = null,
    val eliminators: List<Eliminator> = emptyList(),
    val lockedZone: Point? = null,
    val myLocation: Point? = null,
    val debugLocation: Point? = null,
    val radarPending: RadarPending? = null,
    val radarConfirmed: RadarConfirmed? = null,
    val radarResults: List<RadarResult> = emptyList(),
    val askedQuestions: List<AskedQuestion> = emptyList(),
) {
    /** Centers of every clear (in-play) circle: the locked zone plus the active station's zone. */
    val clearZoneCenters: List<Point>
        get() {
            val active = selectedStation?.takeIf { hidingZoneVisible }?.point()
            return if (active != null && (lockedZone == null || !lockedZone.samePlaceAs(active))) {
                listOfNotNull(lockedZone, active)
            } else {
                listOfNotNull(lockedZone)
            }
        }

    /** True when the currently selected station is the one whose zone is locked in. */
    val isSelectedStationLocked: Boolean
        get() = lockedZone != null && selectedStation?.point()?.samePlaceAs(lockedZone) == true

    /** The location shown on the map: debug override if active, otherwise real GPS. */
    val effectiveLocation: Point?
        get() = debugLocation ?: myLocation

    /** Radar preview circle, or null when no radius is pending or no location is known. */
    val radarPreviewCircle: Polygon?
        get() {
            val pending = radarPending ?: return null
            val loc = effectiveLocation ?: return null
            return ZoneGeometry.hidingZoneCircle(loc, pending.radiusMiles)
        }

    /**
     * Permanent radar result polygons rendered on the map:
     * - hiderIsInside=false → filled circle (excluded zone)
     * - hiderIsInside=true  → inverted mask (everything outside the circle)
     */
    val radarResultPolygons: List<Polygon>
        get() = radarResults.mapNotNull { result ->
            if (result.hiderIsInside) {
                ZoneGeometry.hidingZoneMask(listOf(result.center), result.radiusMiles)
            } else {
                ZoneGeometry.hidingZoneCircle(result.center, result.radiusMiles)
            }
        }

    /** Inverted mask that shades everything OUTSIDE the clear circles, or null when there are none. */
    val hidingZoneMask: Polygon?
        get() = ZoneGeometry.hidingZoneMask(clearZoneCenters)

    /** Outline circles drawn around each clear zone. */
    val hidingZoneCircles: List<Polygon>
        get() = clearZoneCenters.map { ZoneGeometry.hidingZoneCircle(it) }

    /** Origin used for new directional cuts: the reference point, else the selected station. */
    val activeOrigin: Point?
        get() = referencePoint ?: selectedStation?.point()

    fun gameContext(): GameContext = GameContext(
        referencePoint = referencePoint,
        selectedStation = selectedStation,
        stations = stations,
    )

    /** All currently eliminated ("out of play") polygons across every active rule. */
    fun outRegions(): List<Polygon> {
        val context = gameContext()
        return eliminators.flatMap { it.outRegions(context) }
    }
}

/** True when two points refer to the same coordinate (used to de-duplicate locked zones). */
private fun Point.samePlaceAs(other: Point): Boolean =
    latitude() == other.latitude() && longitude() == other.longitude()
