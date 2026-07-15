package com.hiddenwindy.game

import com.hiddenwindy.data.NamedPoint
import com.hiddenwindy.data.Station
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import org.maplibre.turf.TurfConstants
import org.maplibre.turf.TurfMeasurement

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

/** The distance the player picked for a Thermometer reading (label = button text, e.g. "3 Miles"). */
data class ThermometerPending(val label: String, val distanceMiles: Double)

/**
 * An in-progress Thermometer reading. [start] is captured when the player taps Start; [finish] is
 * captured when they tap Finish. While [finish] is null we are still measuring travel; once set we
 * are awaiting the hotter/colder answer.
 */
data class ThermometerActive(
    val label: String,
    val distanceMiles: Double,
    val start: Point,
    val finish: Point? = null,
    /** Straight-line miles from start to finish, set when the journey ends. */
    val finishedMiles: Double? = null,
    val timerStarted: Boolean = false,
    val remainingSeconds: Int = 5 * 60,
    /** Monotonic ID so the copy panel knows when a brand-new finish has occurred. */
    val finishedAt: Long = 0L,
)

/** A Matching question that has been cast and is awaiting the hider's reply. */
data class MatchingConfirmed(
    val dataset: String,
    val featureLabel: String,
    val seekerNearestName: String,
    /** For Station's Name Length only: the actual station name (seekerNearestName holds the length). */
    val seekerNearestStationName: String? = null,
    val timerStarted: Boolean = false,
    val remainingSeconds: Int = 5 * 60,
    /** Monotonic ID so the copy panel knows when a brand-new confirmation has started. */
    val confirmedAt: Long = System.currentTimeMillis(),
)

/** A Measuring POI question that has been cast and is awaiting the hider's reply. */
data class MeasuringPOIConfirmed(
    val featureLabel: String,
    val nearestName: String,
    val distanceMiles: Double,
    /** Locations of every POI of this type; used to compute circle eliminators on resolve. */
    val poiLocations: List<Point>,
    /** Dataset key; when "natural_water" the resolve builds a polygon-buffer eliminator instead. */
    val dataset: String = "",
    val timerStarted: Boolean = false,
    val remainingSeconds: Int = 5 * 60,
    val confirmedAt: Long = System.currentTimeMillis(),
)

/** A Photos question that has been cast and is awaiting the hider's photo. */
data class PhotosConfirmed(
    val subject: String,
    val questionText: String,
    val timerStarted: Boolean = false,
    val remainingSeconds: Int = 10 * 60,
    val confirmedAt: Long = System.currentTimeMillis(),
)

/** Stable keys for each top-level question, matching the Questions menu items. */
object QuestionCategory {
    const val MATCHING = "MATCHING"
    const val MEASURING = "MEASURING"
    const val RADAR = "RADAR"
    const val THERMOMETER = "THERMOMETER"
    const val PHOTOS = "PHOTOS"
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

/** A dataset being rendered as point markers on the map (see Layers feature). */
data class POILayerState(
    val dataset: String,
    val displayName: String,
    val points: List<NamedPoint>,
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
    val spoofLocationActive: Boolean = false,
    val radarPending: RadarPending? = null,
    val radarConfirmed: RadarConfirmed? = null,
    val radarResults: List<RadarResult> = emptyList(),
    val thermometerPending: ThermometerPending? = null,
    val thermometerActive: ThermometerActive? = null,
    val matchingConfirmed: MatchingConfirmed? = null,
    val measuringPOIConfirmed: MeasuringPOIConfirmed? = null,
    val photosConfirmed: PhotosConfirmed? = null,
    val askedQuestions: List<AskedQuestion> = emptyList(),
    val activePOILayer: POILayerState? = null,
    /**
     * Precomputed, dissolved exclusion shading (rules + radar). Built off the main thread in the
     * ViewModel whenever [eliminators]/[radarResults] change, so the renderer never runs the heavy
     * JTS union / marching-squares contour on the UI thread (which ANRed for Body of Water).
     */
    val eliminatorOverlay: List<Polygon> = emptyList(),
    /** True while the draggable Adjust target is active (drift correction mode). */
    val adjustingLocation: Boolean = false,
    /** Manually placed question origin from the Adjust target, or null to use GPS. */
    val adjustedLocation: Point? = null,
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

    /** Origin used for the pending question: the Adjust override if set, else GPS. */
    val questionOrigin: Point?
        get() = adjustedLocation ?: effectiveLocation

    /** The draggable Adjust target position, or null when not adjusting. */
    val adjustTargetPoint: Point?
        get() = when {
            adjustingLocation -> adjustedLocation
            radarConfirmed != null -> adjustedLocation
            else -> null
        }

    /** Dashed connector from the GPS icon to the Adjust target, or null when not adjusting. */
    val adjustLine: LineString?
        get() {
            if (!adjustingLocation) return null
            val gps = effectiveLocation ?: return null
            val target = adjustedLocation ?: return null
            return LineString.fromLngLats(listOf(gps, target))
        }

    /** Straight-line miles travelled since the active Thermometer reading began, or null. */
    val thermometerTraveledMiles: Double?
        get() {
            val active = thermometerActive ?: return null
            if (active.finish != null) return null
            val loc = effectiveLocation ?: return null
            return TurfMeasurement.distance(active.start, loc, TurfConstants.UNIT_MILES)
        }

    /** True once the active Thermometer reading has travelled at least its target distance. */
    val thermometerThresholdReached: Boolean
        get() {
            val active = thermometerActive ?: return false
            val traveled = thermometerTraveledMiles ?: return false
            return traveled >= active.distanceMiles
        }

    /** Radar preview circle, or null when no radius is pending or no location is known. */
    val radarPreviewCircle: Polygon?
        get() {
            val pending = radarPending ?: return null
            val loc = questionOrigin ?: return null
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

    /** Every "out of play" polygon from all sources (rules + radar results); may overlap. */
    fun allOutRegions(): List<Polygon> = outRegions() + radarResultPolygons

}

/** True when two points refer to the same coordinate (used to de-duplicate locked zones). */
private fun Point.samePlaceAs(other: Point): Boolean =
    latitude() == other.latitude() && longitude() == other.longitude()
