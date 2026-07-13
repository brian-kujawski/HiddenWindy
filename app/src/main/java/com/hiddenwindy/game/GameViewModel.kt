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
    private var measuringPOITimerJob: Job? = null

    private var lineRegions: Map<String, List<Polygon>> = emptyMap()
    private var linesByName: Map<String, List<LineString>> = emptyMap()
    private var airportRegions: Map<String, List<Polygon>> = emptyMap()
    private var airportPoints: List<NamedPoint> = emptyList()
    private var parkRegions: Map<String, List<Polygon>> = emptyMap()
    private var parkPoints: List<NamedPoint> = emptyList()
    private var stationNameLengthRegions: Map<String, List<Polygon>> = emptyMap()
    private var amusementParkRegions: Map<String, List<Polygon>> = emptyMap()
    private var amusementParkPoints: List<NamedPoint> = emptyList()
    private var zooRegions: Map<String, List<Polygon>> = emptyMap()
    private var zooPoints: List<NamedPoint> = emptyList()
    private var aquariumRegions: Map<String, List<Polygon>> = emptyMap()
    private var aquariumPoints: List<NamedPoint> = emptyList()
    private var museumRegions: Map<String, List<Polygon>> = emptyMap()
    private var museumPoints: List<NamedPoint> = emptyList()
    private var golfCourseRegions: Map<String, List<Polygon>> = emptyMap()
    private var golfCoursePoints: List<NamedPoint> = emptyList()
    private var hospitalRegions: Map<String, List<Polygon>> = emptyMap()
    private var hospitalPoints: List<NamedPoint> = emptyList()
    private var consulateRegions: Map<String, List<Polygon>> = emptyMap()
    private var consulatePoints: List<NamedPoint> = emptyList()
    private var libraryRegions: Map<String, List<Polygon>> = emptyMap()
    private var libraryPoints: List<NamedPoint> = emptyList()
    private var movieTheaterRegions: Map<String, List<Polygon>> = emptyMap()
    private var movieTheaterPoints: List<NamedPoint> = emptyList()

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
        viewModelScope.launch {
            parkRegions = withContext(Dispatchers.IO) { pointRegionsRepository.loadRegions("natural_park") }
            parkPoints = withContext(Dispatchers.IO) { pointRegionsRepository.loadNamedPoints("natural_park") }
        }
        viewModelScope.launch {
            stationNameLengthRegions = withContext(Dispatchers.IO) { pointRegionsRepository.loadRegions("station_name_length") }
        }
        viewModelScope.launch {
            amusementParkRegions = withContext(Dispatchers.IO) { pointRegionsRepository.loadRegions("poi-amusementparks") }
            amusementParkPoints = withContext(Dispatchers.IO) { pointRegionsRepository.loadNamedPoints("poi-amusementparks") }
        }
        viewModelScope.launch {
            zooRegions = withContext(Dispatchers.IO) { pointRegionsRepository.loadRegions("poi-zoo") }
            zooPoints = withContext(Dispatchers.IO) { pointRegionsRepository.loadNamedPoints("poi-zoo") }
        }
        viewModelScope.launch {
            aquariumRegions = withContext(Dispatchers.IO) { pointRegionsRepository.loadRegions("poi-aquarium") }
            aquariumPoints = withContext(Dispatchers.IO) { pointRegionsRepository.loadNamedPoints("poi-aquarium") }
        }
        viewModelScope.launch {
            museumRegions = withContext(Dispatchers.IO) { pointRegionsRepository.loadRegions("poi-museum") }
            museumPoints = withContext(Dispatchers.IO) { pointRegionsRepository.loadNamedPoints("poi-museum") }
        }
        viewModelScope.launch {
            golfCourseRegions = withContext(Dispatchers.IO) { pointRegionsRepository.loadRegions("poi-golfcourse") }
            golfCoursePoints = withContext(Dispatchers.IO) { pointRegionsRepository.loadNamedPoints("poi-golfcourse") }
        }
        viewModelScope.launch {
            hospitalRegions = withContext(Dispatchers.IO) { pointRegionsRepository.loadRegions("utilities-hospital") }
            hospitalPoints = withContext(Dispatchers.IO) { pointRegionsRepository.loadNamedPoints("utilities-hospital") }
        }
        viewModelScope.launch {
            consulateRegions = withContext(Dispatchers.IO) { pointRegionsRepository.loadRegions("utilities-consulates") }
            consulatePoints = withContext(Dispatchers.IO) { pointRegionsRepository.loadNamedPoints("utilities-consulates") }
        }
        viewModelScope.launch {
            libraryRegions = withContext(Dispatchers.IO) { pointRegionsRepository.loadRegions("utilities-library") }
            libraryPoints = withContext(Dispatchers.IO) { pointRegionsRepository.loadNamedPoints("utilities-library") }
        }
        viewModelScope.launch {
            movieTheaterRegions = withContext(Dispatchers.IO) { pointRegionsRepository.loadRegions("poi-movietheater") }
            movieTheaterPoints = withContext(Dispatchers.IO) { pointRegionsRepository.loadNamedPoints("poi-movietheater") }
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
    fun confirmMatching(dataset: String, featureLabel: String, seekerNearestName: String, seekerNearestStationName: String? = null) {
        _state.update { state ->
            state.copy(
                matchingConfirmed = MatchingConfirmed(
                    dataset = dataset,
                    featureLabel = featureLabel,
                    seekerNearestName = seekerNearestName,
                    seekerNearestStationName = seekerNearestStationName,
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
            "natural_park" -> parkRegions[name]
            "station_name_length" -> stationNameLengthRegions[name]
            "poi-amusementparks" -> amusementParkRegions[name]
            "poi-zoo" -> zooRegions[name]
            "poi-aquarium" -> aquariumRegions[name]
            "poi-museum" -> museumRegions[name]
            "poi-golfcourse" -> golfCourseRegions[name]
            "utilities-hospital" -> hospitalRegions[name]
            "utilities-consulates" -> consulateRegions[name]
            "utilities-library" -> libraryRegions[name]
            "poi-movietheater" -> movieTheaterRegions[name]
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

    /** Returns the name of the nearest park to the current location, or null. */
    fun nearestParkToMe(): String? {
        val loc = _state.value.effectiveLocation ?: return null
        if (parkPoints.isEmpty()) return null
        return pointRegionsRepository.nearestName(loc, parkPoints)
    }

    /** Returns the alphanumeric character count of the nearest station's name, or null. */
    fun nearestStationNameLengthToMe(): String? {
        val loc = _state.value.effectiveLocation ?: return null
        val stations = _state.value.stations
        if (stations.isEmpty()) return null
        val nearest = stations.minByOrNull { haversineMeters(loc.latitude(), loc.longitude(), it.lat, it.lon) } ?: return null
        return nearest.name.count { it.isLetterOrDigit() }.toString()
    }

    /** Returns the name of the nearest station to the current location, or null. */
    fun nearestStationNameToMe(): String? {
        val loc = _state.value.effectiveLocation ?: return null
        val stations = _state.value.stations
        if (stations.isEmpty()) return null
        return stations.minByOrNull { haversineMeters(loc.latitude(), loc.longitude(), it.lat, it.lon) }?.name
    }

    /** Returns the name of the nearest amusement park to the current location, or null. */
    fun nearestAmusementParkToMe(): String? {
        val loc = _state.value.effectiveLocation ?: return null
        if (amusementParkPoints.isEmpty()) return null
        return pointRegionsRepository.nearestName(loc, amusementParkPoints)
    }

    /** Returns the name of the nearest zoo to the current location, or null. */
    fun nearestZooToMe(): String? {
        val loc = _state.value.effectiveLocation ?: return null
        if (zooPoints.isEmpty()) return null
        return pointRegionsRepository.nearestName(loc, zooPoints)
    }

    /** Returns the name of the nearest aquarium to the current location, or null. */
    fun nearestAquariumToMe(): String? {
        val loc = _state.value.effectiveLocation ?: return null
        if (aquariumPoints.isEmpty()) return null
        return pointRegionsRepository.nearestName(loc, aquariumPoints)
    }

    /** Returns the name of the nearest museum to the current location, or null. */
    fun nearestMuseumToMe(): String? {
        val loc = _state.value.effectiveLocation ?: return null
        if (museumPoints.isEmpty()) return null
        return pointRegionsRepository.nearestName(loc, museumPoints)
    }

    /** Returns the name of the nearest golf course to the current location, or null. */
    fun nearestGolfCourseToMe(): String? {
        val loc = _state.value.effectiveLocation ?: return null
        if (golfCoursePoints.isEmpty()) return null
        return pointRegionsRepository.nearestName(loc, golfCoursePoints)
    }

    /** Returns the name of the nearest hospital to the current location, or null. */
    fun nearestHospitalToMe(): String? {
        val loc = _state.value.effectiveLocation ?: return null
        if (hospitalPoints.isEmpty()) return null
        return pointRegionsRepository.nearestName(loc, hospitalPoints)
    }

    /** Returns the name of the nearest foreign consulate to the current location, or null. */
    fun nearestConsulateToMe(): String? {
        val loc = _state.value.effectiveLocation ?: return null
        if (consulatePoints.isEmpty()) return null
        return pointRegionsRepository.nearestName(loc, consulatePoints)
    }

    /** Returns the name of the nearest library to the current location, or null. */
    fun nearestLibraryToMe(): String? {
        val loc = _state.value.effectiveLocation ?: return null
        if (libraryPoints.isEmpty()) return null
        return pointRegionsRepository.nearestName(loc, libraryPoints)
    }

    /** Returns the name of the nearest movie theater to the current location, or null. */
    fun nearestMovieTheaterToMe(): String? {
        val loc = _state.value.effectiveLocation ?: return null
        if (movieTheaterPoints.isEmpty()) return null
        return pointRegionsRepository.nearestName(loc, movieTheaterPoints)
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
        measuringPOITimerJob?.cancel()
        measuringPOITimerJob = null
        _state.update { GameState(stations = it.stations, myLocation = it.myLocation) }
    }

    /** Shows the given dataset's points as map markers. Toggling the same dataset clears it. */
    fun showPOILayer(dataset: String, displayName: String) {
        val points = datasetPoints(dataset)
        if (points.isEmpty()) return
        val current = _state.value.activePOILayer
        if (current?.dataset == dataset) {
            _state.update { it.copy(activePOILayer = null) }
        } else {
            _state.update { it.copy(activePOILayer = POILayerState(dataset, displayName, points)) }
        }
    }

    /** Clears any active POI layer markers from the map. */
    fun clearPOILayer() {
        _state.update { it.copy(activePOILayer = null) }
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

    /** Returns the nearest POI distance in miles for the given dataset, or null if location unknown. */
    fun nearestPointDistanceMiles(dataset: String): Double? {
        val loc = _state.value.effectiveLocation ?: return null
        val points = datasetPoints(dataset)
        if (points.isEmpty()) return null
        return points.minOfOrNull { TurfMeasurement.distance(loc, it.location, TurfConstants.UNIT_MILES) }
    }

    private fun datasetPoints(dataset: String): List<NamedPoint> = when (dataset) {
        "transit_airport" -> airportPoints
        "natural_park" -> parkPoints
        "poi-amusementparks" -> amusementParkPoints
        "poi-zoo" -> zooPoints
        "poi-aquarium" -> aquariumPoints
        "poi-golfcourse" -> golfCoursePoints
        "poi-museum" -> museumPoints
        "poi-movietheater" -> movieTheaterPoints
        "utilities-hospital" -> hospitalPoints
        "utilities-library" -> libraryPoints
        "utilities-consulates" -> consulatePoints
        else -> emptyList()
    }

    /** Confirms a Measuring POI question: computes the seeker's nearest POI and stores state. */
    fun confirmMeasuringPOI(featureLabel: String, dataset: String) {
        val loc = _state.value.effectiveLocation ?: return
        val points = datasetPoints(dataset)
        if (points.isEmpty()) return
        val nearest = points.minByOrNull { TurfMeasurement.distance(loc, it.location, TurfConstants.UNIT_MILES) } ?: return
        val distanceMiles = TurfMeasurement.distance(loc, nearest.location, TurfConstants.UNIT_MILES)
        _state.update { state ->
            state.copy(
                measuringPOIConfirmed = MeasuringPOIConfirmed(
                    featureLabel = featureLabel,
                    nearestName = nearest.name,
                    distanceMiles = distanceMiles,
                    poiLocations = points.map { it.location },
                )
            )
        }
    }

    /** Starts the 5-minute countdown after the measuring question has been sent to the hider. */
    fun startMeasuringPOITimer() {
        if (_state.value.measuringPOIConfirmed?.timerStarted == true) return
        _state.update { state ->
            state.copy(measuringPOIConfirmed = state.measuringPOIConfirmed?.copy(timerStarted = true))
        }
        measuringPOITimerJob = viewModelScope.launch {
            while (true) {
                delay(1_000)
                val remaining = _state.value.measuringPOIConfirmed?.remainingSeconds ?: break
                if (remaining <= 0) break
                _state.update { state ->
                    state.copy(measuringPOIConfirmed = state.measuringPOIConfirmed?.copy(remainingSeconds = remaining - 1))
                }
            }
        }
    }

    /** Resolves the Measuring POI question. [closer] = true means the hider is closer to their nearest POI. */
    fun resolveMeasuringPOI(closer: Boolean) {
        measuringPOITimerJob?.cancel()
        measuringPOITimerJob = null
        val confirmed = _state.value.measuringPOIConfirmed ?: return
        val eliminator = MeasuringPOIEliminator(
            id = "measuring-poi-${eliminatorCounter++}",
            featureLabel = confirmed.featureLabel,
            poiLocations = confirmed.poiLocations,
            distanceMiles = confirmed.distanceMiles,
            closer = closer,
        )
        val record = AskedQuestion(
            category = QuestionCategory.MEASURING,
            label = "Measuring",
            detail = confirmed.featureLabel,
        )
        _state.update {
            it.copy(
                measuringPOIConfirmed = null,
                eliminators = it.eliminators + eliminator,
                askedQuestions = it.askedQuestions + record,
            )
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
