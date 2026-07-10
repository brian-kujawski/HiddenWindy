package com.hiddenwindy.map

import com.hiddenwindy.BuildConfig

/** Static identifiers and the MapTiler style endpoint. */
object MapConstants {

    /** MapTiler "Streets v2" vector style, authenticated with the project API key. */
    val STYLE_URL: String =
        "https://api.maptiler.com/maps/streets-v2/style.json?key=${BuildConfig.MAPTILER_API_KEY}"

    const val LINES_SOURCE = "transit-lines-source"
    const val LINES_LAYER = "transit-lines-layer"

    const val STATIONS_SOURCE = "stations-source"
    const val STATIONS_LAYER = "stations-layer"

    const val SELECTED_SOURCE = "selected-station-source"
    const val SELECTED_LAYER = "selected-station-layer"

    const val REFERENCE_SOURCE = "reference-point-source"
    const val REFERENCE_LAYER = "reference-point-layer"

    const val HIDING_ZONE_SOURCE = "hiding-zone-source"
    const val HIDING_ZONE_FILL_LAYER = "hiding-zone-fill-layer"
    const val HIDING_ZONE_LINE_LAYER = "hiding-zone-line-layer"
    const val HIDING_ZONE_OUTLINE_SOURCE = "hiding-zone-outline-source"

    const val ELIMINATOR_SOURCE = "eliminator-source"
    const val ELIMINATOR_FILL_LAYER = "eliminator-fill-layer"

    const val MY_LOCATION_SOURCE = "my-location-source"
    const val MY_LOCATION_LAYER = "my-location-layer"

    /** Initial map camera — downtown Chicago (41°53'N 87°38'W). */
    const val INITIAL_LAT = 41.8833
    const val INITIAL_LON = -87.6333
    const val INITIAL_ZOOM = 10.0
}
