package com.hiddenwindy.map

import com.hiddenwindy.data.Station
import com.hiddenwindy.game.GameState
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.maps.Style
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection

/**
 * Owns the MapLibre sources and layers and keeps them in sync with [GameState].
 *
 * Rendering order (bottom to top): transit lines, eliminator shading, hiding zone, stations,
 * selected-station highlight.
 */
class MapController {

    private var style: Style? = null

    private val empty: FeatureCollection = FeatureCollection.fromFeatures(emptyList<Feature>())

    /** Adds all sources and layers to [style] using the bundled transit GeoJSON. */
    fun setup(style: Style, linesGeoJson: String, stationsGeoJson: String) {
        this.style = style

        style.addSource(GeoJsonSource(MapConstants.LINES_SOURCE, linesGeoJson))
        style.addSource(GeoJsonSource(MapConstants.STATIONS_SOURCE, stationsGeoJson))
        style.addSource(GeoJsonSource(MapConstants.ELIMINATOR_SOURCE, empty))
        style.addSource(GeoJsonSource(MapConstants.HIDING_ZONE_SOURCE, empty))
        style.addSource(GeoJsonSource(MapConstants.HIDING_ZONE_OUTLINE_SOURCE, empty))
        style.addSource(GeoJsonSource(MapConstants.SELECTED_SOURCE, empty))
        style.addSource(GeoJsonSource(MapConstants.REFERENCE_SOURCE, empty))
        style.addSource(GeoJsonSource(MapConstants.MY_LOCATION_SOURCE, empty))
        style.addSource(GeoJsonSource(MapConstants.RADAR_PREVIEW_SOURCE, empty))
        style.addSource(GeoJsonSource(MapConstants.RADAR_RESULT_SOURCE, empty))

        // Transit lines (data-driven colour from the "color" property).
        style.addLayer(
            LineLayer(MapConstants.LINES_LAYER, MapConstants.LINES_SOURCE).withProperties(
                PropertyFactory.lineColor(Expression.get("color")),
                PropertyFactory.lineWidth(3.0f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            )
        )

        // "Out of play" shading.
        style.addLayer(
            FillLayer(MapConstants.ELIMINATOR_FILL_LAYER, MapConstants.ELIMINATOR_SOURCE).withProperties(
                PropertyFactory.fillColor("#D32F2F"),
                PropertyFactory.fillOpacity(0.35f),
            )
        )

        // Hiding zone circle.
        style.addLayer(
            FillLayer(MapConstants.HIDING_ZONE_FILL_LAYER, MapConstants.HIDING_ZONE_SOURCE).withProperties(
                PropertyFactory.fillColor("#1565C0"),
                PropertyFactory.fillOpacity(0.18f),
            )
        )
        style.addLayer(
            LineLayer(MapConstants.HIDING_ZONE_LINE_LAYER, MapConstants.HIDING_ZONE_OUTLINE_SOURCE).withProperties(
                PropertyFactory.lineColor("#1565C0"),
                PropertyFactory.lineWidth(2.0f),
            )
        )

        // Stations.
        style.addLayer(
            CircleLayer(MapConstants.STATIONS_LAYER, MapConstants.STATIONS_SOURCE).withProperties(
                PropertyFactory.circleRadius(4.0f),
                PropertyFactory.circleColor("#FFFFFF"),
                PropertyFactory.circleStrokeColor("#37474F"),
                PropertyFactory.circleStrokeWidth(1.5f),
            )
        )

        // Selected-station highlight.
        style.addLayer(
            CircleLayer(MapConstants.SELECTED_LAYER, MapConstants.SELECTED_SOURCE).withProperties(
                PropertyFactory.circleRadius(7.0f),
                PropertyFactory.circleColor("#FFC107"),
                PropertyFactory.circleStrokeColor("#37474F"),
                PropertyFactory.circleStrokeWidth(2.0f),
            )
        )

        // Radar preview circle — shown while a radius is selected but not yet confirmed.
        style.addLayer(
            FillLayer(MapConstants.RADAR_PREVIEW_FILL_LAYER, MapConstants.RADAR_PREVIEW_SOURCE).withProperties(
                PropertyFactory.fillColor("#FF6F00"),
                PropertyFactory.fillOpacity(0.12f),
            )
        )
        style.addLayer(
            LineLayer(MapConstants.RADAR_PREVIEW_LINE_LAYER, MapConstants.RADAR_PREVIEW_SOURCE).withProperties(
                PropertyFactory.lineColor("#FF6F00"),
                PropertyFactory.lineWidth(2.0f),
                PropertyFactory.lineDasharray(arrayOf(4f, 3f)),
            )
        )

        // Permanent radar results — deep red, higher opacity than directional cuts.
        style.addLayer(
            FillLayer(MapConstants.RADAR_RESULT_FILL_LAYER, MapConstants.RADAR_RESULT_SOURCE).withProperties(
                PropertyFactory.fillColor("#B71C1C"),
                PropertyFactory.fillOpacity(0.45f),
            )
        )

        // My location — rendered on top of all other layers.
        style.addLayer(
            CircleLayer(MapConstants.MY_LOCATION_LAYER, MapConstants.MY_LOCATION_SOURCE).withProperties(
                PropertyFactory.circleRadius(9.0f),
                PropertyFactory.circleColor("#1A73E8"),
                PropertyFactory.circleStrokeColor("#FFFFFF"),
                PropertyFactory.circleStrokeWidth(2.5f),
            )
        )
    }

    /** Pushes the current [state] into the overlay sources. No-op before [setup]. */
    fun render(state: GameState) {
        val style = style ?: return

        val mask = state.hidingZoneMask
        source(style, MapConstants.HIDING_ZONE_SOURCE)?.setGeoJson(
            if (mask != null) {
                FeatureCollection.fromFeature(Feature.fromGeometry(mask))
            } else {
                empty
            }
        )
        source(style, MapConstants.HIDING_ZONE_OUTLINE_SOURCE)?.setGeoJson(
            FeatureCollection.fromFeatures(state.hidingZoneCircles.map { Feature.fromGeometry(it) })
        )

        val outFeatures = state.outRegions().map { Feature.fromGeometry(it) }
        source(style, MapConstants.ELIMINATOR_SOURCE)?.setGeoJson(
            FeatureCollection.fromFeatures(outFeatures)
        )

        source(style, MapConstants.SELECTED_SOURCE)?.setGeoJson(
            state.selectedStation?.let { selectedFeature(it) } ?: empty
        )

        source(style, MapConstants.REFERENCE_SOURCE)?.setGeoJson(
            state.referencePoint?.let { FeatureCollection.fromFeature(Feature.fromGeometry(it)) } ?: empty
        )

        source(style, MapConstants.MY_LOCATION_SOURCE)?.setGeoJson(
            state.effectiveLocation?.let { FeatureCollection.fromFeature(Feature.fromGeometry(it)) } ?: empty
        )

        source(style, MapConstants.RADAR_PREVIEW_SOURCE)?.setGeoJson(
            state.radarPreviewCircle?.let { FeatureCollection.fromFeature(Feature.fromGeometry(it)) } ?: empty
        )

        source(style, MapConstants.RADAR_RESULT_SOURCE)?.setGeoJson(
            FeatureCollection.fromFeatures(state.radarResultPolygons.map { Feature.fromGeometry(it) })
        )
    }

    fun release() {
        style = null
    }

    private fun selectedFeature(station: Station): FeatureCollection =
        FeatureCollection.fromFeature(Feature.fromGeometry(station.point()))

    private fun source(style: Style, id: String): GeoJsonSource? =
        style.getSourceAs(id)
}
