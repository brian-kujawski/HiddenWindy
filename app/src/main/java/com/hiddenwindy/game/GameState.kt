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
) {
    /** The active hiding zone circle, or null when hidden / no station selected. */
    val hidingZone: Polygon?
        get() = selectedStation
            ?.takeIf { hidingZoneVisible }
            ?.let { ZoneGeometry.hidingZoneCircle(it.point()) }

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
