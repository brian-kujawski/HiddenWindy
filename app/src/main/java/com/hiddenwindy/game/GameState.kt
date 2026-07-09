package com.hiddenwindy.game

import com.hiddenwindy.data.Station
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

/** Immutable snapshot of the game, observed by the UI and the map renderer. */
data class GameState(
    val stations: List<Station> = emptyList(),
    val selectedStation: Station? = null,
    val hidingZoneVisible: Boolean = false,
    val referencePoint: Point? = null,
    val eliminators: List<Eliminator> = emptyList(),
    val lockedZones: List<Point> = emptyList(),
) {
    /** Centers of every clear (in-play) circle: the locked zones plus the active station's zone. */
    val clearZoneCenters: List<Point>
        get() {
            val active = selectedStation?.takeIf { hidingZoneVisible }?.point()
            return if (active != null && lockedZones.none { it.samePlaceAs(active) }) {
                lockedZones + active
            } else {
                lockedZones
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
