package com.hiddenwindy.game

import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import org.maplibre.turf.TurfConstants
import org.maplibre.turf.TurfTransformation

/** Rectangular geographic bounds (WGS84 degrees). */
data class CityBounds(
    val west: Double,
    val south: Double,
    val east: Double,
    val north: Double,
) {
    val centerLat: Double get() = (south + north) / 2.0
    val centerLon: Double get() = (west + east) / 2.0
}

/** Pure geometry helpers for the hiding zone and elimination overlays. */
object ZoneGeometry {

    /** Default hiding zone radius, per the game rules. */
    const val HIDING_ZONE_RADIUS_MILES = 0.5

    /**
     * Generous bounds covering the Chicago CTA 'L' + Metra network. Directional cuts are clipped to
     * these bounds so the shaded polygons always cover the visible play area.
     */
    val CHICAGO_BOUNDS = CityBounds(
        west = -88.90,
        south = 41.20,
        east = -87.20,
        north = 42.80,
    )

    /** Builds a geodesic ~circle polygon of [radiusMiles] around [center]. */
    fun hidingZoneCircle(center: Point, radiusMiles: Double = HIDING_ZONE_RADIUS_MILES): Polygon =
        TurfTransformation.circle(center, radiusMiles, CIRCLE_STEPS, TurfConstants.UNIT_MILES)

    /**
     * Returns the "out of play" rectangle on the [direction] side of [origin], clipped to [bounds].
     * Returns null when the origin already sits outside the bounds on that axis (nothing to shade).
     */
    fun directionalOutPolygon(
        origin: Point,
        direction: Direction,
        bounds: CityBounds = CHICAGO_BOUNDS,
    ): Polygon? {
        val lon = origin.longitude()
        val lat = origin.latitude()
        val (west, south, east, north) = listOf(bounds.west, bounds.south, bounds.east, bounds.north)

        val rect: List<DoubleArray> = when (direction) {
            Direction.NORTH -> {
                if (lat >= north) return null
                rectangle(west, lat.coerceAtLeast(south), east, north)
            }
            Direction.SOUTH -> {
                if (lat <= south) return null
                rectangle(west, south, east, lat.coerceAtMost(north))
            }
            Direction.EAST -> {
                if (lon >= east) return null
                rectangle(lon.coerceAtLeast(west), south, east, north)
            }
            Direction.WEST -> {
                if (lon <= west) return null
                rectangle(west, south, lon.coerceAtMost(east), north)
            }
        }
        val ring = rect.map { Point.fromLngLat(it[0], it[1]) }
        return Polygon.fromLngLats(listOf(ring))
    }

    /** Closed rectangle ring (lon/lat) in counter-clockwise order. */
    private fun rectangle(west: Double, south: Double, east: Double, north: Double): List<DoubleArray> =
        listOf(
            doubleArrayOf(west, south),
            doubleArrayOf(east, south),
            doubleArrayOf(east, north),
            doubleArrayOf(west, north),
            doubleArrayOf(west, south),
        )

    private const val CIRCLE_STEPS = 64
}
