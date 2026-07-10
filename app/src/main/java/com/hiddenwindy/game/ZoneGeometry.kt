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
    const val HIDING_ZONE_RADIUS_MILES = 0.25

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
     * Builds an inverted hiding-zone mask: a [bounds]-sized rectangle with a circular hole punched
     * out around each of [centers]. Rendered as a fill this shades everything OUTSIDE the circles
     * while leaving the circles themselves clear. Returns null when there are no clear circles.
     */
    fun hidingZoneMask(
        centers: List<Point>,
        radiusMiles: Double = HIDING_ZONE_RADIUS_MILES,
        bounds: CityBounds = CHICAGO_BOUNDS,
    ): Polygon? {
        if (centers.isEmpty()) return null
        val outer = rectangle(bounds.west, bounds.south, bounds.east, bounds.north)
            .map { Point.fromLngLat(it[0], it[1]) }
        val holes = centers.map { hidingZoneCircle(it, radiusMiles).coordinates()[0] }
        return Polygon.fromLngLats(listOf(outer) + holes)
    }

    /**
     * Builds an inverted mask from arbitrary [regions]: a [bounds]-sized rectangle with each
     * region's outer ring punched out as a hole. Rendered as a fill this shades everything OUTSIDE
     * the regions. Returns null when [regions] is empty.
     */
    fun invertedMask(regions: List<Polygon>, bounds: CityBounds = CHICAGO_BOUNDS): Polygon? {
        if (regions.isEmpty()) return null
        val outer = rectangle(bounds.west, bounds.south, bounds.east, bounds.north)
            .map { Point.fromLngLat(it[0], it[1]) }
        val holes = regions.mapNotNull { it.coordinates().firstOrNull() }
        return Polygon.fromLngLats(listOf(outer) + holes)
    }

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

    /**
     * Half-plane "out of play" polygon for the Thermometer question.
     *
     * We travelled from [start] to [finish]. The perpendicular bisector of that segment splits the
     * map into the side closer to [start] and the side closer to [finish]. When the hider got
     * [hotter] (closer) we know they are on the [finish] side, so the [start] side is out of play;
     * when colder the [finish] side is out of play. The returned polygon covers the excluded side,
     * extended far past [bounds] so it always spans the visible play area. Returns null when start
     * and finish coincide (no direction to bisect).
     */
    fun thermometerOutPolygon(
        start: Point,
        finish: Point,
        hotter: Boolean,
        @Suppress("UNUSED_PARAMETER") bounds: CityBounds = CHICAGO_BOUNDS,
    ): Polygon? {
        // Work in a local metres frame so the perpendicular is a true right angle on the map.
        val lat0 = Math.toRadians((start.latitude() + finish.latitude()) / 2.0)
        val mPerDegLat = 111_320.0
        val mPerDegLon = 111_320.0 * Math.cos(lat0)

        fun toM(p: Point): DoubleArray =
            doubleArrayOf(p.longitude() * mPerDegLon, p.latitude() * mPerDegLat)

        fun toLngLat(x: Double, y: Double): Point =
            Point.fromLngLat(x / mPerDegLon, y / mPerDegLat)

        val s = toM(start)
        val f = toM(finish)
        var dx = f[0] - s[0]
        var dy = f[1] - s[1]
        val len = Math.hypot(dx, dy)
        if (len == 0.0) return null
        dx /= len
        dy /= len

        // Midpoint (a point on the bisector) and the perpendicular direction.
        val mx = (s[0] + f[0]) / 2.0
        val my = (s[1] + f[1]) / 2.0
        val px = -dy
        val py = dx

        // Direction pointing into the excluded half-plane: toward start if hotter, else toward finish.
        val outX = if (hotter) -dx else dx
        val outY = if (hotter) -dy else dy

        // Big enough to cover the whole play area (bounds span < ~180 km).
        val big = 500_000.0
        val a = toLngLat(mx + px * big, my + py * big)
        val b = toLngLat(mx - px * big, my - py * big)
        val c = toLngLat(mx - px * big + outX * big, my - py * big + outY * big)
        val d = toLngLat(mx + px * big + outX * big, my + py * big + outY * big)
        return Polygon.fromLngLats(listOf(listOf(a, b, c, d, a)))
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
