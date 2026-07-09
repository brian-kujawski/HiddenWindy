package com.hiddenwindy.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hiddenwindy.game.GameViewModel
import com.hiddenwindy.game.ZoneGeometry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.geojson.Point
import kotlin.coroutines.resume

/**
 * Hosts the MapLibre [MapView] inside Compose. Loads the MapTiler style, wires the transit layers,
 * handles tap (select station) and long-press (set reference point), and re-renders overlays
 * whenever the game state changes.
 */
@Composable
fun MapViewContainer(
    viewModel: GameViewModel,
    modifier: Modifier = Modifier,
) {
    val mapView = rememberMapViewWithLifecycle()
    val controller = remember { MapController() }
    var ready by remember { mutableStateOf(false) }

    AndroidView(factory = { mapView }, modifier = modifier)

    LaunchedEffect(mapView) {
        val map = mapView.awaitMap()
        val bounds = ZoneGeometry.CHICAGO_BOUNDS
        map.cameraPosition = CameraPosition.Builder()
            .target(LatLng(bounds.centerLat, bounds.centerLon))
            .zoom(10.0)
            .build()

        val style = map.awaitStyle(MapConstants.STYLE_URL)
        val linesGeoJson = withContext(Dispatchers.IO) { viewModel.raw().linesGeoJson() }
        val stationsGeoJson = withContext(Dispatchers.IO) { viewModel.raw().stationsGeoJson() }
        controller.setup(
            style = style,
            linesGeoJson = linesGeoJson,
            stationsGeoJson = stationsGeoJson,
        )
        controller.render(viewModel.state.value)
        ready = true

        map.addOnMapClickListener { latLng ->
            viewModel.selectNearestStation(latLng.longitude, latLng.latitude)
            true
        }
        map.addOnMapLongClickListener { latLng ->
            viewModel.setReferencePoint(Point.fromLngLat(latLng.longitude, latLng.latitude))
            true
        }
    }

    LaunchedEffect(ready) {
        if (!ready) return@LaunchedEffect
        viewModel.state.collectLatest { controller.render(it) }
    }

    DisposableEffect(Unit) {
        onDispose { controller.release() }
    }
}

/** Suspends until the [MapLibreMap] is available. */
private suspend fun MapView.awaitMap(): MapLibreMap =
    suspendCancellableCoroutine { cont -> getMapAsync { cont.resume(it) } }

/** Suspends until the [Style] has finished loading from [styleUrl]. */
private suspend fun MapLibreMap.awaitStyle(styleUrl: String): Style =
    suspendCancellableCoroutine { cont -> setStyle(Style.Builder().fromUri(styleUrl)) { cont.resume(it) } }

/** Builds a [MapView] whose native lifecycle is driven by the Compose lifecycle. */
@Composable
private fun rememberMapViewWithLifecycle(): MapView {
    val context = LocalContext.current
    val mapView = remember { MapView(context).apply { onCreate(null) } }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }
    return mapView
}
