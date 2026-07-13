package com.hiddenwindy.map

import com.hiddenwindy.data.Station
import com.hiddenwindy.game.GameState
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
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
        style.addSource(GeoJsonSource(MapConstants.POI_MARKERS_SOURCE, empty))
        style.addSource(GeoJsonSource(MapConstants.ADJUST_LINE_SOURCE, empty))
        style.addSource(GeoJsonSource(MapConstants.ADJUST_TARGET_SOURCE, empty))

        // Transit lines (data-driven colour from the "color" property).
        style.addLayer(
            LineLayer(MapConstants.LINES_LAYER, MapConstants.LINES_SOURCE).withProperties(
                PropertyFactory.lineColor(Expression.get("color")),
                PropertyFactory.lineWidth(3.0f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            )
        )

        // Stations — added BEFORE the eliminator shading so covered markers are visually darkened
        // by the semi-transparent fill rather than sitting on top of it.
        style.addLayer(
            CircleLayer(MapConstants.STATIONS_LAYER, MapConstants.STATIONS_SOURCE).withProperties(
                PropertyFactory.circleRadius(4.0f),
                PropertyFactory.circleColor("#FFFFFF"),
                PropertyFactory.circleStrokeColor("#37474F"),
                PropertyFactory.circleStrokeWidth(1.5f),
            )
        )

        // "Out of play" shading — rendered above station markers so eliminated stations appear
        // darkened through the translucent fill.
        style.addLayer(
            FillLayer(MapConstants.ELIMINATOR_FILL_LAYER, MapConstants.ELIMINATOR_SOURCE).withProperties(
                PropertyFactory.fillColor("#424242"),
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

        // Permanent radar results — dark gray, higher opacity than directional cuts.
        style.addLayer(
            FillLayer(MapConstants.RADAR_RESULT_FILL_LAYER, MapConstants.RADAR_RESULT_SOURCE).withProperties(
                PropertyFactory.fillColor("#424242"),
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

        // POI layer markers — shown when user activates a dataset from the Layers picker.
        // Circles always visible; labels only at zoom >= 13 to avoid clutter.
        style.addLayer(
            CircleLayer(MapConstants.POI_MARKERS_CIRCLE_LAYER, MapConstants.POI_MARKERS_SOURCE).withProperties(
                PropertyFactory.circleRadius(6.0f),
                PropertyFactory.circleColor("#FFC107"),
                PropertyFactory.circleStrokeColor("#FFFFFF"),
                PropertyFactory.circleStrokeWidth(1.5f),
            )
        )
        style.addLayer(
            SymbolLayer(MapConstants.POI_MARKERS_LABEL_LAYER, MapConstants.POI_MARKERS_SOURCE).withProperties(
                PropertyFactory.textField(Expression.get("name")),
                PropertyFactory.textSize(11f),
                PropertyFactory.textAnchor(Property.TEXT_ANCHOR_TOP),
                PropertyFactory.textOffset(arrayOf(0f, 0.6f)),
                PropertyFactory.textColor("#212121"),
                PropertyFactory.textHaloColor("#FFFFFF"),
                PropertyFactory.textHaloWidth(1.5f),
            ).withFilter(Expression.gte(Expression.zoom(), Expression.literal(13)))
        )

        // Adjust drift connector — dashed line from the GPS icon to the target.
        style.addLayer(
            LineLayer(MapConstants.ADJUST_LINE_LAYER, MapConstants.ADJUST_LINE_SOURCE).withProperties(
                PropertyFactory.lineColor("#000000"),
                PropertyFactory.lineWidth(2.0f),
                PropertyFactory.lineDasharray(arrayOf(2.0f, 2.0f)),
            )
        )

        // Adjust drift target — crosshair ring plus a small centre dot.
        style.addLayer(
            CircleLayer(MapConstants.ADJUST_TARGET_RING_LAYER, MapConstants.ADJUST_TARGET_SOURCE).withProperties(
                PropertyFactory.circleRadius(14.0f),
                PropertyFactory.circleOpacity(0.0f),
                PropertyFactory.circleStrokeColor("#000000"),
                PropertyFactory.circleStrokeWidth(3.0f),
            )
        )
        style.addLayer(
            CircleLayer(MapConstants.ADJUST_TARGET_DOT_LAYER, MapConstants.ADJUST_TARGET_SOURCE).withProperties(
                PropertyFactory.circleRadius(3.0f),
                PropertyFactory.circleColor("#000000"),
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

        // Dissolve every exclusion (rules + radar) into non-overlapping geometry so a translucent
        // fill renders as one flat shade instead of stacking darker where questions overlap.
        val outFeatures = PolygonUnion.dissolve(state.allOutRegions()).map { Feature.fromGeometry(it) }
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

        // Radar results are folded into the unified exclusion shading above, so this legacy layer
        // stays empty to avoid a second translucent pass that would stack opacity.
        source(style, MapConstants.RADAR_RESULT_SOURCE)?.setGeoJson(empty)

        // POI layer markers for the active dataset, or empty when no layer is selected.
        val poiFeatures = state.activePOILayer?.points?.map { namedPoint ->
            Feature.fromGeometry(namedPoint.location).also { f ->
                f.addStringProperty("name", namedPoint.name)
            }
        } ?: emptyList()
        source(style, MapConstants.POI_MARKERS_SOURCE)?.setGeoJson(
            FeatureCollection.fromFeatures(poiFeatures)
        )

        source(style, MapConstants.ADJUST_LINE_SOURCE)?.setGeoJson(
            state.adjustLine?.let { FeatureCollection.fromFeature(Feature.fromGeometry(it)) } ?: empty
        )
        source(style, MapConstants.ADJUST_TARGET_SOURCE)?.setGeoJson(
            state.adjustTargetPoint?.let { FeatureCollection.fromFeature(Feature.fromGeometry(it)) } ?: empty
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
