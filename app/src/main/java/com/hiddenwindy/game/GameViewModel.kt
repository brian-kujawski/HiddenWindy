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
import com.hiddenwindy.data.LineRegionsRepository
import com.hiddenwindy.data.NamedPoint
import com.hiddenwindy.data.PointRegionsRepository
import com.hiddenwindy.data.Station
import com.hiddenwindy.data.TransitRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import org.maplibre.turf.TurfConstants
import org.maplibre.turf.TurfMeasurement

/** Holds the game state and the actions that mutate it. */
class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TransitRepository(application)
    private val lineRegionsRepository = LineRegionsRepository(application)
    private val pointRegionsRepository = PointRegionsRepository(application)
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(application)
    private var timerJob: Job? = null
    private var thermometerTimerJob: Job? = null
    private var matchingTimerJob: Job? = null

    private var lineRegions: Map<String, List<Polygon>> = emptyMap()
    private var linesByName: Map<String, List<LineString>> = emptyMap()
    private var airportRegions: Map<String, List<Polygon>> = emptyMap()
    private var airportPoints: List<NamedPoint> = emptyList()

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
        viewModelScope.launch {
            lineRegions = withContext(Dispatchers.IO) { lineRegionsRepository.loadRegions() }
            linesByName = withContext(Dispatchers.IO) { lineRegionsRepository.loadLinesByName() }
        }
        viewModelScope.launch {
            airportRegions = withContext(Dispatchers.IO) { pointRegionsRepository.loadRegions("transit_airport") }
            airportPoints = withContext(Dispatchers.IO) { pointRegionsRepository.loadNamedPoints("transit_airport") }
        }
    }

    fun raw(): TransitRepository = repository

    /** The line nearest to the current (real or debug) location, or null if unknown. */
    fun nearestLineToMe(): String? {
        val loc = _state.value.effectiveLocation ?: return null
        if (linesByName.isEmpty()) return null
        return lineRegionsRepository.nearestLineName(loc, linesByName)
    }

    /**
     * Answers the Matching question for the line nearest to my location. [same] = true shades
     * everything outside that line's region; false shades the region itself.
     */
    fun confirmMatching(dataset: String, featureLabel: String, seekerNearestName: String) {
        _state.update { state ->
            state.copy(
                matchingConfirmed = MatchingConfirmed(
                    dataset = dataset,
                    featureLabel = featureLabel,
                    seekerNearestName = seekerNearestName,
                )
            )
        }
    }

    /** Starts the 5-minute countdown after the matching question has been sent to the hider. */
    fun startMatchingTimer() {
        if (_state.value.matchingConfirmed?.timerStarted == true) return
        _state.update { state ->
            state.copy(matchingConfirmed = state.matchingConfirmed?.copy(timerStarted = true))
        }
        matchingTimerJob = viewModelScope.launch {
            while (true) {
                delay(1_000)
                val remaining = _state.value.matchingConfirmed?.remainingSeconds ?: break
                if (remaining <= 0) break
                _state.update { state ->
                    state.copy(matchingConfirmed = state.matchingConfirmed?.copy(remainingSeconds = remaining - 1))
                }
            }
        }
    }

    /** Resolves the Matching question with the hider's reply. */
    fun resolveMatching(same: Boolean) {
        val confirmed = _state.value.matchingConfirmed ?: return
        val name = confirmed.seekerNearestName
        val regions = when (confirmed.dataset) {
            "line_regions" -> lineRegions[name]
            "transit_airport" -> airportRegions[name]
            else -> null
        } ?: return
        matchingTimerJob?.cancel()
        matchingTimerJob = null
        val eliminator = PointMatchEliminator(
            id = "match-${eliminatorCounter++}",
            featureName = name,
            regions = regions,
            same = same,
        )
        val record = AskedQuestion(
            category = QuestionCategory.MATCHING,
            label = "Matching",
            detail = confirmed.featureLabel,
        )
        _state.update {
            it.copy(
                matchingConfirmed = null,
                eliminators = it.eliminators + eliminator,
                askedQuestions = it.askedQuestions + record,
            )
        }
    }

    /** Returns the name of the nearest commercial airport to the current location, or null. */
    fun nearestAirportToMe(): String? {
        val loc = _state.value.effectiveLocation ?: return null
        if (airportPoints.isEmpty()) return null
        return pointRegionsRepository.nearestName(loc, airportPoints)
    }

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
        timerJob?.cancel()
        timerJob = null
        thermometerTimerJob?.cancel()
        thermometerTimerJob = null
        matchingTimerJob?.cancel()
        matchingTimerJob = null
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

    /** Toggles spoof-location mode. Turning it off clears the spoofed location. */
    fun toggleSpoofLocation() {
        _state.update { state ->
            if (state.spoofLocationActive) {
                state.copy(spoofLocationActive = false, debugLocation = null)
            } else {
                state.copy(spoofLocationActive = true)
            }
        }
    }

    /** Sets the spoofed location from a long-press when spoof mode is active. */
    fun setSpoofLocation(point: Point) {
        _state.update { it.copy(debugLocation = point) }
    }

    /** Arms a Radar preview with the given radius. */
    fun setRadarPending(label: String, radiusMiles: Double) {
        _state.update { it.copy(radarPending = RadarPending(label, radiusMiles)) }
    }

    /** Clears the pending Radar preview. */
    fun clearRadarPending() {
        _state.update { it.copy(radarPending = null) }
    }

    /** Arms a Thermometer reading with the given target distance. */
    fun setThermometerPending(label: String, distanceMiles: Double) {
        _state.update { it.copy(thermometerPending = ThermometerPending(label, distanceMiles)) }
    }

    /** Clears the pending Thermometer selection. */
    fun clearThermometerPending() {
        _state.update { it.copy(thermometerPending = null) }
    }

    /** Begins the Thermometer journey: captures the current location as the start point. */
    fun startThermometer() {
        val pending = _state.value.thermometerPending ?: return
        val start = _state.value.effectiveLocation ?: return
        _state.update {
            it.copy(
                thermometerPending = null,
                thermometerActive = ThermometerActive(
                    label = pending.label,
                    distanceMiles = pending.distanceMiles,
                    start = start,
                ),
            )
        }
    }

    /** Ends the journey: captures the current location as the finish point, awaiting the answer. */
    fun finishThermometer() {
        val active = _state.value.thermometerActive ?: return
        if (active.finish != null) return
        val finish = _state.value.effectiveLocation ?: return
        val traveledMiles = TurfMeasurement.distance(active.start, finish, TurfConstants.UNIT_MILES)
        _state.update {
            it.copy(
                thermometerActive = active.copy(
                    finish = finish,
                    finishedMiles = traveledMiles,
                    finishedAt = System.currentTimeMillis(),
                )
            )
        }
    }

    /** Cancels any in-progress Thermometer reading. */
    fun cancelThermometer() {
        thermometerTimerJob?.cancel()
        thermometerTimerJob = null
        _state.update { it.copy(thermometerPending = null, thermometerActive = null) }
    }

    /** Starts the 5-minute countdown after the finish message has been sent to the hider. */
    fun startThermometerTimer() {
        if (_state.value.thermometerActive?.timerStarted == true) return
        _state.update { state ->
            state.copy(thermometerActive = state.thermometerActive?.copy(timerStarted = true))
        }
        thermometerTimerJob = viewModelScope.launch {
            while (true) {
                delay(1_000)
                val remaining = _state.value.thermometerActive?.remainingSeconds ?: break
                if (remaining <= 0) break
                _state.update { state ->
                    state.copy(thermometerActive = state.thermometerActive?.copy(remainingSeconds = remaining - 1))
                }
            }
        }
    }

    /**
     * Resolves the Thermometer reading. [hotter] = true means the hider got closer, so the far
     * (start) side of the path's perpendicular bisector is out of play; false shades the finish side.
     */
    fun resolveThermometer(hotter: Boolean) {
        val active = _state.value.thermometerActive ?: return
        val finish = active.finish ?: return
        val eliminator = ThermometerEliminator(
            id = "thermo-${eliminatorCounter++}",
            start = active.start,
            finish = finish,
            hotter = hotter,
        )
        val record = AskedQuestion(
            category = QuestionCategory.THERMOMETER,
            label = "Thermometer",
            detail = "${active.label}, ${if (hotter) "hotter" else "colder"}",
        )
        _state.update {
            it.copy(
                thermometerActive = null,
                eliminators = it.eliminators + eliminator,
                askedQuestions = it.askedQuestions + record,
            )
        }
    }

    /**
     * Confirms the pending radar cast: captures the current location as the cast centre,
     * clears the preview, and arms the 5-minute countdown.
     */
    fun confirmRadar() {
        val pending = _state.value.radarPending ?: return
        val center = _state.value.effectiveLocation ?: return
        val record = AskedQuestion(
            category = QuestionCategory.RADAR,
            label = "Radar",
            detail = "${pending.label} mi",
        )
        _state.update { state ->
            state.copy(
                radarPending = null,
                radarConfirmed = RadarConfirmed(
                    label = pending.label,
                    radiusMiles = pending.radiusMiles,
                    center = center,
                ),
                askedQuestions = state.askedQuestions + record,
            )
        }
    }

    /** Starts the radar countdown. No-op if already running. */
    fun startRadarTimer() {
        if (_state.value.radarConfirmed?.timerStarted == true) return
        _state.update { state ->
            state.copy(radarConfirmed = state.radarConfirmed?.copy(timerStarted = true))
        }
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1_000)
                val remaining = _state.value.radarConfirmed?.remainingSeconds ?: break
                if (remaining <= 0) break
                _state.update { state ->
                    state.copy(radarConfirmed = state.radarConfirmed?.copy(remainingSeconds = remaining - 1))
                }
            }
        }
    }

    /**
     * Resolves the radar cast. Creates a permanent [RadarResult] on the map and clears the
     * confirmation state. [hiderIsInside] = true shades everything outside the circle;
     * false shades the circle itself (excluded zone).
     */
    fun resolveRadar(hiderIsInside: Boolean) {
        timerJob?.cancel()
        timerJob = null
        val confirmed = _state.value.radarConfirmed ?: return
        val result = RadarResult(
            center = confirmed.center,
            radiusMiles = confirmed.radiusMiles,
            hiderIsInside = hiderIsInside,
        )
        _state.update { state ->
            state.copy(
                radarConfirmed = null,
                radarResults = state.radarResults + result,
            )
        }
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
        timerJob?.cancel()
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
