package com.hiddenwindy.game

import android.app.Application
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.hiddenwindy.data.Station
import com.hiddenwindy.data.TransitRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.geojson.Point

/** Holds the game state and the actions that mutate it. */
class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TransitRepository(application)
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(application)

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            _state.update { it.copy(myLocation = Point.fromLngLat(loc.longitude, loc.latitude)) }
        }
    }

    private val _state = MutableStateFlow(GameState())
    val state: StateFlow<GameState> = _state.asStateFlow()

    private var eliminatorCounter = 0

    init {
        viewModelScope.launch {
            val stations = withContext(Dispatchers.IO) { repository.loadStations() }
            _state.update { it.copy(stations = stations) }
        }
    }

    fun raw(): TransitRepository = repository

    fun selectStation(station: Station?) {
        _state.update { it.copy(selectedStation = station) }
    }

    /** Selects the station whose marker coordinate matches a rendered-feature hit ([lon]/[lat]). */
    fun selectStationByCoord(lon: Double, lat: Double) {
        val match = _state.value.stations.minByOrNull { haversineMeters(lat, lon, it.lat, it.lon) }
        selectStation(match)
    }

    fun toggleHidingZone() {
        _state.update { it.copy(hidingZoneVisible = !it.hidingZoneVisible) }
    }

    /** Locks the selected station's hiding zone so its clear circle persists after deselection. */
    fun lockHidingZone() {
        val center = _state.value.selectedStation?.point() ?: return
        _state.update { it.copy(lockedZone = center) }
    }

    /** Releases the currently locked hiding zone. */
    fun unlockHidingZone() {
        _state.update { it.copy(lockedZone = null) }
    }

    fun setReferencePoint(point: Point?) {
        _state.update { it.copy(referencePoint = point) }
    }

    /** Adds a directional cut anchored at the active origin (reference point or selected station). */
    fun addDirectionalEliminator(direction: Direction) {
        val origin = _state.value.activeOrigin ?: return
        val eliminator = DirectionalEliminator(
            id = "cut-${eliminatorCounter++}",
            origin = origin,
            direction = direction,
        )
        _state.update { it.copy(eliminators = it.eliminators + eliminator) }
    }

    fun undoLastEliminator() {
        _state.update {
            if (it.eliminators.isEmpty()) it
            else it.copy(eliminators = it.eliminators.dropLast(1))
        }
    }

    fun resetEliminators() {
        _state.update { it.copy(eliminators = emptyList()) }
    }

    /** Clears all game state (overlays, selections, locks) while keeping the station list. */
    fun resetGame() {
        _state.update { GameState(stations = it.stations, myLocation = it.myLocation) }
    }

    /** Toggles the hardcoded Cloud Gate debug location on/off. */
    fun toggleBeanLocation() {
        _state.update { state ->
            if (state.debugLocation != null) {
                state.copy(debugLocation = null)
            } else {
                state.copy(debugLocation = BEAN_LOCATION)
            }
        }
    }

    /** Arms a Radar preview with the given radius. */
    fun setRadarPending(label: String, radiusMiles: Double) {
        _state.update { it.copy(radarPending = RadarPending(label, radiusMiles)) }
    }

    /** Clears the pending Radar preview. */
    fun clearRadarPending() {
        _state.update { it.copy(radarPending = null) }
    }

    companion object {
        /** Cloud Gate ("The Bean"), Millennium Park. */
        private val BEAN_LOCATION = Point.fromLngLat(-87.62332881571997, 41.88270578560553)
    }

    /**
     * Starts receiving device location updates. Call this once the
     * ACCESS_FINE_LOCATION permission has been granted.
     */
    @SuppressWarnings("MissingPermission")
    fun onLocationPermissionGranted() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L)
            .setMinUpdateIntervalMillis(2_000L)
            .build()
        try {
            fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        } catch (_: SecurityException) {
            // Permission was revoked between check and call — safe to ignore
        }
    }

    override fun onCleared() {
        super.onCleared()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    }
}
