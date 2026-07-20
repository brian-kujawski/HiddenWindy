package com.hiddenwindy.game

import com.hiddenwindy.data.NamedPoint
import com.hiddenwindy.data.Station
import com.hiddenwindy.map.PolygonUnion
import com.hiddenwindy.map.WaterDistanceGrid
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

/**
 * Eliminates based on the Measuring "Body of Water" question: "compared to me, are you closer to or
 * farther from a body of water?"
 *
 * The seeker's nearest water edge is [distanceMiles] away. If the hider is [closer], they are within
 * [distanceMiles] of some water, so everywhere farther than that from every body is out. If they are
 * farther, everywhere within [distanceMiles] of any body is out. [waterPolygons] are the named
 * bodies' shapes; buffering them by [distanceMiles] yields the "within" region.
 */
data class MeasuringWaterEliminator(
    override val id: String,
    val featureLabel: String,
    val grid: WaterDistanceGrid,
    val distanceMiles: Double,
    val closer: Boolean,
) : Eliminator {

    override val label: String =
        if (closer) "Closer to $featureLabel" else "Farther from $featureLabel"

    // The contour depends only on immutable fields, but outRegions() is called on every state
    // emission (render + stations-in-play), and the countdown timer emits every second. Compute the
    // isodistance geometry once and reuse it so we never re-run marching squares per frame.
    private val cachedRegions: List<Polygon> by lazy {
        val thresholdMeters = distanceMiles * 1609.344
        if (closer) {
            // Hider is closer to water than us: shade everything FARTHER than the threshold.
            grid.regionsBeyond(thresholdMeters)
        } else {
            // Hider is farther: shade everything WITHIN the threshold of water.
            grid.regionsWithin(thresholdMeters)
        }
    }

    // Threshold the precomputed distance field and contour the exact polarity we want to shade,
    // returning valid polygons-with-holes directly. This avoids the runtime JTS buffer (ANR) and
    // the invertedMask ring-nesting bug.
    override fun outRegions(context: GameContext): List<Polygon> = cachedRegions
}

/**
 * Eliminates based on the Measuring "Coastline" question: "compared to me, are you closer to or
 * farther from the coastline?"
 *
 * The seeker is [distanceMiles] from the coastline. Buffering the [coastline] polyline by that
 * distance yields the band within [distanceMiles] of it. If the hider is [closer] everything outside
 * the band is out; if farther the band itself is out.
 */
data class MeasuringCoastlineEliminator(
    override val id: String,
    val featureLabel: String,
    val coastline: List<Point>,
    val distanceMiles: Double,
    val closer: Boolean,
) : Eliminator {

    override val label: String =
        if (closer) "Closer to $featureLabel" else "Farther from $featureLabel"

    override fun outRegions(context: GameContext): List<Polygon> {
        val band = PolygonUnion.bufferLineMiles(coastline, distanceMiles, context.bounds.centerLat)
        if (band.isEmpty()) return emptyList()
        return if (closer) {
            listOfNotNull(ZoneGeometry.invertedMask(band, context.bounds))
        } else {
            band
        }
    }
}

/**
 * Eliminates based on a Tentacles question: "Of the [featureLabel]s within 1 mile of me, which
 * one are you closest to? (You must also be within 1 mile.)"
 *
 * At resolve time we know the hider's [chosenPOIName] (which nearby POI they are closest to) and
 * that they are within 1 mile of [seekerLocation]. The valid hider region is the intersection of:
 *   - the 1-mile circle around [seekerLocation], AND
 *   - the Voronoi cell of [chosenPOIName] computed only over [nearbyPOIs] (not the global dataset).
 *
 * Everything outside that intersection is shaded as eliminated.
 *
 * When [chosenPOIName] is null the hider reported they are NOT within 1 mile of the seeker, so the
 * question degrades to a 1-mile Radar "no": the 1-mile circle around [seekerLocation] is out.
 */
data class TentaclesEliminator(
    override val id: String,
    val featureLabel: String,
    val seekerLocation: Point,
    val nearbyPOIs: List<NamedPoint>,
    val chosenPOIName: String?,
) : Eliminator {

    override val label: String =
        if (chosenPOIName != null) "$featureLabel: $chosenPOIName" else "$featureLabel: outside 1 mi"

    override fun outRegions(context: GameContext): List<Polygon> {
        if (chosenPOIName == null) {
            return listOf(ZoneGeometry.hidingZoneCircle(seekerLocation, 1.0))
        }
        val validRegion = PolygonUnion.tentaclesValidRegion(seekerLocation, nearbyPOIs, chosenPOIName)
        if (validRegion.isEmpty()) return emptyList()
        return listOfNotNull(ZoneGeometry.invertedMask(validRegion, context.bounds))
    }
}
