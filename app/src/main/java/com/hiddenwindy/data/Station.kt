package com.hiddenwindy.data

import org.maplibre.geojson.Point

/** A single transit station parsed from the bundled GeoJSON. */
data class Station(
    val name: String,
    val lon: Double,
    val lat: Double,
    val lines: List<String>,
    val ada: Boolean,
    val mapId: String?,
) {
    fun point(): Point = Point.fromLngLat(lon, lat)
}
