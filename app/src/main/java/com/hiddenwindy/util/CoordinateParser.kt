package com.hiddenwindy.util

/** A parsed latitude/longitude pair, in decimal degrees. */
data class ParsedCoordinate(val latitude: Double, val longitude: Double)

/**
 * Matches a full DMS (degrees/minutes/seconds) coordinate pair, e.g. `41°52'56.5"N 87°37'09.7"W`.
 * Accepts common symbol variants for degrees (`°`, `d`, `:`), minutes (`'`, the Unicode prime
 * `\u2032`, `m`), and seconds (`"`, the Unicode double-prime `\u2033`, `s`), with the hemisphere
 * letter directly following each coordinate. Degree-minute-only (no seconds) coordinates are not
 * supported.
 */
private val DMS_PAIR = Regex(
    """(\d{1,3})\s*[°:d]\s*(\d{1,2})\s*['\u2032m]\s*(\d{1,2}(?:\.\d+)?)\s*["\u2033]?s?""" +
        """\s*([NSns])[,\s]+\s*(\d{1,3})\s*[°:d]\s*(\d{1,2})\s*['\u2032m]\s*(\d{1,2}(?:\.\d+)?)\s*["\u2033]?s?\s*([EWew])"""
)

/** Matches decimal degrees with trailing hemisphere letters, e.g. "41.88235N, 87.61935W". */
private val DECIMAL_WITH_HEMISPHERE = Regex(
    """(\d{1,3}(?:\.\d+)?)\s*°?\s*([NSns])[,\s]+(\d{1,3}(?:\.\d+)?)\s*°?\s*([EWew])"""
)

/**
 * Matches a plain signed decimal pair, e.g. "41.88235, -87.61935" — our own app's own texted
 * format, and the most common case overall. Requires an explicit decimal point on both numbers so
 * we don't false-positive on unrelated integer pairs (e.g. "Draw 3, Pick 1").
 */
private val SIGNED_DECIMAL_PAIR = Regex(
    """(-?\d{1,3}\.\d+)\s*,\s*(-?\d{1,3}\.\d+)"""
)

/**
 * Attempts to find a latitude/longitude pair anywhere within [text], trying DMS, then decimal
 * degrees with hemisphere letters, then a plain signed decimal pair. Assumes latitude-then-
 * longitude order (the standard human-facing convention, and the order this app always uses in
 * its own texted messages); if the first candidate pair fails range validation, a swapped
 * interpretation is tried before moving on to the next pattern. Returns null if nothing usable is
 * found.
 */
fun parseCoordinateFromText(text: String): ParsedCoordinate? {
    dmsMatch(text)?.let { return it }
    decimalWithHemisphereMatch(text)?.let { return it }
    signedDecimalPairMatch(text)?.let { return it }
    return null
}

private fun dmsMatch(text: String): ParsedCoordinate? {
    val m = DMS_PAIR.find(text) ?: return null
    val g = m.groupValues
    val lat = dmsToDecimal(g[1].toDouble(), g[2].toDouble(), g[3].toDouble(), g[4][0])
    val lon = dmsToDecimal(g[5].toDouble(), g[6].toDouble(), g[7].toDouble(), g[8][0])
    return validOrSwapped(lat, lon)
}

private fun decimalWithHemisphereMatch(text: String): ParsedCoordinate? {
    val m = DECIMAL_WITH_HEMISPHERE.find(text) ?: return null
    val g = m.groupValues
    val lat = signedByHemisphere(g[1].toDouble(), g[2][0])
    val lon = signedByHemisphere(g[3].toDouble(), g[4][0])
    return validOrSwapped(lat, lon)
}

private fun signedDecimalPairMatch(text: String): ParsedCoordinate? {
    val m = SIGNED_DECIMAL_PAIR.find(text) ?: return null
    val g = m.groupValues
    return validOrSwapped(g[1].toDouble(), g[2].toDouble())
}

private fun dmsToDecimal(degrees: Double, minutes: Double, seconds: Double, hemisphere: Char): Double {
    val magnitude = degrees + minutes / 60.0 + seconds / 3600.0
    return signedByHemisphere(magnitude, hemisphere)
}

private fun signedByHemisphere(magnitude: Double, hemisphere: Char): Double {
    val negative = hemisphere.equals('S', ignoreCase = true) || hemisphere.equals('W', ignoreCase = true)
    return if (negative) -magnitude else magnitude
}

private fun isValidLatLon(lat: Double, lon: Double): Boolean = lat in -90.0..90.0 && lon in -180.0..180.0

/** Validates [lat]/[lon] as-is, or swapped if the original order doesn't fall in range. */
private fun validOrSwapped(lat: Double, lon: Double): ParsedCoordinate? = when {
    isValidLatLon(lat, lon) -> ParsedCoordinate(lat, lon)
    isValidLatLon(lon, lat) -> ParsedCoordinate(lon, lat)
    else -> null
}
