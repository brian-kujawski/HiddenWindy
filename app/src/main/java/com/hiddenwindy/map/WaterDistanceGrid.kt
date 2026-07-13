package com.hiddenwindy.map

import android.content.Context
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import kotlin.math.roundToLong

/**
 * A precomputed distance-to-nearest-water field over the play bounds, plus a marching-squares
 * contour extractor that turns a distance threshold into isodistance region polygons.
 *
 * This exists to keep the "Body of Water" Measuring question fast. Buffering the ~27k-vertex water
 * geometry with JTS at runtime overran Android's ANR watchdog; thresholding this grid is a linear
 * scan plus a light contour trace, which runs in milliseconds. Accuracy is bounded only by the
 * offline cell size (see tools/build_water_distance_grid.py).
 *
 * The contourer emits VALID polygons-with-holes for whichever polarity the caller wants to shade
 * (near-water or far-from-water). Emitting the shaded region directly - instead of inverting a
 * "within" region via a rectangle-with-holes mask - avoids the even-odd / ring-nesting bug that
 * otherwise re-fills far-from-water pockets and inverts the result.
 *
 * Asset format (assets/natural_water_distance.bin):
 *   line 1 ASCII: "WDIST1 <cols> <rows> <west> <south> <east> <north> <cellMeters> <maxMeters>\n"
 *   then rows*cols little-endian uint16 distances in metres, row-major, NORTH row first.
 */
class WaterDistanceGrid private constructor(
    private val cols: Int,
    private val rows: Int,
    private val west: Double,
    private val south: Double,
    private val east: Double,
    private val north: Double,
    private val maxMeters: Int,
    /** Distances in metres, row-major, row 0 = north. Length = rows*cols. */
    private val dist: IntArray,
) {
    private val lonStep = (east - west) / cols
    private val latStep = (north - south) / rows

    /** Longitude of the centre of column [c] (0-based). */
    private fun lonAt(c: Int): Double = west + (c + 0.5) * lonStep

    /** Latitude of the centre of row [r] (0-based, row 0 = north). */
    private fun latAt(r: Int): Double = north - (r + 0.5) * latStep

    /** Distance at cell (r,c); cells outside the grid count as "not inside the region". */
    private fun distAt(r: Int, c: Int): Int? {
        if (r < 0 || c < 0 || r >= rows || c >= cols) return null
        return dist[r * cols + c]
    }

    private fun clampLon(x: Double): Double = x.coerceIn(west, east)
    private fun clampLat(y: Double): Double = y.coerceIn(south, north)

    /**
     * Region {distance <= thresholdMeters} as valid polygons-with-holes ("within X of water").
     * Rendered as a fill this shades the near-water area - used for the "farther" answer.
     */
    fun regionsWithin(thresholdMeters: Double): List<Polygon> =
        contour(thresholdMeters, wantWithin = true)

    /**
     * Region {distance > thresholdMeters} as valid polygons-with-holes ("farther than X from
     * water"). Rendered as a fill this shades the far-from-water area - used for the "closer" answer.
     */
    fun regionsBeyond(thresholdMeters: Double): List<Polygon> =
        contour(thresholdMeters, wantWithin = false)

    /**
     * Marching squares over the boolean field for the requested polarity, orienting each segment so
     * the region interior is on its left, then stitching directed segments into rings, classifying
     * shells vs holes by signed area, and nesting holes into their containing shell.
     */
    private fun contour(threshold: Double, wantWithin: Boolean): List<Polygon> {
        fun inside(r: Int, c: Int): Boolean {
            val d = distAt(r, c) ?: return false // padding = outside so contours close at bounds
            return if (wantWithin) d <= threshold else d > threshold
        }

        // Oriented segments [x1,y1,x2,y2] with the region interior on the LEFT of x1y1 -> x2y2.
        val segments = ArrayList<DoubleArray>()

        // Iterate with a one-cell border so regions touching the bounds still produce closed rings.
        for (r in -1 until rows) {
            for (c in -1 until cols) {
                val tl = inside(r, c)
                val tr = inside(r, c + 1)
                val br = inside(r + 1, c + 1)
                val bl = inside(r + 1, c)
                var code = 0
                if (tl) code = code or 8
                if (tr) code = code or 4
                if (br) code = code or 2
                if (bl) code = code or 1
                if (code == 0 || code == 15) continue

                val lonL = clampLon(lonAt(c)); val lonR = clampLon(lonAt(c + 1))
                val latT = clampLat(latAt(r)); val latB = clampLat(latAt(r + 1))

                // Edge midpoints (binary field: the midpoint crossing is stable and smooth enough).
                val top = doubleArrayOf((lonL + lonR) / 2.0, latT)
                val right = doubleArrayOf(lonR, (latT + latB) / 2.0)
                val bottom = doubleArrayOf((lonL + lonR) / 2.0, latB)
                val left = doubleArrayOf(lonL, (latT + latB) / 2.0)

                // Filled corner positions, used to orient each segment (interior on left).
                val filled = ArrayList<DoubleArray>(4)
                if (tl) filled.add(doubleArrayOf(lonL, latT))
                if (tr) filled.add(doubleArrayOf(lonR, latT))
                if (br) filled.add(doubleArrayOf(lonR, latB))
                if (bl) filled.add(doubleArrayOf(lonL, latB))

                fun emit(a: DoubleArray, b: DoubleArray) {
                    val mx = (a[0] + b[0]) / 2.0; val my = (a[1] + b[1]) / 2.0
                    // nearest filled corner to this segment's midpoint
                    var fx = filled[0][0]; var fy = filled[0][1]; var best = Double.MAX_VALUE
                    for (p in filled) {
                        val dx = p[0] - mx; val dy = p[1] - my
                        val d2 = dx * dx + dy * dy
                        if (d2 < best) { best = d2; fx = p[0]; fy = p[1] }
                    }
                    val cross = (b[0] - a[0]) * (fy - a[1]) - (b[1] - a[1]) * (fx - a[0])
                    if (cross > 0.0) {
                        segments.add(doubleArrayOf(a[0], a[1], b[0], b[1]))
                    } else {
                        segments.add(doubleArrayOf(b[0], b[1], a[0], a[1]))
                    }
                }

                when (code) {
                    1 -> emit(left, bottom)
                    2 -> emit(bottom, right)
                    3 -> emit(left, right)
                    4 -> emit(top, right)
                    5 -> { emit(left, top); emit(bottom, right) }
                    6 -> emit(top, bottom)
                    7 -> emit(left, top)
                    8 -> emit(top, left)
                    9 -> emit(top, bottom)
                    10 -> { emit(top, right); emit(bottom, left) }
                    11 -> emit(top, right)
                    12 -> emit(left, right)
                    13 -> emit(bottom, right)
                    14 -> emit(left, bottom)
                }
            }
        }

        val rings = stitchRings(segments)
        return assemblePolygons(rings)
    }

    /** Stitches directed segments into closed rings by chaining endpoints (quantised for matching). */
    private fun stitchRings(segments: List<DoubleArray>): List<List<DoubleArray>> {
        if (segments.isEmpty()) return emptyList()
        fun key(x: Double, y: Double): Long {
            val qx = (x * 1_000_000.0).roundToLong()
            val qy = (y * 1_000_000.0).roundToLong()
            return (qx shl 32) xor (qy and 0xFFFFFFFFL)
        }
        val startIndex = HashMap<Long, ArrayDeque<Int>>()
        segments.forEachIndexed { i, s -> startIndex.getOrPut(key(s[0], s[1])) { ArrayDeque() }.addLast(i) }
        val used = BooleanArray(segments.size)
        val rings = ArrayList<List<DoubleArray>>()

        for (start in segments.indices) {
            if (used[start]) continue
            val ring = ArrayList<DoubleArray>()
            var current = start
            var guard = 0
            val maxSteps = segments.size + 1
            while (!used[current] && guard++ < maxSteps) {
                used[current] = true
                val s = segments[current]
                ring.add(doubleArrayOf(s[0], s[1]))
                val candidates = startIndex[key(s[2], s[3])]
                var next = -1
                if (candidates != null) {
                    while (candidates.isNotEmpty()) {
                        val cand = candidates.removeFirst()
                        if (!used[cand]) { next = cand; break }
                    }
                }
                if (next == -1) {
                    ring.add(doubleArrayOf(s[2], s[3]))
                    break
                }
                current = next
            }
            if (ring.size >= 3) {
                val first = ring.first(); val last = ring.last()
                if (first[0] != last[0] || first[1] != last[1]) ring.add(doubleArrayOf(first[0], first[1]))
                if (ring.size >= 4) rings.add(ring)
            }
        }
        return rings
    }

    /** Classifies rings into shells (CCW, positive area) and holes (CW), then nests holes. */
    private fun assemblePolygons(rings: List<List<DoubleArray>>): List<Polygon> {
        if (rings.isEmpty()) return emptyList()

        data class Ring(val pts: List<DoubleArray>, val area: Double)

        val shells = ArrayList<Ring>()
        val holes = ArrayList<Ring>()
        for (ring in rings) {
            val a = signedArea(ring)
            if (kotlin.math.abs(a) < 1e-12) continue
            if (a > 0.0) shells.add(Ring(ring, a)) else holes.add(Ring(ring, -a))
        }
        if (shells.isEmpty()) return emptyList()

        // Assign each hole to the smallest-area shell that contains its centroid.
        val shellHoles = HashMap<Int, MutableList<List<DoubleArray>>>()
        for (hole in holes) {
            val cen = centroid(hole.pts)
            var bestShell = -1
            var bestArea = Double.MAX_VALUE
            shells.forEachIndexed { i, shell ->
                if (shell.area < bestArea && pointInRing(cen[0], cen[1], shell.pts)) {
                    bestShell = i; bestArea = shell.area
                }
            }
            if (bestShell >= 0) shellHoles.getOrPut(bestShell) { ArrayList() }.add(hole.pts)
        }

        val out = ArrayList<Polygon>(shells.size)
        shells.forEachIndexed { i, shell ->
            val outer = shell.pts.map { Point.fromLngLat(it[0], it[1]) }
            val innerRings = shellHoles[i]?.map { h -> h.map { Point.fromLngLat(it[0], it[1]) } } ?: emptyList()
            out.add(Polygon.fromLngLats(listOf(outer) + innerRings))
        }
        return out
    }

    private fun signedArea(ring: List<DoubleArray>): Double {
        var a = 0.0
        for (i in 0 until ring.size - 1) {
            val p = ring[i]; val q = ring[i + 1]
            a += p[0] * q[1] - q[0] * p[1]
        }
        return a / 2.0
    }

    private fun centroid(ring: List<DoubleArray>): DoubleArray {
        var sx = 0.0; var sy = 0.0
        val n = ring.size - 1 // last repeats the first
        for (i in 0 until n) { sx += ring[i][0]; sy += ring[i][1] }
        return doubleArrayOf(sx / n, sy / n)
    }

    /** Ray-casting point-in-ring test (ring is closed: first == last). */
    private fun pointInRing(x: Double, y: Double, ring: List<DoubleArray>): Boolean {
        var inside = false
        var j = ring.size - 1
        for (i in ring.indices) {
            val xi = ring[i][0]; val yi = ring[i][1]
            val xj = ring[j][0]; val yj = ring[j][1]
            if (((yi > y) != (yj > y)) && (x < (xj - xi) * (y - yi) / (yj - yi) + xi)) inside = !inside
            j = i
        }
        return inside
    }

    companion object {
        private const val ASSET_NAME = "natural_water_distance.bin"

        /** Loads the grid from assets, or null if the asset is missing/malformed. */
        fun load(context: Context): WaterDistanceGrid? {
            return runCatching {
                context.assets.open(ASSET_NAME).use { input ->
                    val bytes = input.readBytes()
                    val newline = bytes.indexOf('\n'.code.toByte())
                    require(newline > 0) { "missing header" }
                    val header = String(bytes, 0, newline, Charsets.US_ASCII).trim()
                    val parts = header.split(" ")
                    require(parts[0] == "WDIST1" && parts.size >= 9) { "bad header: $header" }
                    val cols = parts[1].toInt()
                    val rows = parts[2].toInt()
                    val west = parts[3].toDouble()
                    val south = parts[4].toDouble()
                    val east = parts[5].toDouble()
                    val north = parts[6].toDouble()
                    val maxMeters = parts[8].toInt()

                    val offset = newline + 1
                    val count = cols * rows
                    require(bytes.size - offset >= count * 2) { "truncated grid" }
                    val dist = IntArray(count)
                    var p = offset
                    for (i in 0 until count) {
                        val lo = bytes[p].toInt() and 0xFF
                        val hi = bytes[p + 1].toInt() and 0xFF
                        dist[i] = lo or (hi shl 8)
                        p += 2
                    }
                    WaterDistanceGrid(cols, rows, west, south, east, north, maxMeters, dist)
                }
            }.getOrNull()
        }
    }
}
