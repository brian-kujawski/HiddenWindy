package com.hiddenwindy.data

import android.content.Context
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.MultiPolygon
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import org.maplibre.turf.TurfConstants
import org.maplibre.turf.TurfMisc

/**
 * Loads the precomputed "nearest transit line" partition (`line_regions.geojson`, baked offline by
 * `tools/build_line_regions.py`) and the raw line geometry used to decide which line a point is
 * nearest to. Regions may overlap where lines share track.
 */
class LineRegionsRepository(private val context: Context) {

    /** name -> the polygons that make up that line's nearest-line region. */
    fun loadRegions(): Map<String, List<Polygon>> {
        val fc = FeatureCollection.fromJson(readAsset(REGIONS_ASSET))
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

    /** name -> every LineString drawn for that line (branches collapse under the same name). */
    fun loadLinesByName(): Map<String, List<LineString>> {
        val fc = FeatureCollection.fromJson(readAsset(LINES_ASSET))
        val map = HashMap<String, MutableList<LineString>>()
        for (feature in fc.features() ?: emptyList()) {
            val name = feature.getStringProperty("name") ?: continue
            (feature.geometry() as? LineString)?.let {
                map.getOrPut(name) { mutableListOf() }.add(it)
            }
        }
        return map
    }

    /** Returns the name of the line nearest to [point], or null when [linesByName] is empty. */
    fun nearestLineName(point: Point, linesByName: Map<String, List<LineString>>): String? {
        var best: String? = null
        var bestDist = Double.MAX_VALUE
        for ((name, lines) in linesByName) {
            for (line in lines) {
                val coords = line.coordinates()
                if (coords.size < 2) continue
                val snapped = TurfMisc.nearestPointOnLine(point, coords, TurfConstants.UNIT_KILOMETERS)
                val dist = snapped.getNumberProperty("dist")?.toDouble() ?: continue
                if (dist < bestDist) {
                    bestDist = dist
                    best = name
                }
            }
        }
        return best
    }

    /** Returns the straight-line distance in miles to the nearest line, or null when [linesByName] is empty. */
    fun nearestLineDistanceMiles(point: Point, linesByName: Map<String, List<LineString>>): Double? {
        var bestMiles: Double? = null
        for ((_, lines) in linesByName) {
            for (line in lines) {
                val coords = line.coordinates()
                if (coords.size < 2) continue
                val snapped = TurfMisc.nearestPointOnLine(point, coords, TurfConstants.UNIT_MILES)
                val dist = snapped.getNumberProperty("dist")?.toDouble() ?: continue
                if (bestMiles == null || dist < bestMiles) bestMiles = dist
            }
        }
        return bestMiles
    }

    /**
     * Returns all line names within [thresholdKm] of the nearest line to [point], sorted
     * alphabetically. Returns a single element in the common case; multiple elements when lines
     * share track (e.g. the Loop, where Green/Brown/Orange/Pink/Purple run together).
     */
    fun nearestLineNames(
        point: Point,
        linesByName: Map<String, List<LineString>>,
        thresholdKm: Double = 0.015,
    ): List<String> {
        var bestDist = Double.MAX_VALUE
        val bestDistByName = HashMap<String, Double>()
        for ((name, lines) in linesByName) {
            for (line in lines) {
                val coords = line.coordinates()
                if (coords.size < 2) continue
                val snapped = TurfMisc.nearestPointOnLine(point, coords, TurfConstants.UNIT_KILOMETERS)
                val dist = snapped.getNumberProperty("dist")?.toDouble() ?: continue
                val prev = bestDistByName[name]
                if (prev == null || dist < prev) bestDistByName[name] = dist
                if (dist < bestDist) bestDist = dist
            }
        }
        if (bestDist == Double.MAX_VALUE) return emptyList()
        return bestDistByName.entries
            .filter { (_, dist) -> dist <= bestDist + thresholdKm }
            .map { it.key }
            .sorted()
    }

    private fun readAsset(name: String): String =
        context.assets.open(name).bufferedReader().use { it.readText() }

    companion object {
        private const val REGIONS_ASSET = "line_regions.geojson"
        private const val LINES_ASSET = "transit_lines.geojson"
    }
}
