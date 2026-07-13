package com.hiddenwindy.map

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.LinearRing
import org.locationtech.jts.operation.union.UnaryUnionOp
import org.maplibre.geojson.Point
import kotlin.math.cos
import org.locationtech.jts.geom.Polygon as JtsPolygon
import org.maplibre.geojson.Polygon as MlPolygon

/**
 * Dissolves overlapping exclusion polygons into a single non-overlapping geometry.
 *
 * Rendering many translucent polygons stacks their opacity where they overlap, so redundantly
 * eliminated area appears darker. Unioning first means every eliminated area renders as one flat
 * shade. This is also semantically correct: a spot is out of play if *any* question excludes it,
 * which is exactly the geometric union of all exclusions.
 */
object PolygonUnion {

    private val factory = GeometryFactory()

    /** Unions [polygons] (which may overlap or contain holes) into non-overlapping MapLibre polygons. */
    fun dissolve(polygons: List<MlPolygon>): List<MlPolygon> {
        if (polygons.isEmpty()) return emptyList()
        val jts = polygons.mapNotNull { toJts(it) }
        if (jts.isEmpty()) return emptyList()
        val union = UnaryUnionOp.union(jts) ?: return emptyList()
        return fromJts(union)
    }

    /**
     * Buffers [polygons] outward by [miles] and returns the dissolved result.
     *
     * Buffering happens in a local equirectangular metre frame (centred on [centerLat]) so the
     * buffer distance is uniform on the ground; a fixed degree buffer would stretch east-west versus
     * north-south. Overlapping buffers merge into non-overlapping output, ready for rendering or
     * [org.maplibre.geojson.Polygon] hole-punching.
     */
    fun bufferMiles(polygons: List<MlPolygon>, miles: Double, centerLat: Double): List<MlPolygon> {
        if (polygons.isEmpty() || miles <= 0.0) return emptyList()
        val mPerDegLat = 110_540.0
        val mPerDegLon = 111_320.0 * cos(Math.toRadians(centerLat))

        fun project(c: Coordinate) = Coordinate(c.x * mPerDegLon, c.y * mPerDegLat)
        fun unproject(c: Coordinate) = Coordinate(c.x / mPerDegLon, c.y / mPerDegLat)

        val jts = polygons.mapNotNull { toJts(it) }
        if (jts.isEmpty()) return emptyList()

        val projected = jts.map { transformGeometry(it, ::project) }
        val union = UnaryUnionOp.union(projected) ?: return emptyList()
        val buffered = union.buffer(miles * 1609.344)
        val backProjected = transformGeometry(buffered, ::unproject)
        return fromJts(backProjected)
    }

    /** Applies [fn] to every coordinate of [geometry], returning a new geometry. */
    private fun transformGeometry(geometry: Geometry, fn: (Coordinate) -> Coordinate): Geometry {
        val copy = geometry.copy()
        copy.apply(org.locationtech.jts.geom.CoordinateFilter { c ->
            val moved = fn(c)
            c.x = moved.x
            c.y = moved.y
        })
        copy.geometryChanged()
        return copy
    }

    private fun toJts(polygon: MlPolygon): JtsPolygon? {
        val rings = polygon.coordinates()
        if (rings.isEmpty()) return null
        val shell = toRing(rings[0]) ?: return null
        val holes = rings.drop(1).mapNotNull { toRing(it) }
        return factory.createPolygon(shell, holes.toTypedArray())
    }

    private fun toRing(points: List<Point>): LinearRing? {
        if (points.size < 3) return null
        val coords = points.map { Coordinate(it.longitude(), it.latitude()) }.toMutableList()
        // JTS requires an explicitly closed ring.
        if (coords.first() != coords.last()) coords.add(coords.first())
        if (coords.size < 4) return null
        return factory.createLinearRing(coords.toTypedArray())
    }

    private fun fromJts(geometry: Geometry): List<MlPolygon> {
        val result = mutableListOf<MlPolygon>()
        for (i in 0 until geometry.numGeometries) {
            val g = geometry.getGeometryN(i)
            if (g is JtsPolygon && !g.isEmpty) result.add(fromJtsPolygon(g))
        }
        return result
    }

    private fun fromJtsPolygon(polygon: JtsPolygon): MlPolygon {
        val rings = mutableListOf<List<Point>>()
        rings.add(ringToPoints(polygon.exteriorRing.coordinates))
        for (i in 0 until polygon.numInteriorRing) {
            rings.add(ringToPoints(polygon.getInteriorRingN(i).coordinates))
        }
        return MlPolygon.fromLngLats(rings)
    }

    private fun ringToPoints(coords: Array<Coordinate>): List<Point> =
        coords.map { Point.fromLngLat(it.x, it.y) }
}
