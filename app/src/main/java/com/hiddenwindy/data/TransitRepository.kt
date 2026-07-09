package com.hiddenwindy.data

import android.content.Context
import org.json.JSONObject

/**
 * Loads the bundled transit GeoJSON from `assets/`. The raw JSON strings are handed straight to
 * MapLibre `GeoJsonSource`s, while stations are also parsed into [Station] models for game logic.
 */
class TransitRepository(private val context: Context) {

    fun linesGeoJson(): String = readAsset(LINES_ASSET)

    fun stationsGeoJson(): String = readAsset(STATIONS_ASSET)

    fun loadStations(): List<Station> {
        val root = JSONObject(stationsGeoJson())
        val features = root.optJSONArray("features") ?: return emptyList()
        val result = ArrayList<Station>(features.length())
        for (i in 0 until features.length()) {
            val feature = features.optJSONObject(i) ?: continue
            val geometry = feature.optJSONObject("geometry") ?: continue
            val coords = geometry.optJSONArray("coordinates") ?: continue
            if (coords.length() < 2) continue
            val lon = coords.optDouble(0)
            val lat = coords.optDouble(1)
            val props = feature.optJSONObject("properties") ?: JSONObject()

            val linesArray = props.optJSONArray("lines")
            val lines = if (linesArray != null) {
                (0 until linesArray.length()).map { linesArray.optString(it) }
            } else {
                emptyList()
            }

            result.add(
                Station(
                    name = props.optString("name"),
                    lon = lon,
                    lat = lat,
                    lines = lines,
                    ada = props.optBoolean("ada", false),
                    mapId = props.optString("mapId").takeIf { it.isNotEmpty() },
                )
            )
        }
        return result
    }

    private fun readAsset(name: String): String =
        context.assets.open(name).bufferedReader().use { it.readText() }

    companion object {
        private const val LINES_ASSET = "transit_lines.geojson"
        private const val STATIONS_ASSET = "stations.geojson"
    }
}
