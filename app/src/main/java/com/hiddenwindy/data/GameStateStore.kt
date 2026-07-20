package com.hiddenwindy.data

import android.content.Context
import com.hiddenwindy.game.AskedQuestion
import com.hiddenwindy.game.Direction
import com.hiddenwindy.game.DirectionalEliminator
import com.hiddenwindy.game.Eliminator
import com.hiddenwindy.game.GameState
import com.hiddenwindy.game.LineMatchEliminator
import com.hiddenwindy.game.MeasuringCoastlineEliminator
import com.hiddenwindy.game.MeasuringPOIEliminator
import com.hiddenwindy.game.MeasuringWaterEliminator
import com.hiddenwindy.game.PointMatchEliminator
import com.hiddenwindy.game.RadarResult
import com.hiddenwindy.game.TentaclesEliminator
import com.hiddenwindy.game.ThermometerEliminator
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import java.io.File

/** Params for a water eliminator that needs the (large) water distance grid before it can be rebuilt. */
data class PendingWaterEliminator(
    val id: String,
    val featureLabel: String,
    val distanceMiles: Double,
    val closer: Boolean,
)

/** Params for a line-band eliminator (coastline/county/border) that needs its polyline before rebuilding. */
data class PendingLineEliminator(
    val id: String,
    val featureLabel: String,
    val distanceMiles: Double,
    val closer: Boolean,
)

/** A decoded game snapshot, ready to apply to [GameState]. */
data class RestoredGame(
    val eliminators: List<Eliminator>,
    val pendingWater: List<PendingWaterEliminator>,
    val pendingLines: List<PendingLineEliminator>,
    val radarResults: List<RadarResult>,
    val askedQuestions: List<AskedQuestion>,
    val selectedStation: Station?,
    val hidingZoneVisible: Boolean,
    val referencePoint: Point?,
    val lockedZone: Point?,
    val debugLocation: Point?,
    val spoofLocationActive: Boolean,
    val adjustedLocation: Point?,
    val nextEliminatorId: Int,
    val hiderModeEnabled: Boolean,
)

/**
 * Persists committed game progress to a small JSON file so an OS-killed background process doesn't
 * wipe the player's game. Only durable results and map continuity are stored; in-flight question
 * panels (with live countdowns) are not.
 *
 * Reference geometry is NOT written: region maps, the ~50 MB water distance grid, and the border
 * polylines all reload from assets. Eliminators built from those heavy sources (water and the
 * coastline/county/border line bands) persist only their parameters and are re-attached to the
 * reloaded geometry on restore (see GameViewModel). All other eliminators inline their (bounded)
 * geometry so they can be restored independently of asset load order.
 */
class GameStateStore(context: Context) {

    private val file = File(context.filesDir, FILE_NAME)

    /** Writes [state]'s durable fields to disk, replacing any previous snapshot. */
    fun save(state: GameState) {
        val tmp = File(file.parentFile, "$FILE_NAME.tmp")
        tmp.writeText(encode(state).toString())
        if (!tmp.renameTo(file)) {
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
        }
    }

    /** Reads the saved snapshot, or null if there is none / it is unreadable. */
    fun load(): RestoredGame? {
        if (!file.exists()) return null
        return try {
            decode(JSONObject(file.readText()))
        } catch (t: Throwable) {
            // Corrupt or incompatible snapshot: discard and start fresh.
            file.delete()
            null
        }
    }

    /** Deletes the saved snapshot (e.g. when starting a New Game). */
    fun clear() {
        file.delete()
    }

    // ---- encode ----

    private fun encode(state: GameState): JSONObject = JSONObject().apply {
        put("version", VERSION)
        put("eliminators", JSONArray().apply {
            state.eliminators.forEach { encodeEliminator(it)?.let(::put) }
        })
        put("radarResults", JSONArray().apply {
            state.radarResults.forEach { put(encodeRadar(it)) }
        })
        put("askedQuestions", JSONArray().apply {
            state.askedQuestions.forEach { put(encodeAsked(it)) }
        })
        state.selectedStation?.let { put("selectedStation", encodeStation(it)) }
        put("hidingZoneVisible", state.hidingZoneVisible)
        state.referencePoint?.let { put("referencePoint", encodePoint(it)) }
        state.lockedZone?.let { put("lockedZone", encodePoint(it)) }
        state.debugLocation?.let { put("debugLocation", encodePoint(it)) }
        put("spoofLocationActive", state.spoofLocationActive)
        state.adjustedLocation?.let { put("adjustedLocation", encodePoint(it)) }
        put("hiderModeEnabled", state.hiderModeEnabled)
    }

    private fun encodeEliminator(e: Eliminator): JSONObject? = when (e) {
        is DirectionalEliminator -> JSONObject().apply {
            put("type", "DIRECTIONAL")
            put("id", e.id)
            put("origin", encodePoint(e.origin))
            put("direction", e.direction.name)
        }
        is LineMatchEliminator -> JSONObject().apply {
            put("type", "LINE_MATCH")
            put("id", e.id)
            put("lineName", e.lineName)
            put("same", e.same)
            put("regions", encodePolygons(e.regions))
        }
        is PointMatchEliminator -> JSONObject().apply {
            put("type", "POINT_MATCH")
            put("id", e.id)
            put("featureName", e.featureName)
            put("same", e.same)
            put("regions", encodePolygons(e.regions))
        }
        is ThermometerEliminator -> JSONObject().apply {
            put("type", "THERMOMETER")
            put("id", e.id)
            put("start", encodePoint(e.start))
            put("finish", encodePoint(e.finish))
            put("hotter", e.hotter)
        }
        is MeasuringPOIEliminator -> JSONObject().apply {
            put("type", "MEASURING_POI")
            put("id", e.id)
            put("featureLabel", e.featureLabel)
            put("poiLocations", encodePoints(e.poiLocations))
            put("distanceMiles", e.distanceMiles)
            put("closer", e.closer)
        }
        is MeasuringWaterEliminator -> JSONObject().apply {
            put("type", "MEASURING_WATER")
            put("id", e.id)
            put("featureLabel", e.featureLabel)
            put("distanceMiles", e.distanceMiles)
            put("closer", e.closer)
        }
        is MeasuringCoastlineEliminator -> JSONObject().apply {
            put("type", "MEASURING_LINE")
            put("id", e.id)
            put("featureLabel", e.featureLabel)
            put("distanceMiles", e.distanceMiles)
            put("closer", e.closer)
        }
        is TentaclesEliminator -> JSONObject().apply {
            put("type", "TENTACLES")
            put("id", e.id)
            put("featureLabel", e.featureLabel)
            put("seekerLocation", encodePoint(e.seekerLocation))
            put("nearbyPOIs", encodeNamedPoints(e.nearbyPOIs))
            e.chosenPOIName?.let { put("chosenPOIName", it) }
        }
        else -> null
    }

    private fun encodeRadar(r: RadarResult): JSONObject = JSONObject().apply {
        put("id", r.id)
        put("center", encodePoint(r.center))
        put("radiusMiles", r.radiusMiles)
        put("hiderIsInside", r.hiderIsInside)
    }

    private fun encodeAsked(q: AskedQuestion): JSONObject = JSONObject().apply {
        put("category", q.category)
        put("label", q.label)
        put("detail", q.detail)
        q.eliminatorId?.let { put("eliminatorId", it) }
        put("disabled", q.disabled)
    }

    private fun encodeStation(s: Station): JSONObject = JSONObject().apply {
        put("name", s.name)
        put("lon", s.lon)
        put("lat", s.lat)
        put("lines", JSONArray(s.lines))
        put("ada", s.ada)
        s.mapId?.let { put("mapId", it) }
    }

    private fun encodePoint(p: Point): JSONArray =
        JSONArray().put(p.longitude()).put(p.latitude())

    private fun encodePoints(points: List<Point>): JSONArray =
        JSONArray().apply { points.forEach { put(encodePoint(it)) } }

    private fun encodeNamedPoints(points: List<NamedPoint>): JSONArray =
        JSONArray().apply {
            points.forEach {
                put(JSONObject().apply {
                    put("name", it.name)
                    put("location", encodePoint(it.location))
                })
            }
        }

    private fun encodePolygons(polygons: List<Polygon>): JSONArray =
        JSONArray().apply { polygons.forEach { put(encodePolygon(it)) } }

    private fun encodePolygon(polygon: Polygon): JSONArray = JSONArray().apply {
        polygon.coordinates().forEach { ring ->
            put(JSONArray().apply { ring.forEach { put(encodePoint(it)) } })
        }
    }

    // ---- decode ----

    private fun decode(root: JSONObject): RestoredGame {
        val eliminators = ArrayList<Eliminator>()
        val pendingWater = ArrayList<PendingWaterEliminator>()
        val pendingLines = ArrayList<PendingLineEliminator>()
        val elimsArr = root.optJSONArray("eliminators") ?: JSONArray()
        for (i in 0 until elimsArr.length()) {
            val o = elimsArr.getJSONObject(i)
            when (o.getString("type")) {
                "DIRECTIONAL" -> eliminators.add(
                    DirectionalEliminator(
                        id = o.getString("id"),
                        origin = decodePoint(o.getJSONArray("origin")),
                        direction = Direction.valueOf(o.getString("direction")),
                    )
                )
                "LINE_MATCH" -> eliminators.add(
                    LineMatchEliminator(
                        id = o.getString("id"),
                        lineName = o.getString("lineName"),
                        regions = decodePolygons(o.getJSONArray("regions")),
                        same = o.getBoolean("same"),
                    )
                )
                "POINT_MATCH" -> eliminators.add(
                    PointMatchEliminator(
                        id = o.getString("id"),
                        featureName = o.getString("featureName"),
                        regions = decodePolygons(o.getJSONArray("regions")),
                        same = o.getBoolean("same"),
                    )
                )
                "THERMOMETER" -> eliminators.add(
                    ThermometerEliminator(
                        id = o.getString("id"),
                        start = decodePoint(o.getJSONArray("start")),
                        finish = decodePoint(o.getJSONArray("finish")),
                        hotter = o.getBoolean("hotter"),
                    )
                )
                "MEASURING_POI" -> eliminators.add(
                    MeasuringPOIEliminator(
                        id = o.getString("id"),
                        featureLabel = o.getString("featureLabel"),
                        poiLocations = decodePoints(o.getJSONArray("poiLocations")),
                        distanceMiles = o.getDouble("distanceMiles"),
                        closer = o.getBoolean("closer"),
                    )
                )
                "MEASURING_WATER" -> pendingWater.add(
                    PendingWaterEliminator(
                        id = o.getString("id"),
                        featureLabel = o.getString("featureLabel"),
                        distanceMiles = o.getDouble("distanceMiles"),
                        closer = o.getBoolean("closer"),
                    )
                )
                "MEASURING_LINE" -> pendingLines.add(
                    PendingLineEliminator(
                        id = o.getString("id"),
                        featureLabel = o.getString("featureLabel"),
                        distanceMiles = o.getDouble("distanceMiles"),
                        closer = o.getBoolean("closer"),
                    )
                )
                "TENTACLES" -> eliminators.add(
                    TentaclesEliminator(
                        id = o.getString("id"),
                        featureLabel = o.getString("featureLabel"),
                        seekerLocation = decodePoint(o.getJSONArray("seekerLocation")),
                        nearbyPOIs = decodeNamedPoints(o.getJSONArray("nearbyPOIs")),
                        chosenPOIName = if (o.has("chosenPOIName")) o.getString("chosenPOIName") else null,
                    )
                )
            }
        }

        val radarResults = ArrayList<RadarResult>()
        val radarArr = root.optJSONArray("radarResults") ?: JSONArray()
        for (i in 0 until radarArr.length()) {
            val o = radarArr.getJSONObject(i)
            radarResults.add(
                RadarResult(
                    id = o.getString("id"),
                    center = decodePoint(o.getJSONArray("center")),
                    radiusMiles = o.getDouble("radiusMiles"),
                    hiderIsInside = o.getBoolean("hiderIsInside"),
                )
            )
        }

        val asked = ArrayList<AskedQuestion>()
        val askedArr = root.optJSONArray("askedQuestions") ?: JSONArray()
        for (i in 0 until askedArr.length()) {
            val o = askedArr.getJSONObject(i)
            asked.add(
                AskedQuestion(
                    category = o.getString("category"),
                    label = o.getString("label"),
                    detail = o.getString("detail"),
                    eliminatorId = if (o.has("eliminatorId")) o.getString("eliminatorId") else null,
                    disabled = o.optBoolean("disabled", false),
                )
            )
        }

        val allIds = eliminators.map { it.id } +
            pendingWater.map { it.id } +
            pendingLines.map { it.id } +
            radarResults.map { it.id }
        val nextId = (allIds.mapNotNull { parseIdSuffix(it) }.maxOrNull() ?: -1) + 1

        return RestoredGame(
            eliminators = eliminators,
            pendingWater = pendingWater,
            pendingLines = pendingLines,
            radarResults = radarResults,
            askedQuestions = asked,
            selectedStation = root.optJSONObject("selectedStation")?.let { decodeStation(it) },
            hidingZoneVisible = root.optBoolean("hidingZoneVisible", false),
            referencePoint = root.optJSONArray("referencePoint")?.let { decodePoint(it) },
            lockedZone = root.optJSONArray("lockedZone")?.let { decodePoint(it) },
            debugLocation = root.optJSONArray("debugLocation")?.let { decodePoint(it) },
            spoofLocationActive = root.optBoolean("spoofLocationActive", false),
            adjustedLocation = root.optJSONArray("adjustedLocation")?.let { decodePoint(it) },
            nextEliminatorId = nextId,
            hiderModeEnabled = root.optBoolean("hiderModeEnabled", false),
        )
    }

    private fun decodeStation(o: JSONObject): Station {
        val linesArr = o.optJSONArray("lines") ?: JSONArray()
        val lines = ArrayList<String>(linesArr.length())
        for (i in 0 until linesArr.length()) lines.add(linesArr.getString(i))
        return Station(
            name = o.getString("name"),
            lon = o.getDouble("lon"),
            lat = o.getDouble("lat"),
            lines = lines,
            ada = o.optBoolean("ada", false),
            mapId = if (o.has("mapId")) o.getString("mapId") else null,
        )
    }

    private fun decodePoint(a: JSONArray): Point =
        Point.fromLngLat(a.getDouble(0), a.getDouble(1))

    private fun decodePoints(a: JSONArray): List<Point> =
        ArrayList<Point>(a.length()).apply {
            for (i in 0 until a.length()) add(decodePoint(a.getJSONArray(i)))
        }

    private fun decodeNamedPoints(a: JSONArray): List<NamedPoint> =
        ArrayList<NamedPoint>(a.length()).apply {
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                add(NamedPoint(o.getString("name"), decodePoint(o.getJSONArray("location"))))
            }
        }

    private fun decodePolygons(a: JSONArray): List<Polygon> =
        ArrayList<Polygon>(a.length()).apply {
            for (i in 0 until a.length()) add(decodePolygon(a.getJSONArray(i)))
        }

    private fun decodePolygon(a: JSONArray): Polygon {
        val rings = ArrayList<List<Point>>(a.length())
        for (i in 0 until a.length()) {
            val ringArr = a.getJSONArray(i)
            val ring = ArrayList<Point>(ringArr.length())
            for (j in 0 until ringArr.length()) ring.add(decodePoint(ringArr.getJSONArray(j)))
            rings.add(ring)
        }
        return Polygon.fromLngLats(rings)
    }

    /** The trailing integer of an eliminator id like "measuring-poi-7", or null. */
    private fun parseIdSuffix(id: String): Int? =
        id.substringAfterLast('-', "").toIntOrNull()

    companion object {
        private const val FILE_NAME = "game_state.json"
        private const val VERSION = 1
    }
}
