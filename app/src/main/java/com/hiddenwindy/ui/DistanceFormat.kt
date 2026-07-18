package com.hiddenwindy.ui

/**
 * Formats a distance in miles using a universal, precision-appropriate representation shared by
 * every Question/Confirm panel in the app (Seeker and Hider mode alike):
 * - 0 through 1,320 ft -> whole feet, e.g. "742 feet"
 * - just above 1,320 ft through 0.999 mi -> 3 decimals, e.g. "0.310 miles"
 * - 1.00 through 9.99 mi -> 2 decimals, e.g. "3.42 miles"
 * - 10.0 mi and up -> 1 decimal, e.g. "14.7 miles"
 *
 * Rounding within a tier can push a value into the next tier's range (e.g. 0.9996 mi rounds to
 * "1.000" at 3-decimal precision). To avoid ever displaying an out-of-range value like
 * "1.000 miles" or "10.00 miles", the rounded value is re-checked against the next tier and
 * promoted when necessary.
 *
 * The returned string is just the magnitude and unit (no "away"/"from" suffix) so callers can
 * keep their own surrounding phrasing.
 */
private const val FEET_PER_MILE = 5280.0

fun formatDistanceMiles(miles: Double): String {
    val safeMiles = if (miles.isFinite()) miles.coerceAtLeast(0.0) else 0.0
    val roundedFeet = Math.round(safeMiles * FEET_PER_MILE)
    if (roundedFeet <= 1320) return "$roundedFeet feet"
    return formatMilesTier(safeMiles)
}

private fun formatMilesTier(miles: Double): String = when {
    miles < 1.0 -> {
        val rounded = roundTo(miles, 3)
        if (rounded >= 1.0) formatMilesTier(rounded) else "%.3f miles".format(rounded)
    }
    miles < 10.0 -> {
        val rounded = roundTo(miles, 2)
        if (rounded >= 10.0) formatMilesTier(rounded) else "%.2f miles".format(rounded)
    }
    else -> "%.1f miles".format(miles)
}

private fun roundTo(value: Double, decimals: Int): Double {
    val factor = Math.pow(10.0, decimals.toDouble())
    return Math.round(value * factor) / factor
}
