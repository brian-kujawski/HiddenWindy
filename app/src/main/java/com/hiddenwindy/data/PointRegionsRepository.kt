package com.hiddenwindy.data

import android.content.Context
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.MultiPolygon
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import org.maplibre.turf.TurfConstants
import org.maplibre.turf.TurfJoins
import org.maplibre.turf.TurfMeasurement
import org.maplibre.turf.TurfMisc

/** A named geographic point loaded from a CSV asset (e.g. an airport). */
data class NamedPoint(val name: String, val location: Point)

/**
 * Loads precomputed "nearest named point" region partitions and their source point data.
 *
 * For each dataset (e.g. "transit_airport") this repository reads:
 *   - `assets/<dataset>_regions.geojson` — the Voronoi partition built by
 *     `tools/build_point_regions.py`; used by the eliminator to shade the map.
 *   - `assets/csv/<dataset>.csv` — the original named points; used at runtime to
 *     compute which point is nearest to the seeker's location.
 */
class PointRegionsRepository(private val context: Context) {

    /** Returns name → polygons for the given dataset's precomputed Voronoi regions. */
    fun loadRegions(dataset: String): Map<String, List<Polygon>> {
        val fc = FeatureCollection.fromJson(readAsset("${dataset}_regions.geojson"))
        val map = HashMap<String, MutableList<Polygon>>()
        for (feature in fc.features() ?: emptyList()) {
            val name = feature.getStringProperty("name") ?: continue
            when (val geometry = feature.geometry()) {
                is Polygon -> map.getOrPut(name) { mutableListOf() }.add(geometry)
                is MultiPolygon -> geometry.polygons().forEach {
                    map.getOrPut(name) { mutableListOf() }.add(it)
                }
                else -> Unit
            }
        }
        return map
    }

    /** Returns the named points for the given dataset, loaded from its bundled CSV. */
    fun loadNamedPoints(dataset: String): List<NamedPoint> {
        val text = readAsset("csv/${dataset}.csv")
        val lines = text.lines()
        if (lines.size < 2) return emptyList()
        // Header: name,latitude,longitude
        return lines.drop(1).mapNotNull { line ->
            val parts = line.split(",")
            if (parts.size < 3) return@mapNotNull null
            val name = parts[0].trim()
            val lat = parts[1].trim().toDoubleOrNull() ?: return@mapNotNull null
            val lon = parts[2].trim().toDoubleOrNull() ?: return@mapNotNull null
            NamedPoint(name, Point.fromLngLat(lon, lat))
        }
    }

    /** Returns the name of the point nearest to [location], or null when [points] is empty. */
    fun nearestName(location: Point, points: List<NamedPoint>): String? =
        points.minByOrNull {
            TurfMeasurement.distance(location, it.location, TurfConstants.UNIT_KILOMETERS)
        }?.name

    /**
     * For a polygon dataset (e.g. named bodies of water), returns the nearest body's name paired
     * with the straight-line distance in miles to its nearest edge (0 when [location] is inside a
     * body). Returns null when [regions] is empty.
     */
    fun nearestPolygonBody(
        location: Point,
        regions: Map<String, List<Polygon>>,
    ): Pair<String, Double>? {
        var bestName: String? = null
        var bestMiles = Double.MAX_VALUE
        for ((name, polygons) in regions) {
            for (polygon in polygons) {
                val miles = if (TurfJoins.inside(location, polygon)) {
                    0.0
                } else {
                    var nearestEdge = Double.MAX_VALUE
                    for (ring in polygon.coordinates()) {
                        if (ring.size < 2) continue
                        val onLine = TurfMisc.nearestPointOnLine(location, ring).geometry() as? Point
                            ?: continue
                        val d = TurfMeasurement.distance(location, onLine, TurfConstants.UNIT_MILES)
                        if (d < nearestEdge) nearestEdge = d
                    }
                    nearestEdge
                }
                if (miles < bestMiles) {
                    bestMiles = miles
                    bestName = name
                }
            }
            if (bestMiles == 0.0) break
        }
        return bestName?.let { it to bestMiles }
    }

    private fun readAsset(name: String): String =
        context.assets.open(name).bufferedReader().use { it.readText() }
}
