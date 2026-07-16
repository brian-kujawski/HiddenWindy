package com.hiddenwindy.map

import com.hiddenwindy.data.NamedPoint
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.LinearRing
import org.locationtech.jts.geom.util.GeometryFixer
import org.locationtech.jts.operation.union.UnaryUnionOp
import org.locationtech.jts.triangulate.VoronoiDiagramBuilder
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
        // Repair each polygon before unioning. The marching-squares water contour can emit rings
        // with tiny self-intersections or imperfect hole nesting; feeding those straight into JTS's
        // overlay throws TopologyException ("unable to assign free hole to a shell") and crashes the
        // app. GeometryFixer (JTS 1.19) snaps them to valid geometry first.
        val jts = polygons.mapNotNull { toJts(it) }.mapNotNull { poly ->
            runCatching { GeometryFixer.fix(poly) }.getOrNull()?.takeUnless { it.isEmpty }
        }
        if (jts.isEmpty()) return emptyList()
        // Even with repaired input, JTS overlay can hit rare robustness failures. Never let that
        // take down the app: fall back to the (already valid) unrepaired polygons, which still
        // render correctly just without opacity flattening where they overlap.
        val union = runCatching { UnaryUnionOp.union(jts) }.getOrNull()
        return if (union != null) fromJts(union) else polygons
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

    /**
     * Buffers a polyline (given as its ordered [points]) outward by [miles] on both sides, returning
     * the resulting band as MapLibre polygons. Used by distance-from-a-line questions (e.g. the
     * coastline). Buffering happens in the same local metre frame as [bufferMiles].
     */
    fun bufferLineMiles(points: List<Point>, miles: Double, centerLat: Double): List<MlPolygon> {
        if (points.size < 2 || miles <= 0.0) return emptyList()
        val mPerDegLat = 110_540.0
        val mPerDegLon = 111_320.0 * cos(Math.toRadians(centerLat))

        val coords = points
            .map { Coordinate(it.longitude() * mPerDegLon, it.latitude() * mPerDegLat) }
            .toTypedArray()
        val line = factory.createLineString(coords)
        val buffered = line.buffer(miles * 1609.344)

        fun unproject(c: Coordinate) = Coordinate(c.x / mPerDegLon, c.y / mPerDegLat)
        val backProjected = transformGeometry(buffered, ::unproject)
        return fromJts(backProjected)
    }

    /**
     * Computes the Voronoi cell of the POI named [chosenName] within [nearbyPOIs], intersected
     * with a [radiusMiles]-radius circle around [center]. Used for Tentacles questions, which
     * partition the search space among only the POIs within 1 mile — not the precomputed global
     * Voronoi regions.
     *
     * All computation is done in a local equirectangular metre frame (same as [bufferMiles]);
     * the result is returned in longitude/latitude coordinates.
     */
    fun tentaclesValidRegion(
        center: Point,
        nearbyPOIs: List<NamedPoint>,
        chosenName: String,
        radiusMiles: Double = 1.0,
    ): List<MlPolygon> {
        if (nearbyPOIs.isEmpty()) return emptyList()
        val chosen = nearbyPOIs.firstOrNull { it.name == chosenName } ?: return emptyList()

        val centerLat = center.latitude()
        val mPerDegLat = 110_540.0
        val mPerDegLon = 111_320.0 * cos(Math.toRadians(centerLat))

        fun project(p: Point): Coordinate = Coordinate(p.longitude() * mPerDegLon, p.latitude() * mPerDegLat)
        fun unproject(c: Coordinate): Coordinate = Coordinate(c.x / mPerDegLon, c.y / mPerDegLat)

        return runCatching {
            val centerProj = project(center)
            val oneMileMeters = radiusMiles * 1609.344
            val circleJts = factory.createPoint(centerProj).buffer(oneMileMeters, 64)

            if (nearbyPOIs.size == 1) {
                // Only one nearby POI — the entire 1-mile circle is valid.
                return@runCatching fromJts(circleJts)
            }

            val chosenProj = project(chosen.location)
            val sitePts = nearbyPOIs.map { factory.createPoint(project(it.location)) }
            val sites = factory.createGeometryCollection(sitePts.toTypedArray())

            val clipEnv = Envelope(circleJts.envelopeInternal)
            clipEnv.expandBy(oneMileMeters)

            val builder = VoronoiDiagramBuilder()
            builder.setSites(sites)
            builder.setClipEnvelope(clipEnv)
            val diagram = builder.getDiagram(factory)

            // Find the Voronoi cell that contains the chosen POI's projected point.
            val chosenPt = factory.createPoint(chosenProj)
            var voronoiCell: Geometry? = null
            for (i in 0 until diagram.numGeometries) {
                val cell = diagram.getGeometryN(i)
                if (cell.intersects(chosenPt)) {
                    voronoiCell = cell
                    break
                }
            }
            if (voronoiCell == null) return@runCatching emptyList()

            val validRegion = voronoiCell.intersection(circleJts)
            if (validRegion.isEmpty) return@runCatching emptyList<MlPolygon>()

            val unprojected = transformGeometry(validRegion, ::unproject)
            fromJts(unprojected)
        }.getOrElse { emptyList() }
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
