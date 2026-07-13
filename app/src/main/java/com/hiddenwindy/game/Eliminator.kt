package com.hiddenwindy.game

import com.hiddenwindy.data.Station
import com.hiddenwindy.map.PolygonUnion
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

/** Cardinal direction used by [DirectionalEliminator] to describe the eliminated side. */
enum class Direction(val label: String) {
    NORTH("North"),
    SOUTH("South"),
    EAST("East"),
    WEST("West"),
}

/**
 * The information a rule needs to decide which part of the map is "out of play".
 *
 * Additional context (e.g. the current transit line, seeker positions) can be added here as new
 * rule types are introduced without changing existing eliminators.
 */
data class GameContext(
    val referencePoint: Point?,
    val selectedStation: Station?,
    val stations: List<Station>,
    val bounds: CityBounds = ZoneGeometry.CHICAGO_BOUNDS,
)

/**
 * A single question/answer that narrows the hiding zone. Each eliminator reports the polygons that
 * are now *out of play*; the remaining unshaded area is still in play.
 *
 * This is the core extension point: new ways to whittle down the zone (radius-from-me, same/different
 * line, N-stops-away, etc.) are added simply by implementing this interface — no changes to the
 * view model or rendering code are required.
 */
interface Eliminator {
    /** Stable identifier, unique within the active rule list. */
    val id: String

    /** Short human-readable description shown in the UI (e.g. "North of me"). */
    val label: String

    /** Regions that are eliminated given [context]. Empty when the rule currently shades nothing. */
    fun outRegions(context: GameContext): List<Polygon>
}

/**
 * Eliminates everything on one cardinal side of a reference point — the answer to
 * "are you north or south of me?" and similar questions.
 */
data class DirectionalEliminator(
    override val id: String,
    val origin: Point,
    val direction: Direction,
) : Eliminator {

    override val label: String = "${direction.label} of point"

    override fun outRegions(context: GameContext): List<Polygon> =
        listOfNotNull(ZoneGeometry.directionalOutPolygon(origin, direction, context.bounds))
}

/**
 * Eliminates based on the Matching question "is your nearest line the same as mine?".
 *
 * [regions] are the precomputed nearest-line polygons for [lineName]. When the hider's nearest line
 * is the SAME ([same] = true) everything outside those regions is out of play; when DIFFERENT the
 * regions themselves are out of play.
 */
data class LineMatchEliminator(
    override val id: String,
    val lineName: String,
    val regions: List<Polygon>,
    val same: Boolean,
) : Eliminator {

    override val label: String =
        if (same) "Same line as $lineName" else "Not near $lineName"

    override fun outRegions(context: GameContext): List<Polygon> =
        if (same) {
            listOfNotNull(ZoneGeometry.invertedMask(regions, context.bounds))
        } else {
            regions
        }
}

/**
 * Eliminates based on a "nearest named point" Matching question (e.g. "nearest airport").
 *
 * [regions] are the precomputed Voronoi polygons for [featureName] from `PointRegionsRepository`.
 * When the hider's nearest point is the SAME ([same] = true) everything outside those regions is
 * out of play; when DIFFERENT the regions themselves are out of play.
 */
data class PointMatchEliminator(
    override val id: String,
    val featureName: String,
    val regions: List<Polygon>,
    val same: Boolean,
) : Eliminator {

    override val label: String =
        if (same) "Same as $featureName" else "Not near $featureName"

    override fun outRegions(context: GameContext): List<Polygon> =
        if (same) {
            listOfNotNull(ZoneGeometry.invertedMask(regions, context.bounds))
        } else {
            regions
        }
}

/**
 * Eliminates based on the Thermometer question: we travelled from [start] to [finish] and the hider
 * reported whether they got [hotter] (closer) or colder. Shades the half-plane on the far side of
 * the perpendicular bisector of that path.
 */
data class ThermometerEliminator(
    override val id: String,
    val start: Point,
    val finish: Point,
    val hotter: Boolean,
) : Eliminator {

    override val label: String = if (hotter) "Hotter" else "Colder"

    override fun outRegions(context: GameContext): List<Polygon> =
        listOfNotNull(ZoneGeometry.thermometerOutPolygon(start, finish, hotter, context.bounds))
}

/**
 * Eliminates based on a Measuring POI question: "compared to me, are you closer to or farther
 * from a [featureLabel]?"
 *
 * The seeker is [distanceMiles] from their nearest POI. If the hider is [closer], they must be
 * within [distanceMiles] of at least one POI — so anywhere farther than [distanceMiles] from
 * every POI is out. If they are farther, they must be beyond [distanceMiles] from every POI —
 * so anywhere within [distanceMiles] of any POI is out.
 */
data class MeasuringPOIEliminator(
    override val id: String,
    val featureLabel: String,
    val poiLocations: List<Point>,
    val distanceMiles: Double,
    val closer: Boolean,
) : Eliminator {

    override val label: String = if (closer) "Closer to $featureLabel" else "Farther from $featureLabel"

    override fun outRegions(context: GameContext): List<Polygon> {
        val circles = poiLocations.map { ZoneGeometry.hidingZoneCircle(it, distanceMiles) }
        return if (closer) {
            // Dissolve overlapping circles into a non-overlapping union before punching holes in the
            // inverted mask. Without this, overlapping hole rings trigger the even-odd fill rule
            // and shade their intersection instead of leaving it clear.
            val dissolved = PolygonUnion.dissolve(circles)
            listOfNotNull(ZoneGeometry.invertedMask(dissolved, context.bounds))
        } else {
            circles
        }
    }
}
